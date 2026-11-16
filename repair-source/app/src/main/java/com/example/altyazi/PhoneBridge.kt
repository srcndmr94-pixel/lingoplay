package com.example.altyazi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * TELEFON ↔ TV KÖPRÜSÜ
 *
 * GameLingo TV altyazıyı okur, ev Wi-Fi'ı üzerinden telefona yollar; telefondaki
 * yapay zekâ (Gemma) çevirip geri gönderir. İnternet gerekmez, her şey evin içinde kalır.
 *
 * Basit metin protokolü (UTF-8, satır satır):
 *   Bulma (UDP 47322, yayın):  "GLFIND <kod>"   ->  telefon: "GLHERE"
 *   Deneme (TCP 47321):        "GL1 <kod> PING" ->  "PONG <hazır:1/0>"
 *   Görünüm:                   "GL1 <kod> STIL" ->  "STIL <yazı boyu sp> <yazı rengi #AARRGGBB> <arka #AARRGGBB>"
 *                              (TV tarafı telefondaki "Altyazı görünümü" ayarlarını kullansın diye)
 *   Çeviri (TCP 47321):        "GL1 <kod> <sıra> <öncekiSayısı>"
 *                              tür \n ad \n satır \n önceki satırlar...
 *                              ->  "OK <çeviri>" | "SKIP" | "ERR <sebep>"
 *   Hızlı taslak:              "GL1 <kod> <sıra> <öncekiSayısı> Q" (aynı gövde)
 *                              ->  "OK <hızlı çeviri>" anında (yapay zekâyı beklemez; bilgisayar için)
 */
object PhoneBridge {
    const val TCP_PORT = 47321
    const val UDP_PORT = 47322

    private fun one(s: String) = s.replace('\n', ' ').replace('\r', ' ').trim()

    /** Bu cihazın ev ağındaki IPv4 adresi (ör. 192.168.1.23). */
    fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress ?: ""
    } catch (_: Throwable) {
        ""
    }

    /** Aynı Wi-Fi'daki telefonu kodla arar; bulursa adresini döner. (Arka planda çağır.) */
    fun discover(pin: String, timeoutMs: Int = 2500): String? {
        return try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = timeoutMs
                val msg = "GLFIND ${one(pin)}".toByteArray(Charsets.UTF_8)
                s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), UDP_PORT))
                val buf = ByteArray(64)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val reply = String(p.data, 0, p.length, Charsets.UTF_8)
                if (reply.startsWith("GLHERE")) p.address.hostAddress else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Telefona ulaşılıyor mu? null: ulaşılamadı; true/false: yapay zekâ hazır mı. */
    fun ping(host: String, pin: String): Boolean? {
        val r = request(host, "GL1 ${one(pin)} PING\n", 3000) ?: return null
        if (!r.startsWith("PONG")) return null
        return r.trim().endsWith("1")
    }

    sealed class Result {
        class Ok(val text: String) : Result()
        object Skip : Result()
        class Err(val reason: String) : Result()
    }

    /** Bir satırı telefona çevirtir. (Arka planda çağır; en fazla ~12 sn bekler.) */
    fun translate(host: String, pin: String, seq: Int, line: String, previous: List<String>,
                  contentType: String, title: String): Result {
        val sb = StringBuilder()
        sb.append("GL1 ").append(one(pin)).append(' ').append(seq).append(' ').append(previous.size).append('\n')
        sb.append(one(contentType)).append('\n')
        sb.append(one(title)).append('\n')
        sb.append(one(line)).append('\n')
        for (p in previous) sb.append(one(p)).append('\n')
        val r = request(host, sb.toString(), 12000) ?: return Result.Err("telefona ulaşılamadı")
        return when {
            r.startsWith("OK ") -> Result.Ok(r.substring(3).trim())
            r.startsWith("SKIP") -> Result.Skip
            r.startsWith("ERR") -> Result.Err(r.removePrefix("ERR").trim())
            else -> Result.Err("anlaşılmayan cevap")
        }
    }

    /** Telefona gelen bir çeviri isteği. */
    class Req(val client: String, val seq: Int, val contentType: String, val title: String,
              val line: String, val previous: List<String>, val quick: Boolean = false)

    /**
     * Sunucu tarafı: tek bir bağlantıyı okur, cevaplar ve kapatır.
     * [ready]: yapay zekâ hazır mı; [work]: çeviriyi yapıp "OK ..." / "SKIP" / "ERR ..." döner.
     */
    fun serveOne(c: Socket, pin: String, ready: () -> Boolean, onClient: (String) -> Unit,
                 stil: () -> String = { "" }, work: (Req) -> String) {
        try {
            c.soTimeout = 5000
            val r = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
            val w = OutputStreamWriter(c.getOutputStream(), Charsets.UTF_8)
            fun reply(s: String) { w.write(s.replace('\n', ' ') + "\n"); w.flush() }

            val head = (r.readLine() ?: return).trim().split(' ')
            if (head.size < 3 || head[0] != "GL1") { reply("ERR protokol"); return }
            if (head[1] != pin) { reply("ERR kod yanlış"); return }
            val client = c.inetAddress.hostAddress ?: "?"
            onClient(client)
            if (head[2] == "PING") { reply("PONG " + if (ready()) "1" else "0"); return }
            if (head[2] == "STIL") { val st = stil(); reply(if (st.isBlank()) "ERR stil yok" else "STIL $st"); return }
            val seq = head[2].toIntOrNull() ?: 0
            val nPrev = head.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
            val contentType = r.readLine() ?: ""
            val title = r.readLine() ?: ""
            val line = (r.readLine() ?: "").trim()
            val previous = (0 until nPrev).mapNotNull { r.readLine() }
            if (line.isEmpty()) { reply("ERR boş"); return }
            c.soTimeout = 15000
            val quick = head.getOrNull(4) == "Q"
            reply(work(Req(client, seq, contentType, title, line, previous, quick)))
        } catch (_: Throwable) {
        } finally {
            try { c.close() } catch (_: Throwable) {}
        }
    }

    /** Sunucu tarafı: bulma (yayın) paketlerine cevap verir; [running] false olunca biter. */
    fun answerDiscovery(s: DatagramSocket, pin: () -> String, running: () -> Boolean) {
        val buf = ByteArray(128)
        while (running()) {
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            val msg = String(p.data, 0, p.length, Charsets.UTF_8).trim()
            if (msg == "GLFIND ${pin()}") {
                val r = "GLHERE".toByteArray(Charsets.UTF_8)
                s.send(DatagramPacket(r, r.size, p.address, p.port))
            }
        }
    }

    private fun request(host: String, body: String, timeoutMs: Int): String? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, TCP_PORT), 2000)
            s.soTimeout = timeoutMs
            val w = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            w.write(body)
            w.flush()
            BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Telefonda çalışan "TV ve bilgisayara çeviri sağla" servisi. Ekran kapalıyken de çalışsın diye
 * ön planda (bildirimli) çalışır; işlemciyi ve Wi-Fi'ı uyanık tutar.
 */
class PhoneBridgeService : Service() {

    companion object {
        const val ACTION_START = "com.example.altyazi.BRIDGE_START"
        const val ACTION_STOP = "com.example.altyazi.BRIDGE_STOP"
        private const val CHANNEL = "tv_kopru"
        private const val NOTIF_ID = 47

        @Volatile var running = false
        @Volatile var pin = ""
        @Volatile var ip = ""
        @Volatile var served = 0
        @Volatile var lastClient = ""
        @Volatile var lastError = ""

        /** Telefonun TV için kalıcı eşleşme kodu (4 hane). */
        fun pinFor(context: Context): String {
            val sp = context.getSharedPreferences("tv_kopru", Context.MODE_PRIVATE)
            var p = sp.getString("kod", "") ?: ""
            if (p.length != 4) {
                p = (1000 + java.util.Random().nextInt(9000)).toString()
                sp.edit().putString("kod", p).apply()
            }
            return p
        }

        fun statusText(): String = when {
            !running -> "Kapalı"
            lastError.isNotEmpty() -> "Açık · kod $pin · $ip · uyarı: $lastError"
            else -> "Açık · kod $pin · adres $ip · $served çeviri" + if (lastClient.isNotEmpty()) " · son cihaz: $lastClient" else ""
        }
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var llm: LlmTranslate? = null
    private var store: TranslationStore? = null
    private var fast: FastTranslate? = null
    private val latestSeq = ConcurrentHashMap<String, AtomicInteger>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY
        startForegroundSafely()
        running = true
        pin = pinFor(this)
        ip = PhoneBridge.localIp()
        served = 0
        lastError = ""
        store = TranslationStore(this, TranslationStore.AI)
        UserDict.apply(this)
        if (LlmTranslate.hasModel(this)) {
            llm = LlmTranslate(this)
            LlmTranslate.preload(this)
        } else {
            lastError = "yapay zekâ modeli yok"
        }
        locks(true)
        if (!scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fast = FastTranslate().also { f -> scope.launch { try { f.prepare() } catch (_: Throwable) {} } }
        scope.launch { tcpLoop() }
        scope.launch { udpLoop() }
        scope.launch {
            // Adres değişirse (Wi-Fi yeniden bağlandı) ekranda güncel kalsın.
            while (running) { ip = PhoneBridge.localIp(); delay(5000) }
        }
        CaptureService.log("TV KÖPRÜSÜ AÇIK · kod $pin · adres $ip")
        return START_STICKY
    }

    private fun locks(on: Boolean) {
        try {
            if (on) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameLingo:tvkopru").also { it.acquire() }
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GameLingo:tvkopru").also { it.acquire() }
                // Ekran kapalıyken TV'nin yayın (bulma) paketleri de gelsin.
                multicast = wm.createMulticastLock("GameLingo:tvkopru").also { it.setReferenceCounted(false); it.acquire() }
            } else {
                try { wake?.release() } catch (_: Throwable) {}
                try { wifi?.release() } catch (_: Throwable) {}
                try { multicast?.release() } catch (_: Throwable) {}
                wake = null; wifi = null; multicast = null
            }
        } catch (e: Throwable) {
            CrashLog.note(this, "tv kopru kilit", e)
        }
    }

    private fun udpLoop() {
        try {
            val s = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PhoneBridge.UDP_PORT))
            }
            udp = s
            PhoneBridge.answerDiscovery(s, { pin }, { running })
        } catch (e: Throwable) {
            if (running) CrashLog.note(this, "tv kopru bulma", e)
        }
    }

    private fun tcpLoop() {
        try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PhoneBridge.TCP_PORT))
            }
            server = ss
            while (running) {
                val c = ss.accept()
                scope.launch { handle(c) }
            }
        } catch (e: Throwable) {
            if (running) {
                lastError = "bağlantı noktası açılamadı"
                CrashLog.note(this, "tv kopru sunucu", e)
            }
        }
    }

    /** Telefondaki "Altyazı görünümü" ayarları: "<sp> <#AARRGGBB yazı> <#AARRGGBB arka>". */
    private fun stilMetni(): String = try {
        val p = Prefs(this)
        val arka = (SubtitleStyle.arkaAlfa(p.subtitleBg) shl 24) or (SubtitleStyle.arkaRenk(p.subtitleBgColor) and 0xFFFFFF)
        String.format("%d #%08X #%08X", p.subtitleSize, SubtitleStyle.yaziRengi(p.subtitleColor), arka)
    } catch (_: Throwable) { "" }

    private fun handle(c: Socket) {
        PhoneBridge.serveOne(c, pin, { llm?.ready == true }, { lastClient = it }, { stilMetni() }) { req ->
            // Hafızada varsa anında.
            store?.get(req.line)?.let { served++; return@serveOne "OK $it" }
            // Hızlı taslak: yapay zekâyı beklemeden telefonun hızlı çevirisi (bilgisayar önce bunu gösterir).
            if (req.quick) {
                val f = fast ?: return@serveOne "ERR hızlı çeviri yok"
                val t = try {
                    runBlocking { OfflineFix.translate(req.line) { f.translate(it) } }
                } catch (_: Throwable) { null }
                return@serveOne if (t.isNullOrBlank()) "ERR ${f.lastError ?: "hızlı çeviri başarısız"}"
                else "OK " + NameFix.koru(req.line, TurkishFix.duzelt(t))
            }
            val l = llm ?: return@serveOne "ERR yapay zekâ modeli yok"
            // Model belleğe yükleniyorsa biraz bekle.
            var waited = 0
            while (!l.ready && LlmTranslate.isLoading() && waited < 60) { Thread.sleep(500); waited++ }
            if (!l.ready) return@serveOne "ERR yapay zekâ hazır değil"
            // Bu TV'nin en yeni satırı hangisi? Sırası gelince daha yenisi varsa bunu atla (birikmesin).
            val latest = latestSeq.getOrPut(req.client) { AtomicInteger(0) }
            latest.accumulateAndGet(req.seq) { a, b -> maxOf(a, b) }
            val out = runBlocking {
                l.translate(req.line, req.previous, req.contentType, req.title, discardIfStale = false) { latest.get() <= req.seq }
            }
            if (out.isNullOrBlank()) {
                return@serveOne if (latest.get() > req.seq) "SKIP" else "ERR ${l.lastError ?: "çeviri başarısız"}"
            }
            store?.put(req.line, out)
            served++
            lastError = ""
            "OK $out"
        }
    }

    private fun startForegroundSafely() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Çeviri köprüsü", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 3, Intent(this, PhoneBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("GameLingo · TV ve bilgisayara çeviri sağlanıyor")
            .setContentText("Kod ${pinFor(this)} · telefon aynı Wi-Fi'da kalsın")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Durdur", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopAll() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        try { udp?.close() } catch (_: Throwable) {}
        server = null; udp = null
        locks(false)
        try { fast?.close() } catch (_: Throwable) {}
        fast = null
        try { scope.cancel() } catch (_: Throwable) {}
        CaptureService.log("TV köprüsü kapandı")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopAll()
        super.onDestroy()
    }
}
