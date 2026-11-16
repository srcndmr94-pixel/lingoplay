package com.example.altyazi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.altyazi.START"
        const val ACTION_STOP = "com.example.altyazi.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "altyazi"
        private const val NOTIF_ID = 1001

        /** Ekran bu genişliğe kadar olduğu gibi yakalanır (altyazı net kalsın diye). */
        private const val TARGET_WIDTH = 2200   // telefonda tam çözünürlüğe yakın: küçük altyazılar daha doğru okunur
        /** TV kutuları (Xiaomi Box vb.) zayıf: daha küçük yakalama, video takılmasın. */
        private const val TARGET_WIDTH_TV = 1024
        /** TV'de taramalar arası en az bu kadar beklenir (ms). */
        private const val MIN_INTERVAL_TV = 300L
        /** Bu kadar süre yeni kare gelmezse ekrandaki çeviri kaldırılır (ms). */
        private const val STALE_MS = 4000L

        /**
         * Dönme sonrası kare gelmezse okuyucuyu kaç kez yenileyelim.
         * Az tutuluyor: kare gelmemesinin en sık sebebi arıza değil, ekranın
         * durağan olması. Gereksiz yenileme çalışan yakalamayı da riske atar.
         */
        private const val KURTARMA_DENEMESI = 2
        /** Dönme sonrası en çok kaç kurtarma denemesi (kademeli, ~7 dakika). */
        private const val KURTARMA_UST_SINIR = 20

        /** Aynı replik sayılmak için gereken ortak başlangıç: en az bu kadar harf... */
        private const val ORTAK_BAS_EN_AZ = 25
        /** ...ve kısa olanın en az bu oranı. */
        private const val ORTAK_BAS_ORAN = 0.65f

        /**
         * Oran şartı aranmadan aynı replik sayılan ortak başlangıç uzunluğu.
         *
         * Uzun repliklerde OCR kuyruğun yarısını bozabiliyor ve oran %65'in
         * altına düşüyor. Kullanıcının 7 Kasım kaydından ölçüm:
         *   aynı replik  -> ortak başlangıç 46-64 harf
         *   farklı replik -> ortak başlangıç 0-10 harf
         * Arada geniş boşluk var; 40 harf iki kümeyi güvenle ayırıyor.
         */
        private const val ORTAK_BAS_MUTLAK = 40

        /** Menü taraması bu genişliğe küçültülerek yapılır; menü yazıları büyüktür. */
        private const val MENU_WIDTH = 1200

        /** Menü taraması kaç tarama turunda bir yapılsın. */
        private const val MENU_EVERY = 4

        private const val MAX_LABELS = 5

        /** Aynı anda en fazla kaç replik çevrilsin. */
        private const val MAX_PARALLEL = 3

        /** Menü taraması ekranın sadece bu kadar üst kısmında yapılır. */
        private const val MENU_ZONE = 0.5f

        /** Türkçeye özgü harfler: bu satır bizim kendi çevirimizdir, tekrar çevirme. */
        private const val TURKISH_ONLY = "ığşİĞŞ"

        /** Servis çalışıyor mu? Ana ekran tuşun yazısını buna göre belirler. */
        @Volatile
        var running = false

        /** Son işlemlerin kaydı; ana ekrandan kopyalanıp sorun teşhisinde kullanılır. */
        private val logLines = ArrayDeque<String>()

        @JvmStatic
        fun log(line: String) {
            synchronized(logLines) {
                logLines.addLast(line)
                // 150'ydi. Eleme sebepleri de kayda girdiği için tek bir sahne
                // 150 satırı aşıyor ve başı kesiliyordu; teşhis için en kritik
                // satırlar da genelde baştakiler oluyor.
                while (logLines.size > 400) logLines.removeFirst()
            }
        }

        /** Bu oturumda çevrilen satırlar (en yenisi sonda): ana ekrandaki Geçmiş ve Son çevrilen. */
        private val history = ArrayDeque<Pair<String, String>>()

        fun history(): List<Pair<String, String>> = synchronized(history) { history.toList() }

        fun lastPair(): Pair<String, String>? = synchronized(history) { history.lastOrNull() }

        /**
         * 9.93 — Ana ekrandaki canlı süreler. Son satırın GERÇEK ölçümü:
         * yazının okunması (OCR) ve satır okunduktan sonra ilk çevirinin
         * ekrana çıkması.
         */
        @Volatile var sonOcrMs = 0L
        @Volatile var sonCeviriMs = 0L

        internal fun addHistory(source: String, value: String) {
            synchronized(history) {
                // Aynı repliğin düzeltilmiş hali eskisinin yerine geçer.
                if (history.lastOrNull()?.first == source) history.removeLast()
                history.addLast(source to value)
                while (history.size > 150) history.removeFirst()
            }
        }

        /** "Ne görüyor?" ekranı için son yakalanan görüntü (küçültülmüş). */
        @Volatile var lastPreview: Bitmap? = null

        /** Oyun ekranının çerçevesiz, küçültülmüş son karesi; sadece RAM'de tutulur. */
        @Volatile var lastPreviewRaw: Bitmap? = null

        fun dumpLog(): String = synchronized(logLines) {
            if (logLines.isEmpty()) "Kayıt yok. BAŞLAT'a basıp oyunda birkaç replik bekleyin."
            else logLines.joinToString("\n")
        }

        /**
         * Çalışma kaydını siler.
         *
         * Kayıt 150 satır tutuyor ve oturumlar üst üste biniyordu; gönderilen
         * kayıtta hangi satırın hangi denemeye ait olduğu anlaşılmıyordu.
         * Kullanıcı artık oyuna girmeden önce kaydı temizleyip tek bir sahnenin
         * temiz dökümünü alabiliyor.
         */
        fun clearLog() = synchronized(logLines) { logLines.clear() }

        /**
         * Yalnızca Türkçede bulunan sık kelimeler. Satırda bunlardan biri
         * geçiyorsa metin zaten Türkçedir: çevirmeye kalkmak hem saçma bir
         * sonuç üretiyor hem sırayı işgal ediyordu ("6 Hareket Halinde",
         * "izlemeye devam edin" gibi arayüz yazıları kayda düşmüştü).
         */
        private val TURKCE_KELIMELER = setOf(
            "bir", "bu", "şu", "için", "ile", "çok", "daha", "var", "yok",
            "değil", "ama", "gibi", "kadar", "sonra", "önce", "şey", "evet",
            "hayır", "tamam", "devam", "edin", "halinde", "abone", "izlemeye",
            "ve", "veya", "ben", "sen", "biz", "siz", "onlar", "bizim",
            "senin", "benim", "nasıl", "neden", "nerede", "kim", "hangi",
            "oldu", "olur", "yapıyor", "geldi", "gitti", "dedi", "bilmiyorum",
        )

        /** Oyun arayüzü yazıları; altyazıyla karışmasınlar. */
        private val UI_WORDS = setOf(
            "TOUCHPAD", "OPTIONS", "SKIP", "CONTINUE", "LOADING", "AUTOSAVE",
            "OBJECTIVE", "NEW OBJECTIVE", "MISSION", "CHECKPOINT"
        )

        private val CONTROLLER_LABELS = setOf(
            "L1", "L2", "L3", "R1", "R2", "R3",
            "X", "O", "□", "△", "▲", "●", "■", "✕",
            "PS", "START", "SELECT", "TOUCH", "SKIP", "HOLD", "MAP", "MENU",
            "BACK", "NEXT", "OPTION", "PAUSE", "WAIT"
        )

        /**
         * Altyazı OLMAYAN, ama ekranda altyazı bölgesine düşebilen yazılar.
         * Bunlar çevrilirse hem jeton harcanır hem de gerçek replik sıra bekler.
         * Küçük harfe çevrilmiş metnin içinde aranır.
         */
        private val BLOCK_PHRASES = listOf(
            // Android'in ekran kaydı uyarısı (Prime, Netflix vb.)
            "güvenlik nedeniyle", "uygulama içerikleri", "gizlendi",
            "secure content", "content is hidden",
            // Oynatıcı arayüzü
            "dialogue boost", "x-ray", "xray", "ekran görüntüsü",
            "alt yazı", "alt yazi", "altyazılar", "subtitles",
            "audio and subtitles", "audio & subtitles",
            "skip intro", "skip recap", "next episode", "watch credits",
            "episodes", "bölümler", "ses artırma", "ayarlar ve",
            // Konsol ve uzaktan oynatma arayüzü. Kullanıcının 11 Kasım 9.66
            // kaydında PlayStation durum çubuğu altyazı sanılıp çevrildi:
            //   "tand Play Station D4,2 Mn 23 so. öng" -> "TAND OYUN İSTASYONU..."
            //   "Play Station >4,2 Mn 23 so."          -> "Çalma istasyonu> 4,2 mn 23."
            "playstation", "play station", "xbox", "nintendo", "steam deck",
            "remote play", "uzaktan oynat", "dualsense", "ps remote",
        )

        /** Oyuncu kadrosu listesinde sık geçmeyen, cümleye özgü kelimeler. */
        private val SENTENCE_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "i", "you", "he", "she", "we", "they", "it", "me", "him", "her",
            "my", "your", "his", "our", "their", "this", "that", "these",
            "to", "of", "in", "on", "at", "for", "with", "from", "by",
            "and", "or", "but", "if", "so", "not", "no", "yes",
            "do", "does", "did", "don't", "can", "can't", "will", "won't",
            "have", "has", "had", "get", "got", "go", "come", "know", "think",
            "what", "why", "how", "when", "where", "who",
        )

        /**
         * Tek-iki harfli GERÇEK İngilizce kelimeler.
         *
         * [altyaziDegil] içindeki parçalanmış-okuma kuralı bunları kırıntı
         * saymaz. Noktalama atıldıktan sonraki hâlleri tutuluyor; kesme
         * işaretiyle ayrılan ekler ("I'm" -> "m") de burada.
         */
        private val KISA_KELIMELER = setOf(
            "a", "i", "am", "an", "as", "at", "be", "by", "do", "go", "he",
            "hi", "if", "in", "is", "it", "me", "my", "no", "of", "oh", "ok",
            "on", "or", "so", "to", "up", "us", "we", "ya", "yo", "ye", "ah",
            "ay", "eh", "uh", "um", "aw", "ow", "oi", "ha", "hm", "mm", "nah",
            "m", "s", "t", "d", "ll", "re", "ve", "em", "im", "id",
        )
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var projection: MediaProjection? = null

    /**
     * Sanal ekranın hangi yakalama izniyle kurulduğu. Android bir izin için
     * createVirtualDisplay'i yalnız bir kez kabul ediyor; bu alan ikinci çağrıyı
     * daha baştan engelliyor (bkz. configureDisplay).
     */
    @Volatile private var sanalEkraninIzni: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var readerThread: HandlerThread? = null
    private var readerHandler: Handler? = null

    private lateinit var prefs: Prefs
    private lateinit var overlay: Overlay
    private var fast: FastTranslate? = null
    private var firefox: FirefoxCeviri? = null
    private var opusMt: OpusMtEngine? = null
    // Count only completed strong-engine calls, not OCR/dictionary/cache hits.
    private val opusAccepted = java.util.concurrent.atomic.AtomicInteger(0)
    private val opusRejected = java.util.concurrent.atomic.AtomicInteger(0)
    private val deepLAccepted = java.util.concurrent.atomic.AtomicInteger(0)
    private val deepLRejected = java.util.concurrent.atomic.AtomicInteger(0)
    private var llm: LlmTranslate? = null
    @Volatile private var llmLatest = ""
    private val recentLines = ArrayDeque<String>()
    private var ai: AiTranslate? = null
    private var store: TranslationStore? = null
    private var storeAi: TranslationStore? = null

    // var: DURDUR'da kapatılıyor; servis yok edilmeden hemen yeniden BAŞLAT denirse yenisi açılır.
    private var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var recognizerClosed = false

    private var captureWidth = 0
    private var captureHeight = 0
    private var displayCreated = false

    private var screenWidth = 0
    private var screenHeight = 0

    /** Her karede yeni görüntü ayırmak telefonu yoruyor; aynı tamponu kullanıyoruz. */
    private var frameBitmap: Bitmap? = null

    /** Altyazı şeridinin önceki karedeki parmak izi; değişmeyen kare okunmaz. */
    private var bandImza: IntArray? = null
    private var imzaPiksel: IntArray? = null
    private val subtitleReadGuard299 = SubtitleReadGuard299()

    // --- altyazı durumu ---
    @Volatile private var shownSource = ""
    private var candidate = ""
    private var candidateSeen = 0
    @Volatile private var translatingFor = ""
    /** Monotonic identity: equal text shown at a later time is a NEW subtitle. */
    private val subtitleGeneration = SubtitleGenerationGate()
    // Dengeli modda tek replik iki kez değişmesin: ilk doğrulanan Türkçe sabittir.
    private val stableCaption199 = StableCaptionGate199()

    /** Aynı anda sürmekte olan çeviriler. Yeni replik eskisini iptal etmez. */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val startedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var lastOcrRaw = ""
    @Volatile private var lastMlKitRaw = ""
    @Volatile private var lastFilteredOcr = ""
    @Volatile private var sonCaptureMs = 0L
    @Volatile private var lastOcrTrace = "ham=0 kalan=0 secilen=0"
    @Volatile private var diagOcrErrors = 0
    private var emptyFrames = 0
    private var everShown = false
    private var noTextWarned = false
    /** Android TV'nin kendisinde mi çalışıyoruz (GameLingo TV)? */
    private val isTv: Boolean by lazy { TvMode.isTvDevice(this) }

    private var lastPreviewMs = 0L

    /** Yakalanan ekranın küçük kopyası; okunan altyazı bölgesi turkuaz çerçeveyle işaretli. */
    private fun onizlemeKaydet(full: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            if (full.isRecycled || full.width <= 0) return
            // ImageReader satır dolgusunu (padding) önizlemeden çıkar: seçilen
            // yüzdelik alan, gerçek OCR tarama koordinatlarıyla aynı kalsın.
            val usable = if (captureWidth in 1..full.width) captureWidth else full.width
            val w = 640
            val h = (full.height * w / usable).coerceAtLeast(1)
            val k = w.toFloat() / usable
            val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(raw).drawBitmap(
                full, android.graphics.Rect(0, 0, usable, full.height),
                android.graphics.Rect(0, 0, w, h),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
            // Ayar ekranındayken uygulamanın kendi arayüzünü oyun önizlemesiyle değiştirme.
            if (!MainActivity.visible && !RegionActivity.visible) lastPreviewRaw = raw
            val prev = raw.copy(Bitmap.Config.ARGB_8888, true)
            val c = android.graphics.Canvas(prev)
            val p = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 3f
                color = android.graphics.Color.rgb(46, 230, 214)
            }
            c.drawRect(left * k, top * k, right * k, bottom * k, p)
            lastPreview = prev
        } catch (_: Throwable) {
        }
    }

    private var subtitleAnchorPx = 0
    private var subtitleBottomPx = -1
    @Volatile private var protectedBandTopPx = -1
    @Volatile private var protectedBandBottomPx = -1

    // "İngilizcenin altında" konumu için: okunan altyazının ekrandaki alt kenarı ve
    // şerit (kesit) koordinatlarını ekrana çevirmek için gerekenler.
    private val dialogueContext = DialogueContextRepair()
    private var sonBozukLog = 0L
    @Volatile private var textBottomScreenPx = -1
    @Volatile private var textBottomAt = 0L
    private var ocrBandTop = 0
    private var ocrBandScale = 1f
    private var ocrScreenPerCap = 1f
    private var ownTextFrames = 0

    // --- menü/görev durumu ---
    private val menuCache = java.util.Collections.synchronizedMap(LinkedHashMap<String, String>())
    private var lastMenuKey = ""
    private var menuJob: Job? = null

    /** Şu an ekrana bizim yazdığımız metinler; tekrar okunup çevrilmesinler. */
    // Birden fazla iş parçacığı (OCR döngüsü, yapay zekâ, taslak) aynı anda okuyup yazıyor:
    // eş zamanlı küme; döngü sırasında değişse de ConcurrentModificationException olmaz.
    private val displayed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        overlay = Overlay(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sistem servisi kendiliğinden yeniden başlatmışsa (intent yok) ekran
        // yakalama izni artık geçersizdir. Döngüye girmemek için hemen duruyoruz.
        if (intent == null || intent.action == null) {
            log("Servis izinsiz yeniden başlatıldı, durduruluyor")
            stopEverything()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (EngineBenchmark1035.running) {
                    log("MODEL TESTI CALISIYOR: test tamamlanmadan ekran yakalama acilmaz")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startForegroundSafely()
                // Ayarlar değişmiş olabilir: yapay zekâ devresini yeniden aç.
                AiTranslate.devreyiAc()

                val code = intent.getIntExtra(EXTRA_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                if (data == null) {
                    stopEverything()
                    return START_NOT_STICKY
                }

                if (projection == null) {
                    // Daha önce durdurulmuşsa iş alanı kapalıdır, yenisini açıyoruz.
                    if (!scope.isActive) {
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    }
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val mp = try {
                        mpm.getMediaProjection(code, data)
                    } catch (e: Throwable) {
                        CrashLog.note(this, "ekran izni", e)
                        null
                    }
                    if (mp == null) {
                        stopEverything()
                        return START_NOT_STICKY
                    }
                    mp.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            stopEverything()
                        }
                    }, Handler(mainLooper))
                    projection = mp

                    overlay.show()
                    TvMode.autoUpdate(this, prefs)
                    overlay.setStil(gorunumStili())
                    running = true
                    startPipeline()
                }
            }

            else -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- kurulum

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Lingo", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)

        return builder
            .setContentTitle("Lingo çalışıyor")
            .setContentText("Ekrandaki yazılar Türkçeye çevriliyor")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    "Durdur",
                    stopIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private fun startForegroundSafely() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            val d = wm.defaultDisplay
            @Suppress("DEPRECATION")
            val p = android.graphics.Point().also { d.getRealSize(it) }
            p.x to p.y
        }
    }

    /**
     * Android, bir ekran yakalama izni için sanal ekranı YALNIZCA BİR KEZ
     * kurmaya izin veriyor. Telefon döndüğünde yeniden kurmuyoruz, var olanı
     * yeniden boyutlandırıyoruz.
     */
    private fun configureDisplay(): Boolean {
        val (sw, sh) = screenSize()
        if (sw <= 0 || sh <= 0) return false
        screenWidth = sw
        screenHeight = sh

        val scale = min(1f, (if (isTv) TARGET_WIDTH_TV else TARGET_WIDTH).toFloat() / max(sw, 1))
        val w = max(2, (sw * scale).toInt() / 2 * 2)
        val h = max(2, (sh * scale).toInt() / 2 * 2)

        if (displayCreated && w == captureWidth && h == captureHeight) return true

        if (readerThread == null) {
            // TV'de görüntü okuyucu düşük öncelikte: video oynatmanın önüne geçmesin.
            val thread = HandlerThread(
                "altyazi-reader",
                if (isTv) android.os.Process.THREAD_PRIORITY_BACKGROUND else android.os.Process.THREAD_PRIORITY_DEFAULT
            ).also { it.start() }
            readerThread = thread
            readerHandler = Handler(thread.looper)
        }

        val reader = try {
            ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        } catch (e: Throwable) {
            CrashLog.note(this, "goruntu okuyucu", e)
            return false
        }

        return try {
            if (!displayCreated) {
                // Android aynı yakalama izniyle ikinci bir sanal ekran kurdurtmuyor;
                // denersek SecurityException atıyor ve yakalama tamamen ölüyor.
                // Bu yüzden izin nesnesi başına EN FAZLA BİR KEZ kuruyoruz.
                if (projection != null && projection === sanalEkraninIzni) {
                    reader.close()
                    log("Sanal ekran bu izinle zaten kurulmuştu; yeniden kurulamaz. DURDUR → BAŞLAT gerekiyor.")
                    return false
                }
                val vd = projection?.createVirtualDisplay(
                    "altyazi",
                    w, h,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    readerHandler
                )
                if (vd == null) {
                    reader.close()
                    return false
                }
                virtualDisplay = vd
                displayCreated = true
                sanalEkraninIzni = projection
            } else {
                val vd = virtualDisplay
                if (vd == null) {
                    reader.close()
                    return false
                }
                // Sıra önemli: önce yüzeyi ayır, sonra boyutla, sonra yeni yüzeyi bağla.
                // Doğrudan resize+setSurface yapınca bazı cihazlarda (Pixel, Android 16)
                // ekran dönünce hiç yeni kare gelmiyordu: "ilk başlatmada çevirmiyor".
                vd.setSurface(null)
                vd.resize(w, h, resources.displayMetrics.densityDpi)
                vd.setSurface(reader.surface)
            }

            val old = imageReader
            imageReader = reader
            try {
                old?.close()
            } catch (_: Throwable) {
            }

            captureWidth = w
            captureHeight = h
            true
        } catch (e: Throwable) {
            CrashLog.note(this, "ekran kurulumu", e)
            try {
                reader.close()
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun releaseDisplay() {
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        imageReader = null
        try {
            readerThread?.quitSafely()
        } catch (_: Throwable) {
        }
        readerThread = null
        readerHandler = null
        // Tamponu serbest bırakmıyoruz; tarama işi hâlâ okuyor olabilir.
        frameBitmap = null
        bandImza = null
        displayCreated = false
        captureWidth = 0
        captureHeight = 0
        // Yeni BAŞLAT yeni bir izin alacak: kilidi kaldır.
        sanalEkraninIzni = null
    }

    // ----------------------------------------------------------------- akış

    /** Çeviri döngüsü bir kez çalışsın (çift başlatma kilidi). */
    private val pipelineOn = java.util.concurrent.atomic.AtomicBoolean(false)

    // Ekran döndükten sonra kare gelip gelmediğini izlemek için
    @Volatile private var lastFrameMs = 0L
    @Volatile private var staleWarned = false
    @Volatile private var rotatedAtMs = 0L
    @Volatile private var framesSinceRotate = 0
    @Volatile private var reattachTries = 0
    /** Son kurtarma denemesinin zamani; denemeler arasi sure buradan olculuyor. */
    @Volatile private var sonKurtarmaMs = 0L

    /**
     * Dönmeden sonra kare gelmiyorsa kurtarma.
     *
     * Eskiden burada var olan yüzey ayrılıp AYNI yüzey geri bağlanıyordu. Pixel'de
     * (Android 16) bu hiçbir şey yapmıyor: üretici aynı yüzeye yeniden bağlanmıyor,
     * üç deneme de boşa gidiyor ve yakalama tamamen ölüyordu ("Döndükten sonra
     * görüntü gelmedi 1/3, 2/3, 3/3" ve ardından sessizlik).
     *
     * Çözüm: YENİ bir görüntü okuyucu (yani yeni bir Surface) kurup onu bağla.
     * Yeni yüzey üreticiyi gerçekten yeniden başlatıyor.
     *
     * DİKKAT — sanal ekran ASLA serbest bırakılıp yeniden kurulmaz. Android,
     * bir yakalama izni için createVirtualDisplay'e yalnızca bir kez izin veriyor;
     * ikinci çağrı SecurityException atıyor ("Don't take multiple captures by
     * invoking MediaProjection#createVirtualDisplay multiple times"). Bir sürümde
     * üçüncü deneme olarak bu yol vardı: hem işe yaramıyor hem de o ana kadar
     * çalışan sanal ekranı yok ettiği için yakalamayı kalıcı olarak öldürüyordu.
     * Kullanıcının 6 Kasım 20:59 hata raporu tam olarak buydu.
     *
     * @param zorla ikinci ve sonraki denemelerde boyutu bir tık oynatıp geri alır;
     *              bazı cihazlarda üreticiyi yeniden yapılandırmaya zorluyor.
     */
    private fun recoverCapture(zorla: Boolean): Boolean = try {
        val vd = virtualDisplay
        val w = captureWidth
        val h = captureHeight
        if (vd == null || w <= 0 || h <= 0) {
            false
        } else {
            val dpi = resources.displayMetrics.densityDpi
            val yeni = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            vd.setSurface(null)
            if (zorla) {
                // Boyutu değiştirip geri almak üreticiyi yeniden yapılandırıyor.
                vd.resize(maxOf(2, w - 2), maxOf(2, h - 2), dpi)
            }
            vd.resize(w, h, dpi)
            vd.setSurface(yeni.surface)
            val eski = imageReader
            imageReader = yeni
            try { eski?.close() } catch (_: Throwable) {}
            // Yeni okuyucu yeni boyutta kare verecek: eski tamponu bırak.
            frameBitmap = null
            bandImza = null
            true
        }
    } catch (e: Throwable) {
        CrashLog.note(this, "yakalama kurtarma", e)
        false
    }

    // Tanı sayaçları (başlangıçta okunmama sorununu kayıtta görmek için)
    @Volatile private var diagFrames = 0
    @Volatile private var diagNoFrame = 0
    @Volatile private var diagText = 0
    @Volatile private var diagOwn = 0

    private fun startPipeline() {
        UserDict.apply(this)   // "Sözlüğüm" satırları çeviride geçerli olsun
        Sozluk.yukle(this)     // İngilizce sözlük (uygulama açılmadan servis başlatıldıysa)
        if (recognizerClosed) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizerClosed = false
        }
        diagFrames = 0; diagNoFrame = 0; diagText = 0; diagOwn = 0
        // Ad defteri YAPIMA özgüdür: Invincible'dan öğrenilen "Alien",
        // "Venture" bir sonraki filme taşınırsa orada ORTAK AD olan bu
        // kelimeler İngilizce kalır. object olduğu için süreç boyunca
        // yaşıyordu ve temizle() hiç çağrılmıyordu (ölü kod).
        AdDefteri.temizle()
        KonusanOnEki.temizle()
        prefs.bicimiOnar()
        dialogueContext.reset()
        rotatedAtMs = 0L; framesSinceRotate = 0; reattachTries = 0; sonKurtarmaMs = 0L
        lastFrameMs = 0L; staleWarned = false
        everShown = false
        if (!pipelineOn.compareAndSet(false, true)) {
            log("Çeviri zaten çalışıyor, ikinci başlatma yok sayıldı")
            return
        }
        val f = FastTranslate()
        fast = f
        // 10.2: Firefox çeviri motoru (kuruluysa anlık taslak ondan gelir).
        firefox = if (prefs.firefoxMotoru) FirefoxCeviri(this).also {
            if (it.baglan()) log("FIREFOX MOTORU: bağlanılıyor (Offline Translator)")
            else log("FIREFOX MOTORU YOK: ${FirefoxCeviri.sonHata ?: "kurulu değil"} · taslak yerleşik motordan")
        } else null
        // A real local Marian/OPUS-MT engine, enabled only by explicit opt-in.
        // Missing/corrupt model packs cannot break working Google/Firefox fallback.
        opusMt = if (prefs.opusMtEnabled && OpusModelPack.isInstalled(this)) {
            OpusMtEngine(this).also { opusCandidate ->
                scope.launch {
                    log("OPUS-MT: yerel model belleğe yükleniyor")
                    log(if (opusCandidate.prepare()) "OPUS-MT: ONNX modeli hazır (performans henüz doğrulanmadı)"
                        else "OPUS-MT: yükleme başarısız (${opusCandidate.lastError}); Google/Firefox yedeği")
                }
            }
        } else {
            if (prefs.opusMtEnabled) log("OPUS-MT: paket yok; Google/Firefox kullanılacak")
            null
        }
        GoogleCeviri.temizle()
        // Tanı açıksa ham Google yanıtı ile sonradan gösterilen Türkçe
        // karşılaştırılabilir; hiçbir ek internet isteği oluşturmaz.
        GoogleCeviri.trace = if (prefs.diagnostics) { satir -> log(satir) } else null
        // DeepL is *opt-in only*; no key means absolutely no DeepL HTTP requests.
        // The same quality gate is enforced for DeepL, OPUS and Google fallbacks.
        val deeplKey = if (prefs.deepLEnabled) DeepLCredentials.read(this) else ""
        val useDeepL = prefs.deepLEnabled && deeplKey.isNotBlank()
        DeepLCeviri.resetSession()
        suspend fun opusAttempt(text: String): String? {
            // BEGIN_BENCH202
            val benchOpus = kotlinx.coroutines.currentCoroutineContext()[SubtitleEngineTrace202]
            val benchOpusStart = android.os.SystemClock.elapsedRealtime()
            // END_BENCH202
            val local = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                opusMt?.takeIf { it.ready }?.translate(text, 64)
            }
            val candidate = local?.takeIf {
                it.isNotBlank() && !it.equals(text, ignoreCase = true) &&
                    // A profanity warning is not proof of a wrong translation.
                    // Preserve the warning in BENCH logs instead of silently
                    // replacing a grammatical OPUS result with a weaker engine.
                    TranslationQualityGate.acceptableStrong(TranslationQualityGate.check(text, it))
            }
            if (candidate != null) opusAccepted.incrementAndGet()
            else opusRejected.incrementAndGet()
            // BEGIN_BENCH202
            benchOpus?.result("OPUS-MT", android.os.SystemClock.elapsedRealtime() - benchOpusStart,
                local, candidate != null, if (local == null) "not-ready-or-timeout" else "quality-gate")
            // END_BENCH202
            return candidate
        }
        OfflineFix.gucluFn = if (useDeepL || prefs.opusMtEnabled || prefs.googleCeviri) { text ->
            val remote = if (useDeepL) {
                val context = kotlinx.coroutines.currentCoroutineContext()[TranslationTurnContext10427]?.previousEnglish.orEmpty()
                // BEGIN_BENCH202
                val benchDeep = kotlinx.coroutines.currentCoroutineContext()[SubtitleEngineTrace202]
                val benchDeepStart = android.os.SystemClock.elapsedRealtime()
                // END_BENCH202
                val answer = DeepLCeviri.translate(text, deeplKey, context)
                // Do not append hardcoded curses to natural MT output.
                // LOST_PROFANITY remains a warning: the balanced quality gate
                // may compare Firefox/ML Kit without inventing swear words.
                val accepted = answer?.takeIf {
                    it.isNotBlank() && !it.equals(text, ignoreCase = true) &&
                        TranslationQualityGate.acceptableStrong(TranslationQualityGate.check(text, it))
                }
                if (accepted != null) deepLAccepted.incrementAndGet()
                else deepLRejected.incrementAndGet()
                // BEGIN_BENCH202
                benchDeep?.result("DeepL", android.os.SystemClock.elapsedRealtime() - benchDeepStart,
                    answer, accepted != null, if (answer == null) "no-result" else
                        TranslationQualityGate.check(text, answer)?.name ?: "ok")
                // END_BENCH202
                accepted
            } else null
            remote ?: (if (prefs.opusMtEnabled && opusMt != null) opusAttempt(text) else null)
                ?: if (prefs.googleCeviri) {
                    // BEGIN_BENCH202
                    val benchGoogle = kotlinx.coroutines.currentCoroutineContext()[SubtitleEngineTrace202]
                    val benchGoogleStart = android.os.SystemClock.elapsedRealtime()
                    // END_BENCH202
                    GoogleCeviri.cevir(text)?.takeIf { googleResult ->
                        val issue = TranslationQualityGate.check(text, googleResult)
                        // LOST_PROFANITY is a warning (as with DeepL), not a
                        // reason to force an ungrammatical Firefox fallback.
                        val acceptable = TranslationQualityGate.acceptableStrong(issue)
                        // BEGIN_BENCH202
                        benchGoogle?.result("Google", android.os.SystemClock.elapsedRealtime() - benchGoogleStart,
                            googleResult, acceptable, issue?.name ?: "ok")
                        // END_BENCH202
                        acceptable
                    }
                } else null
        } else null
        if (useDeepL) log("DEEPL AÇIK (API, kullanım kotası/ücreti olabilir) · OPUS/Google yedeği")
        else if (prefs.deepLEnabled) log("DEEPL KAPALI: geçerli API anahtarı yok; yerel/Google motorları kullanılıyor")
        OfflineFix.clearCache()  // Mod değişince önceki motorun geçici yanıtlarını kullanma.
        if (prefs.googleCeviri) log("GOOGLE ÇEVİRİ AÇIK (anahtarsız; HTTP 429 sınırı olabilir) · internet modunda Gemini kullanılmıyor")
        ai = AiTranslate(prefs)
        store = TranslationStore(this)
        storeAi = TranslationStore(this, TranslationStore.AI)
        if (prefs.hizliHafizaSurumu != TranslationStore.HIZLI_SURUM) {
            val eski = store?.count() ?: 0
            store?.clear()
            prefs.hizliHafizaSurumu = TranslationStore.HIZLI_SURUM
            log("HIZLI ÇEVİRİ HAFIZASI YENİLENDİ: çeviri kuralları değişti, $eski eski taslak silindi")
        }
        // Önceki sürümlerde hafızaya girmiş bozuk okumaları temizle.
        // Sözlük gerekmiyor: harf+rakam yapışması ("o0d", "wo0de") tek başına yeterli işaret.
        val silinen = (store?.purge { HARF_RAKAM.containsMatchIn(it) } ?: 0) +
            (storeAi?.purge { HARF_RAKAM.containsMatchIn(it) } ?: 0)
        if (silinen > 0) log("HAFIZA TEMİZLENDİ: $silinen bozuk kayıt silindi")

        // In FAST mode the LLM never receives any subtitle. Loading it here used
        // 500-700 MB of native memory and could take nearly a minute for no gain.
        // It will load on a subsequent start in a mode that actually uses it.
        // 10.37: Real-device benchmark on Pixel 9 Pro: E4B median 3270 ms,
        // 0/10 responses under 500 ms, one 30 s timeout. Never launch it in
        // the default BALANCED or FAST live subtitle path. The user can still
        // explicitly select DOĞRU ÖNCE (slow/experimental), and benchmark mode
        // remains independent of this service.
        val onDeviceUsed = LiveEnginePolicy.usePhoneLlm(prefs.ceviriBicimi)
        if (onDeviceUsed && LlmTranslate.hasModel(this)) {
            val l = LlmTranslate(this)
            llm = l
            LlmTranslate.preload(this)
            scope.launch {
                if (!LlmTranslate.isReady(this@CaptureService)) {
                    log("Telefon yapay zekâsı belleğe yükleniyor… (büyük modelde ilk seferde 1-2 dk)")
                }
                while (isActive && LlmTranslate.isLoading()) delay(500)
                log(if (l.ready) "TELEFON YAPAY ZEKÂSI HAZIR (${l.backendName}) · ${LlmTranslate.loadState}"
                    else "TELEFON YAPAY ZEKÂSI YÜKLENEMEDİ: ${l.lastError}")
                if (l.ready) l.hazirla()
            }
        } else if (!onDeviceUsed) {
            llm = null
            log("HIZLI/DENGELİ MOD: E4B canlı çeviride kapalı (ölçülen gecikme 3,3 sn; bellek tasarrufu)")
        } else {
            llm = null
            log("Telefon yapay zekâsı modeli yok (ana ekrandan indirilebilir)")
        }

        scope.launch {
            // Sözlük hazır olunca kayda yaz: OCR onarımı o andan sonra devrede.
            var bekleme = 0
            while (isActive && !Sozluk.hazir && bekleme < 60) { delay(500); bekleme++ }
            log(if (Sozluk.hazir) "SÖZLÜK HAZIR: ${Sozluk.adet} kelime (${Sozluk.kaynak}) · OCR onarımı devrede"
                else "SÖZLÜK YÜKLENEMEDİ${Sozluk.sonHata?.let { ": $it" } ?: ""} · OCR onarımı elle kurallarla")
        }

        scope.launch {
            try {
                f.prepare()
                f.translate("Hello.")  // ısındır: ilk replik de hızlı gelsin
            } catch (e: Throwable) {
                CrashLog.note(this@CaptureService, "ceviri modeli", e)
            }
        }

        scope.launch {
            if (!configureDisplay()) {
                stopEverything()
                return@launch
            }

            // Log the *active* engine, not the old pre-DeepL hardcoded label.
            // An enabled checkbox with no saved key does not count as active.
            val chain = when {
                useDeepL -> "DeepL → OPUS-MT → Google/Firefox"
                prefs.opusMtEnabled && opusMt != null -> "OPUS-MT → Google/Firefox"
                prefs.googleCeviri -> "Google → Firefox/yerel"
                else -> "Firefox/yerel"
            }
            val bicimAdi = when (prefs.ceviriBicimi) {
                Prefs.BICIM_HIZLI -> "HIZLI (E4B kapalı)"
                Prefs.BICIM_DOGRU -> "DOĞRU ÖNCE (taslak yalnız gecikince) · E4B deneysel"
                else -> "DENGELİ (E4B kullanılmaz)"
            }
            log("SÜRÜM $surumAdi · motor:$chain · biçim:$bicimAdi · küfür korunuyor${if (prefs.tvMode) " · TV modu" else ""}${if (isTv && prefs.bridgeOn) " · çeviri telefondan (${prefs.bridgeHost})" else ""}${if (prefs.mode != Prefs.MODE_INSTANT) " · model:" + (if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel else prefs.model) else ""}")
            overlay.setSubtitle("GameLingo çalışıyor…", dp(80))
            delay(2000)
            if (shownSource.isEmpty()) overlay.setSubtitle(null, 0)

            // ÖNEMLİ: kurulumda KULLANILAN boyutla başla. Eskiden 2 sn sonra yeniden okunuyordu:
            // BAŞLAT'tan sonra uygulama arka plana geçip oyun yatay açılınca yakalama dikey
            // boyutta kalıyor, fark hiç fark edilmiyordu. Yatay görüntü dikey çerçeveye küçülüp
            // sığdırıldığı için altyazı bölgesinde yazı yoktu: "ilk başlatmada çevirmiyor".
            var lastScreen = screenWidth to screenHeight
            var cycle = 0L
            val startedMs = android.os.SystemClock.elapsedRealtime()
            var nextDiagMs = startedMs + 5000
            var nextKaynakMs = startedMs + 10_000
            var nextTvCheckMs = 0L
            var lastTv = prefs.tvMode
            log("Ekran ${screenWidth}x${screenHeight} · yakalama ${captureWidth}x${captureHeight}${if (isTv) " · TV: hafif tarama (300 ms)" else ""}")

            while (isActive) {
                // TV'de sık tarama videoyu takıltıyor: en az 300 ms ara.
                val interval = prefs.intervalMs.toLong().coerceIn(if (isTv) MIN_INTERVAL_TV else 80L, 1000L)
                delay(interval)

                try {
                    var now = screenSize()
                    if (now != lastScreen) {
                        // Dönme bitmeden ölçü alırsak ara bir boyuta kurulum yapıp orada
                        // kalıyorduk: yeni boyut bir daha değişmediği için yakalama hiç
                        // düzelmiyor, kare gelmiyordu. Ölçü iki kez aynı çıkana kadar bekle.
                        var oturdu = now
                        var deneme = 0
                        while (deneme < 8) {
                            delay(100)
                            val tekrar = screenSize()
                            if (tekrar == oturdu) break
                            oturdu = tekrar
                            deneme++
                        }
                        now = oturdu
                        lastScreen = now
                        val ok = configureDisplay()
                        log("Ekran döndü: ${now.first}x${now.second} · yakalama ${captureWidth}x${captureHeight}${if (ok) "" else " · KURULAMADI"}")
                        rotatedAtMs = android.os.SystemClock.elapsedRealtime()
                        framesSinceRotate = 0
                        reattachTries = 0
                        sonKurtarmaMs = rotatedAtMs
                        bandImza = null
                        subtitleReadGuard299.reset()
                        textBottomScreenPx = -1
                        textBottomAt = 0L
                        protectedBandTopPx = -1
                        protectedBandBottomPx = -1
                        // Never leave a stale Turkish overlay across a rotated
                        // English OCR strip. Fresh frames must re-validate text.
                        shownSource = ""
                        candidate = ""
                        candidateSeen = 0
                        translatingFor = ""
                        subtitleGeneration.invalidate()
                        showSubtitle(null)
                        overlay.clearLabels()
                        lastMenuKey = ""
                        delay(250)
                        continue
                    }

                    // Tanı: başlangıçta hiç altyazı okunmadıysa nedenini kayda yaz (ilk 60 sn).
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    // TV modu: kablo takılıp çıkarıldıysa ya da ana ekrandan değiştirildiyse kutuyu yeniden ayarla.
                    if (nowMs >= nextTvCheckMs) {
                        nextTvCheckMs = nowMs + 2000
                        TvMode.autoUpdate(this@CaptureService, prefs)
                        if (prefs.tvMode != lastTv) {
                            lastTv = prefs.tvMode
                            overlay.setStil(gorunumStili())
                            log(if (lastTv) "TV MODU AÇIK · yazı büyük" else "TV modu kapalı · telefon boyutu")
                        }
                    }
                    // 10.8: bütün oturum boyunca 5 dakikada bir bellek / pil / ısı.
                    if (nowMs >= nextKaynakMs) {
                        nextKaynakMs = nowMs + KAYNAK_ARALIK_MS
                        log(kaynakDurumu())
                        if (prefs.googleCeviri) log(GoogleCeviri.durum())
                    }
                    // İlk 3 dakika her 15 sn'de bir: takılma olursa kayıtta sebebi görünsün.
                    if (nowMs >= nextDiagMs && nowMs - startedMs <= 180_000) {
                        nextDiagMs = nowMs + 15_000
                        log("TANI: kare $diagFrames (boş $diagNoFrame) · okunan yazı $diagText · kendi $diagOwn · OCR hata $diagOcrErrors · $lastOcrTrace · yakalama=${sonCaptureMs}ms OCR=${sonOcrMs}ms · ham: \"${lastMlKitRaw.take(75)}\" · filtre: \"${lastFilteredOcr.take(75)}\" · son: \"${lastOcrRaw.take(75)}\"")
                        if (firefox != null) log(FirefoxCeviri.durum())
                        if (prefs.googleCeviri) log(GoogleCeviri.durum())
                        // Sessiz boşlukları GÖRÜNÜR kıl: sözlükte olmayan ses
                        // sözcükleri eskiden iz bırakmadan düşüyordu.
                        val eksikSes = SoundTags.eksikleriAl()
                        if (eksikSes.isNotEmpty()) {
                            log("SES SÖZCÜĞÜ EKSİK (etikette düştü): " + eksikSes.joinToString(", "))
                        }
                        log("AD DEFTERİ: ${AdDefteri.durum()}")
                        KonusanOnEki.durum().takeIf { it.isNotEmpty() }?.let { log("KONUŞAN ADLARI: $it") }
                    }
                    val captureStart = android.os.SystemClock.elapsedRealtime()
                    val full = grabFull()
                    sonCaptureMs = android.os.SystemClock.elapsedRealtime() - captureStart
                    if (full == null) {
                        diagNoFrame++
                        // Uzun süre yeni görüntü gelmiyorsa (izlenen uygulama videoyu kayda kapalı
                        // katmanda oynatıyor) eski çeviri ekranda takılı kalmasın.
                        if (shownSource.isNotEmpty() && lastFrameMs > 0 && nowMs - lastFrameMs > STALE_MS) {
                            shownSource = ""
                            candidate = ""
                            candidateSeen = 0
                            translatingFor = ""
                            subtitleGeneration.invalidate()
                            showSubtitle(null)
                            if (!staleWarned) {
                                staleWarned = true
                                log("GÖRÜNTÜ GELMİYOR: ${STALE_MS / 1000} sn'dir yeni kare yok, eski çeviri kaldırıldı. İzlenen uygulama videoyu kayda kapatıyor olabilir.")
                            }
                        }
                        // Ekran döndükten sonra kare gelmiyorsa kademeli kurtar:
                        // 1-2. deneme yeni okuyucu, 3. deneme sanal ekranı komple yenile.
                        // KURTARMA VAZGEÇMİYOR, yalnız seyreliyor.
                        //
                        // Eskiden iki deneme sonra kalıcı olarak duruyordu ve
                        // "ekran duruyorsa bu normal" diye kayda yazıyordu.
                        // Kullanıcının 11 Kasım 9.64 kaydında bu yanlış çıktı:
                        //   Döndükten sonra kare yok, okuyucu yenileniyor (1/2)
                        //   Döndükten sonra kare yok, okuyucu yenileniyor (2/2)
                        //   Döndükten sonra hâlâ kare yok. Ekran duruyorsa bu normal...
                        //   TANI: kare 0 (boş 484) ...        <- 45+ sn, SIFIR çeviri
                        // Kullanıcı servisi elle yeniden başlatınca AYNI YÖNDE
                        // anında çalıştı; yani sanal ekran gerçekten bozulmuştu.
                        //
                        // Duran ekranda kare gelmemesi hâlâ normal, o yüzden
                        // denemeler arası süre büyüyor (2,5 sn, 5 sn, ... en çok
                        // 30 sn) ve kayda ilk üç denemeden sonra yazılmıyor:
                        // bedeli neredeyse sıfır, karşılığı kendiliğinden
                        // toparlanma.
                        // ÜST SINIR VAR. Denemeyi hiç bitirmemek ilk hâlimdi ve
                        // kendi gözden geçirmemde kusurlu çıktı: duran ekranda
                        // (oyundan çıkılmış, gece boyu açık kalmış) her 30
                        // saniyede bir ImageReader yeniden kuruluyor, sanal
                        // ekran iki kez yeniden boyutlanıyordu. Saatte ~120
                        // gereksiz kurulum demek.
                        //
                        // 20 deneme kademeli aralıklarla ~7 dakikaya yayılıyor:
                        // sanal ekran gerçekten bozulduysa bu süre içinde
                        // toparlanır; ekran duruyorsa 7 dakika sonra susar ve
                        // bir sonraki dönmede sıfırlanır.
                        if (rotatedAtMs > 0 && framesSinceRotate == 0 &&
                            reattachTries < KURTARMA_UST_SINIR &&
                            nowMs - sonKurtarmaMs > minOf(2500L * (reattachTries + 1), 30_000L)
                        ) {
                            sonKurtarmaMs = nowMs
                            reattachTries++
                            // İlk deneme düz, sonrakiler boyut oynatarak.
                            val ok = recoverCapture(zorla = reattachTries > 1)
                            if (reattachTries <= KURTARMA_DENEMESI + 1) {
                                log("Döndükten sonra kare yok, okuyucu yenileniyor " +
                                    "($reattachTries)${if (ok) "" else " · başarısız"}")
                                if (reattachTries == KURTARMA_DENEMESI + 1) {
                                    log("YAKALAMA UYARISI: ekran döndükten sonra görüntü akışı hâlâ yok. " +
                                        "Durağan menü veya yakalama yüzeyi sorunu olabilir; otomatik kurtarma " +
                                        "denemeleri seyrekleşerek sürecek. Oyun hareketliyken de devam ederse DURDUR/BAŞLAT ile yeni izin alın.")
                                }
                            }
                            if (reattachTries == KURTARMA_UST_SINIR) {
                                log("Kurtarma denemeleri durduruldu ($KURTARMA_UST_SINIR deneme, " +
                                    "~7 dk). Ekran yeniden dönünce baştan denenir.")
                            }
                        }
                        continue
                    }
                    // Dönme sonrası ilk kare: kurtarmanın gerekip gerekmediğini
                    // kayıttan görebilmek için bunu yazıyoruz.
                    if (framesSinceRotate == 0 && rotatedAtMs > 0) {
                        log("Döndükten ${nowMs - rotatedAtMs} ms sonra görüntü geldi" +
                            (if (reattachTries > 0) " · $reattachTries yenilemeden sonra" else " · yenilemesiz"))
                    }
                    framesSinceRotate++
                    lastFrameMs = nowMs
                    diagFrames++
                    processSubtitle(full)

                    // Menü çevirisi tüm ekranı okur, ağırdır: TV'de kapalı.
                    if (!isTv && prefs.menuTranslate && cycle % MENU_EVERY == 0L && menuJob?.isActive != true) {
                        val copy = try {
                            if (full.isRecycled) null
                            else full.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        }
                        if (copy != null) menuJob = scope.launch {
                            try {
                                processMenu(copy)
                            } catch (e: Throwable) {
                                CrashLog.note(this@CaptureService, "menu taramasi", e)
                            } finally {
                                copy.recycle()
                            }
                        }
                    }

                    cycle++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    CrashLog.note(this@CaptureService, "tarama dongusu", e)
                    delay(400)
                }
            }
        }
    }

    /** Ekranın son karesini olduğu gibi alır. */
    private fun grabFull(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Throwable) {
            null
        } ?: return null

        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride

            var bmp = frameBitmap
            if (bmp == null || bmp.isRecycled ||
                bmp.width != paddedWidth || bmp.height != image.height
            ) {
                // Eskisini serbest bırakmıyoruz: başka bir iş onu okuyor olabilir.
                // Çöp toplayıcı kendisi temizler.
                bmp = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                frameBitmap = bmp
                bandImza = null
            }
            bmp.copyPixelsFromBuffer(buffer)
            bmp
        } catch (_: Throwable) {
            null
        } finally {
            try {
                image.close()
            } catch (_: Throwable) {
            }
        }
    }

    // --------------------------------------------------------------- altyazı

    private suspend fun processSubtitle(full: Bitmap) {
        // Ekran yeniden kurulurken tampon serbest bırakılmış olabilir.
        if (full.isRecycled) return

        val topPct = prefs.topPct.coerceIn(0, 98)
        val bottomPct = prefs.bottomPct.coerceIn(topPct + 1, 100)

        val top = (full.height * topPct / 100).coerceIn(0, full.height - 2)
        val bottom = (full.height * bottomPct / 100).coerceIn(top + 2, full.height)
        val usable = if (captureWidth in 1..full.width) captureWidth else full.width
        val left = (usable * prefs.leftPct / 100).coerceIn(0, usable - 4)
        val right = (usable * prefs.rightPct / 100).coerceIn(left + 4, usable)

        // Kutunun ekrandaki yeri, ayardaki konum tercihine göre.
        val seritUstu = screenHeight * topPct / 100
        subtitleAnchorPx = when (prefs.subtitlePos) {
            // Şeridin üstünü kapat: İngilizce yazının üzerine otursun.
            Prefs.POS_COVER -> seritUstu - dp(6)
            // Ekranın en altı.
            Prefs.POS_BOTTOM -> screenHeight - dp(110)
            // İngilizcenin altında: yazı okununca gerçek yeri gösterirken hesaplanır; bu yedek.
            Prefs.POS_BELOW -> screenHeight * prefs.bottomPct.coerceIn(1, 100) / 100 - dp(4)
            // Varsayılan: şeridin hemen üstünde, kendi yazımızı okumayalım diye.
            else -> seritUstu - dp(58)
        }
        // Varsayılan konumda kutunun ALT kenarı şeridin üstünde durur: TV'de yazı büyük
        // olunca kutu aşağı taşıp İngilizce altyazıyı kapatıyor, okuma duruyordu.
        subtitleBottomPx = if (prefs.subtitlePos == Prefs.POS_ABOVE) seritUstu - dp(6) else -1
        // Protect the ENTIRE OCR band, not only its currently visible first line.
        // A second English line can appear farther down on the very next frame.
        protectedBandTopPx = (top * screenHeight.toFloat() / full.height).toInt()
        protectedBandBottomPx = (bottom * screenHeight.toFloat() / full.height).toInt()

        var band = try {
            Bitmap.createBitmap(full, left, top, right - left, bottom - top)
        } catch (e: Throwable) {
            // Tampon bu arada serbest bırakıldıysa bu kareyi atlıyoruz.
            return
        }
        // Kucuk seritleri buyutmek OCR dogrulugunu artiriyor ama tarama
        // suresini de ikiye katliyor. Esigi dusurduk: yalnizca gercekten
        // kucuk seritler buyutuluyor, normal boyuttakiler oldugu gibi gidiyor.
        if (band.height < 110) {
            val scaled = Bitmap.createScaledBitmap(band, band.width * 2, band.height * 2, true)
            if (scaled != band) band.recycle()
            band = scaled
        }

        // "Ne görüyor?" önizlemesi: 2 sn'de bir küçük bir kopya, okunan bölge çerçeveli.
        val simdiMs = android.os.SystemClock.elapsedRealtime()
        if (simdiMs - lastPreviewMs > 2000) {
            lastPreviewMs = simdiMs
            onizlemeKaydet(full, left, top, right, bottom)
        }

        // GameLingo'nun kendi ekranı açıkken (ayar yaparken) kendi yazılarımızı çevirmeyelim.
        if (MainActivity.visible) {
            band.recycle()
            return
        }

        // 24x8 örnekleme seyrek kaldığında aynı görünen karede yeni altyazı
        // atlanabiliyordu. Aynı görüntüyü her karede OCR'dan geçirmek yerine
        // 650 ms'de bir denetliyoruz; onay bekleyen replikte 180 ms.
        val sampleUnchanged = bandDegismedi(band)
        val checkMs = android.os.SystemClock.elapsedRealtime()
        if (!subtitleReadGuard299.shouldOcr(sampleUnchanged, candidateSeen == 1, checkMs)) {
            // Cached OCR is NOT a new observation: never increment candidateSeen
            // or approve a subtitle twice on one ML Kit result.
            band.recycle()
            return
        }

        ocrBandTop = top
        ocrBandScale = if (band.height >= (bottom - top) * 2 - 1) 2f else 1f
        ocrScreenPerCap = if (full.height > 0) screenHeight.toFloat() / full.height else 1f
        val ocrBasi = android.os.SystemClock.elapsedRealtime()
        val text = runOcr(band, filterEdges = true)
        sonOcrMs = android.os.SystemClock.elapsedRealtime() - ocrBasi
        band.recycle()
        if (text.isNotBlank()) diagText++
        lastOcrRaw = text
        handleText(text)
    }

    /**
     * Tek kural: bir replik için ekrana BİR KEZ yazılır ve replik değişene
     * kadar hiç değiştirilmez.
     */
    private fun handleText(raw: String) {
        // Öğrenilmiş konuşan adını ayır: "Old Man Gen I asked..." -> "Old Man Gen: I asked..."
        val text = adiAyir(clean(raw))

        if (text.isEmpty()) {
            emptyFrames++
            if (prefs.diagnostics && !noTextWarned && !everShown && emptyFrames >= 60) {
                noTextWarned = true
                log("UYARI: ekranda hiç yazı okunamadı")
                if (prefs.diagnostics) {
                    showSubtitle("⚠ Ekranda yazı okunamıyor. Altyazı bölgesini kontrol edin.")
                }
                return
            }
            // Oyunun altyazısı kaybolsa da çeviriyi hemen silme: Türkçe metin
            // İngilizcesinden uzun olduğu için okumaya vakit kalmıyordu
            // (6 kare x 80 ms = yarım saniye). En az gösterim süresi dolmadan
            // ekrandan kaldırmıyoruz. Yeni replik gelirse zaten hemen değişir.
            // Gereken süre yazının UZUNLUĞUNDAN hesaplanıyor; sabit süre
            // "Ne?" ile iki satırlık cümleyi aynı tutuyordu. Kullanıcının
            // kaydırıcısı alt sınır (bkz. okumaSuresi).
            val yeterinceDurdu =
                android.os.SystemClock.elapsedRealtime() - shownAtMs >= okumaMs
            if (emptyFrames >= 6 && shownSource.isNotEmpty() && yeterinceDurdu) {
                shownSource = ""
                candidate = ""
                candidateSeen = 0
                translatingFor = ""
                subtitleGeneration.invalidate()
                showSubtitle(null)
            }
            return
        }
        emptyFrames = 0

        // Oyuncu kadrosu, oynatıcı menüsü, güvenlik uyarısı… Bunlar çevrilirse
        // hem jeton gider hem de üç çeviri kanalını doldurup gerçek repliği
        // sıraya sokar; altyazı geriden gelmesinin başlıca sebebi buydu.
        // Kendi çevirimizi yeniden okuyup çevirmeyelim; yoksa metin her turda
        // büyüyerek geri gelir ve çeviri kanallarını boşa doldurur.
        if (isOurOwnText(text) && prefs.subtitlePos != Prefs.POS_COVER) {
            // Bölgede yalnızca KENDİ Türkçe yazımız görünüyor: İngilizce altyazı bitmiş
            // (ya da görüntü engelli). Boş kare gibi say ki eski çeviri ekranda takılı kalmasın.
            ownTextFrames++
            diagOwn++
            candidate = ""
            candidateSeen = 0
            if (ownTextFrames >= 8 && shownSource.isNotEmpty()) {
                ownTextFrames = 0
                shownSource = ""
                translatingFor = ""
                subtitleGeneration.invalidate()
                showSubtitle(null)
            }
            return
        }
        ownTextFrames = 0
        // Bozuk okuma (harf karışmış, yarım çizilmiş yazı): çevirme, temiz okumayı bekle.
        if (OcrQuality.bozukMu(text) || GameplayLogRepair299.waitForCleanReading(text)) {
            val simdi = android.os.SystemClock.elapsedRealtime()
            if (simdi - sonBozukLog > 3000) {
                sonBozukLog = simdi
                log("BOZUK OKUMA (beklendi, çevrilmedi): $text")
            }
            candidate = ""
            candidateSeen = 0
            return
        }
        if (isOurOwnText(text) || altyaziDegil(text)) {
            dustu(text, if (isOurOwnText(text)) "kendi yazımız" else "altyazı değil")
            candidate = ""
            candidateSeen = 0
            return
        }

        if (!SubtitleReadGuard299.newMeaningForDispatch(shownSource, text) &&
            (similar(text, shownSource) || OcrOnar.ayniGurultulu(text, shownSource))) {
            dustu(text, "ekrandakiyle aynı sayıldı: ${shownSource.take(40)}")
            candidate = ""
            candidateSeen = 0
            return
        }

        if (similar(text, candidate)) {
            candidateSeen++
        } else {
            candidate = text
            candidateSeen = 1
        }

        // Kaç kez okununca çevrilsin?
        //
        // İki kez okuma kuralı, yarım okunmuş metni sunucuya göndermemek
        // içindi. Ama çevrimdışı çeviri telefonun içinde ve bedava: orada
        // beklemenin tek sonucu altyazının bir tarama süresi geç gelmesi.
        // 150 ms'lik aralıkta bu, her replikte en az 300 ms kayıp demekti.
        //
        // Cümle tamamlanmış görünüyorsa (noktalamayla bitiyor ya da yeterince
        // uzunsa) çevrimdışı modda ilk okumada çeviriyoruz.
        val tamamGorunuyor = text.length >= 12 ||
            text.trimEnd().lastOrNull() in listOf('.', '!', '?', '…')
        // 10.1: internet modunda da. Cümle tamam görünüyorsa ikinci kareyi
        // beklemek her satırda bir tarama süresi (~0,2 sn) kayıptı.
        // GameTranslator yaklaşımı: güvenilir ilk okumalar hızla geçer;
        // bozuk parça içeren okuma bir kare daha doğrulanır.
        // A near-identical OCR correction must be confirmed by a NEW OCR sample,
        // preventing single-frame typo oscillation from re-translating repeatedly.
        val ocrCorrection = SubtitleReadGuard299.smallCorrection(shownSource, text) ||
            SubtitleReadGuard299.smallCorrection(translatingFor, text)
        val gerekenOkuma = if (ocrCorrection) 2 else
            if (tamamGorunuyor && SubtitlePreparation.confidentFirstRead(text)) 1 else 2

        if (candidateSeen < gerekenOkuma) return
        if (!SubtitleReadGuard299.newMeaningForDispatch(translatingFor, text) &&
            (similar(text, translatingFor) || OcrOnar.ayniGurultulu(text, translatingFor))) {
            dustu(text, "çevrilmekte olanla aynı sayıldı: ${translatingFor.take(40)}")
            return
        }

        // Newest subtitle always gets a slot. An outdated network request
        // must never make the current onscreen subtitle disappear.
        if (inFlight.size >= MAX_PARALLEL) {
            val oldestId = inFlight.keys.minOrNull()
            if (oldestId != null) {
                inFlight.remove(oldestId)?.cancel()
                if (prefs.diagnostics) log("ESKİ ÇEVİRİ İPTAL: $oldestId; yeni repliğe yer açıldı")
            }
        }
        val source = dialogueContext.repair(OcrSoundCue200.repair(text))
        if (!SubtitleReadGuard299.newMeaningForDispatch(translatingFor, source) &&
            similar(source, translatingFor)) return
        // ÖNEMLİ: translatingFor'u kapasite kontrolünden SONRA yazıyoruz.
        // Önce yazılırsa, sıra doluyken elenen replik bir daha hiç denenmez;
        // metin değişene kadar o altyazı ekrana gelmez. ("bazen gelmiyor")
        if (translatingFor.isNotEmpty() && translatingFor != source) {
            sonGidenKaynak = translatingFor
            sonGidenMs = android.os.SystemClock.elapsedRealtime()
        }
        translatingFor = source
        val runId = subtitleGeneration.newSubtitle()
        // New English subtitle accepted. Do not leave the preceding Turkish
        // caption visible while waiting for this line's translation.
        // OCR variations of the SAME utterance keep the current caption.
        if (CaptionSwitch10427.shouldClear(shownSource, source,
                OcrOnar.ayniGurultulu(shownSource, source))) {
            shownSource = ""
            showSubtitle(null)  // Removes the old shownValue from own-text tracking before blanking it.
        }
        dialogueContext.remember(source)
        val contextSnapshot = dialogueContext.contextFor(source)
        if (source != text) log("OCR AD ONARIMI: $text → $source")
        log("OKUNAN: $source")
        // Ad defteri: adları altyazının kendisinden öğreniyoruz. Çeviriden
        // ÖNCE işlenmeli, yoksa aynı satırda henüz bilinmiyor.
        AdDefteri.ogren(source)
        val startedMs = android.os.SystemClock.elapsedRealtime()
        startedAt[source] = startedMs
        // BEGIN_BENCH202
        val benchTrace = SubtitleEngineTrace202(runId, source, contextSnapshot, startedMs,
            android.os.SystemClock::elapsedRealtime, ::log)
        // END_BENCH202
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            // 198'de uzun replikler DeepL/OPUS'un bitmesini bekleyip ekrandan düşüyordu.
            // 199: yalnız DENGELİ modda, güçlü motor 420 ms içinde dönmezse
            // *ayrı* yerel çeviri paralel hazır edilir; güçlü motora yeni ağ isteği yok.
            // İkisi de yalnız geçerli replik için yayın yapabilir ve ilk geçerli
            // sonuç sabit kalır. Böylece yarım saniye sonra değişen altyazı yok.
            val localRescue = if (RealtimeCaptionPolicy199.needsLocalHedge(
                    prefs.ceviriBicimi == Prefs.BICIM_DENGELI,
                    OfflineFix.gucluFn != null,
                    source)) scope.launch(benchTrace) {
                delay(RealtimeCaptionPolicy199.HEDGE_AFTER_MS)
                if (!subtitleGeneration.isCurrent(runId) || shownSource == source) return@launch
                // BEGIN_BENCH202
                val benchLocalStart = android.os.SystemClock.elapsedRealtime()
                val benchLocalEngine = if (fast?.ready == true) "MLKit-local" else
                    if (firefox?.hazir == true) "Firefox-local" else "local-unavailable"
                // END_BENCH202
                val tr = withTimeoutOrNull(RealtimeCaptionPolicy199.LOCAL_TIMEOUT_MS) {
                    fastLocalCaption199(source)
                }
                // BEGIN_BENCH202
                benchTrace.result(benchLocalEngine,
                    android.os.SystemClock.elapsedRealtime() - benchLocalStart,
                    tr, !tr.isNullOrBlank(), if (tr == null) "null-or-timeout" else "ok")
                // END_BENCH202
                if (!tr.isNullOrBlank() && subtitleGeneration.isCurrent(runId) && shownSource != source) {
                    publishForRun(runId, source, tr, remember = false,
                        tag = " · hızlı yerel yedek")
                }
            } else null
            try {
                kotlinx.coroutines.withContext(TranslationTurnContext10427(contextSnapshot) + benchTrace) {
                    runTranslation(source, runId)
                }
            } finally {
                // Ana motor başarılıysa kalan taslak işini boşa sürdürme.
                // Başarısızsa kısa yerel kurtarma devam edebilir; kendi runId
                // denetimi yüzünden sonraki repliğin üzerine yazamaz.
                if (shownSource == source || !subtitleGeneration.isCurrent(runId)) localRescue?.cancel()
                inFlight.remove(runId)
                startedAt.remove(source, startedMs)
            }
        }
        inFlight[runId] = job
        job.start()
    }

    /**
     * Okunan bir satır çevrilmeden elendiğinde sebebini kayda yazar.
     *
     * Kullanıcının 11 Kasım 09:08 ekran görüntüsünde oyunda "Yeah. Go inside
     * and ask for Raymond, bruh." yazıyordu, kaplamada bir önceki repliğin
     * çevirisi duruyordu. Kayıtta o satır "son OCR"da görünüyor ama hiç
     * "OKUNAN" olmamış; hangi süzgece takıldığı anlaşılamadı çünkü elemeler
     * sessizce yapılıyordu. Artık her eleme sebebiyle kayda düşüyor.
     *
     * Aynı metin için üç saniyede bir yazılıyor: her karede tekrarlanıp
     * kaydı boğmasın.
     */
    private var sonDusenMetin = ""
    private var sonDusenAn = 0L

    private fun dustu(metin: String, sebep: String) {
        val simdi = android.os.SystemClock.elapsedRealtime()
        if (metin == sonDusenMetin && simdi - sonDusenAn < 3000) return
        sonDusenMetin = metin
        sonDusenAn = simdi
        if (sebep.startsWith("ekrandakiyle aynı") || sebep.startsWith("çevrilmekte olanla aynı")) {
            // Normal kare tekrarı hata değildir: kayıt ekranını şişirmeyelim.
            if (prefs.diagnostics) log("TEKRAR ATLANDI ($sebep): ${metin.take(60)}")
        } else {
            log("ÇEVRİLMEDİ ($sebep): ${metin.take(60)}")
        }
    }

    /** Ekrana en son ne zaman çeviri yazıldı (en az gösterim süresi için). */
    @Volatile private var shownAtMs = 0L

    /**
     * İki çeviri okuyan için aynı mı?
     *
     * Noktalama ve boşluk farkı sayılmaz. Kullanıcının 11 Kasım kaydında
     * yapay zekâ iki satırı yalnız noktalamayla "düzeltti" ve ekran boşuna
     * yanıp söndü:
     *   taslak "Dostum.."                          YZ "Dostum..."
     *   taslak "Sorun şu ki sadece kendini..."     YZ "Sorun şu ki, sadece..."
     */
    private fun ayniYazi(a: String, b: String): Boolean =
        a == b || sadeYazi(a) == sadeYazi(b)

    private fun sadeYazi(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** Ekranda duran çeviri ve okunması için gereken süre. */
    @Volatile private var shownValue = ""
    @Volatile private var okumaMs = 0L

    // ------------------------------------------------------- altyazı yığını
    //
    // Hızlı konuşmada önceki replik silinmiyor, üst sıraya çıkıyor. Böylece
    // yapay zekâ düzeltmesi satırın sonunda yetişse bile okunacak vakit kalıyor.

    /** Yeni satır bu süre içinde gelirse önceki satır üstte kalır. */
    private val YIGIN_ICINDE = 1500L

    /** Üstteki satırın tam görünür kaldığı süre; sonra solmaya başlar. */
    private val ONCEKI_TUT = 1500L
    private val ONCEKI_SOLMA = 400L

    /** Satır ekrandan bu kadar süre önce kalktıysa geç gelen düzeltmesi gösterilmez. */
    private val GEC_SINIR = 1500L
    /** Yalnız-doğru modunda, giden satırın geç cevabı bu kadar süre gösterilebilir. */
    private val GEC_GOSTER = 2500L

    /** "Yalnız doğru çeviri" modunda yapay zekâya tanınan süre; sonra taslak çıkar. */
    private val YEDEK_TASLAK_MS = 1200L

    /** NameShield yer tutucusu (Qx1, Qx2...). */
    private val YER_TUTUCU = Regex("Qx\\d+")

    /**
     * İnternet cevabına tanınan süre. 12 Kasım 9.91 kaydında sunucu cevapları
     * 0,7–1,4 sn sürdü; 1,2 sn'lik sınır, 100–200 ms sonra gelecek doğru
     * çevirinin önüne bozuk taslağı çıkarıyordu.
     */
    private val YEDEK_INTERNET_MS = 1500L
    private val TELEFON_BEKLE_MS = 1000L
    private val KAYNAK_ARALIK_MS = 300_000L
    @Volatile private var sonGidenKaynak = ""
    @Volatile private var sonGidenMs = 0L

    /**
     * Yumuşatılmış yapay zekâ süresi. Her başarılı çeviride güncelleniyor.
     * Başlangıç tahmini orta bir değer; birkaç satırda gerçeğe oturuyor.
     */
    @Volatile private var yzOrtMs = 1500L

    /**
     * Taslak, yapay zekâ bu süre içinde cevap vermezse çıkar.
     *
     * Taslağı hemen basınca yazı bir saniye sonra değişiyor ve ekran her
     * satırda oynuyor ("çok karışık"). Beklersek çoğu satırda tek bir yazı
     * belirip hiç değişmiyor.
     *
     * Süre SABİT DEĞİL, son çevirilerin hızına göre ayarlanıyor: hızlı
     * modelde (E2B, ~1,5 sn) taslak satırların çoğunda hiç çıkmıyor; büyük
     * modelde (E4B, ~3 sn) üst sınıra oturup taslak çıkıyor, çünkü orada
     * ekranı 3 sn boş bırakmak daha kötü. Kullanıcının kaydındaki 23 satırda
     * yazı değişimi 23'ten 7'ye iniyor, ekran en çok 1,9 sn boş kalıyor.
     */
    private val taslakBekle: Long
        get() = (yzOrtMs + 300L).coerceIn(700L, 2000L)

    @Volatile private var oncekiMetin: String? = null
    @Volatile private var oncekiKaynak = ""
    private val yiginHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var oncekiKaldir: Runnable? = null

    /**
     * Çevirinin okunması için gereken süre.
     *
     * Sabit süre yanlış: "Ne?" ile iki satırlık cümle aynı kalıyordu. Süre
     * uzunlukla büyüyor, 8 sn'de duruyor. Kullanıcının "en az N sn"
     * kaydırıcısı alt sınır olarak geçerli.
     */
    private fun okumaSuresi(value: String): Long =
        maxOf(prefs.minShowSec * 1000L, minOf(OKUMA_EN_COK, 2000L + value.length * 60L))

    /**
     * Okuma süresinin üst sınırı.
     *
     * Önce 8 sn yazılmıştı; o değer kaynaktaki "satır ekranda ne kadar
     * görünür" sınırıydı, "oyunun altyazısı kalktıktan sonra ne kadar daha
     * durur" sınırı değil. İkincisi 5 sn. 8 sn'de kaplama oyunun belirgin
     * şekilde gerisinde kalıyordu.
     */
    private val OKUMA_EN_COK = 5000L

    /** Üst sıradaki satırı [ONCEKI_TUT] sonra solarak kaldırır. */
    private fun oncekiyiZamanla() {
        oncekiKaldir?.let { yiginHandler.removeCallbacks(it) }
        val r = Runnable {
            oncekiMetin = null
            oncekiKaynak = ""
            overlay.oncekiSoldur(ONCEKI_SOLMA)
        }
        oncekiKaldir = r
        yiginHandler.postDelayed(r, ONCEKI_TUT)
    }

    /**
     * Bir çeviriyi üst sıraya koyar. İki yerde kullanılıyor: geç gelen
     * düzeltme ve biz beklerken geçip giden satırın taslağı. Ekranda güncel
     * bir satır yoksa Overlay bunu kendisi göstermez (tek başına havada
     * duran kutu olmasın).
     */
    private fun ustSiradaGoster(kaynak: String, deger: String) {
        oncekiMetin = deger
        oncekiKaynak = kaynak
        oncekiyiZamanla()
        overlay.oncekiYaz(deger)
    }

    /** Yığını hemen boşaltır (ekran temizlendi ya da çeviri durdu). */
    private fun yiginiBosalt() {
        oncekiKaldir?.let { yiginHandler.removeCallbacks(it) }
        oncekiKaldir = null
        oncekiMetin = null
        oncekiKaynak = ""
    }

    private fun showSubtitle(value: String?, refined: Boolean = false, draft: Boolean = false) {
        if (value == null) {
            // Remove the Turkish overlay text, not its English source.
            // Otherwise old Turkish phrases remain in the own-text filter.
            displayed.remove(normalize(shownValue))
            shownValue = ""
            yiginiBosalt()
        } else {
            displayed.add(normalize(value))
            shownAtMs = android.os.SystemClock.elapsedRealtime()
        }
        // Single-app projection captures the target app separately from our window.
        // Keep the translated caption at the bottom; never move it into gameplay.
        overlay.setSubtitle(
            value, screenHeight - dp(110), refined, draft, -1,
            if (prefs.yigin) oncekiMetin else null,
            protectedTopPx = -1, protectedBottomPx = -1, preferBelow = true
        )
    }

    /** Yapay zekâ çevirisi kendi hafızasına, hızlı çeviri ayrı hafızaya yazılır. */
    /**
     * Sürüm adı APK'dan okunuyor. Eskiden kayda elle "9.35" yazılıyordu ve
     * build.gradle.kts'deki sürüm değişince kayıt yanlış sürümü gösteriyordu.
     */
    private val surumAdi: String get() = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Throwable) { "?" }

    /** Harf ve rakam yapışmış kelime: "WO0de", "O0d", "3old" — OCR çöpünün en net işareti. */
    private val HARF_RAKAM = Regex("\\p{L}\\d|\\d\\p{L}")

    /**
     * Bu okuma hafızaya yazılacak kadar temiz mi?
     *
     * Kullanıcının 6 Kasım 22:42 kaydında bozuk bir okumanın çevirisi hafızaya
     * girmiş ve sonraki her okumada anında geri servis edilmişti:
     *   "YZ hafıza (6 ms): Yaşlı adam Gen I'ye nereden geldiğini sorduğum.
     *    O da vadinin üssünü dedi... ve... kampı o O0d'da."
     * Hafıza kalıcı olduğu için tek bir bozuk kayıt kalıcı hasar veriyor.
     * Ekrana yazmaya devam ediyoruz, sadece HATIRLAMIYORUZ.
     */
    private fun hafizayaUygun(source: String): Boolean =
        !HARF_RAKAM.containsMatchIn(source) && !OcrOnar.cokBozuk(source)

    private fun saveToMemory(source: String, value: String, tag: String) {
        if (!hafizayaUygun(source) || !TranslationCachePolicy10427.reusable(source) ||
            !TranslationQualityGate.mayRemember(source, value)) {
            if (prefs.diagnostics) log("HAFIZAYA ALINMADI (bozuk okuma): ${source.take(60)}")
            return
        }
        if (tag.contains("YZ")) storeAi?.put(source, value) else store?.put(source, value)
    }

    /**
     * [refined] verilmezse etiketten anlaşılır. Yapay zekâ atlandığında taslak
     * kesinleşmiş sayılıyor; orada açıkça true geçiliyor ki önce taslak sonra
     * düzeltilmiş diye iki kez çizilmesin.
     */
    private fun publish(
        source: String,
        rawValue: String?,
        remember: Boolean = true,
        tag: String = "",
        refined: Boolean? = null,
        runId: Long? = null,
    ) {
        // Tek çıkış kapısı: DeepL, OPUS, Google, Firefox ve hafıza
        // aynı kaynak-bağımlı oyun terimi korumasından geçer.
        val value = rawValue?.let {
            ProperNameStructure10427.repair(source,
                YoContext10427.correct(source, GameplayLogRepair299.repairTranslation(source, it)))
        }
        if (value.isNullOrBlank()) return
        if (runId != null && !subtitleGeneration.isCurrent(runId)) {
            // BEGIN_BENCH202
            log("BENCH202|id=$runId|stage=STALE|source=${SubtitleEngineTrace202.field(source)}")
            // END_BENCH202
            log("ESKİ İSTEK ATLANDI: ${source.take(65)}")
            return
        }
        // All producers (dictionary, memory, Google, Firefox, device AI, bridge)
        // converge here. Do not poison caches with structurally suspicious output.
        val outputIssue = TranslationQualityGate.check(source, value)
        if (TranslationQualityGate.mustReject(outputIssue)) {
            log("ANLAM KORUMASI - REDDEDİLDİ ($outputIssue): ${source.take(65)} => ${value.take(65)}")
            // BEGIN_BENCH202
            if (runId != null) log("BENCH202|id=$runId|stage=QUALITY_REJECT|reason=$outputIssue" +
                "|source=${SubtitleEngineTrace202.field(source)}|output=${SubtitleEngineTrace202.field(value)}")
            // END_BENCH202
            return
        }
        if (outputIssue != null && prefs.diagnostics) {
            log("KALİTE UYARISI ($outputIssue): ${source.take(65)} => ${value.take(65)}")
        }
        val simdi = android.os.SystemClock.elapsedRealtime()
        // YALNIZ DOĞRU ÇEVİRİ modunda geç gelen cevap: satır az önce gittiyse
        // ve yeni satırın çevirisi henüz ekranda değilse, geç geleni yine de
        // GÖSTER. Bu modda taslak olmadığı için aksi hâlde hızlı konuşmada
        // ekran boş kalır; çeviri bir satır geriden gelir ama okunur. Yeni
        // satırın cevabı gelince normal yoldan üstüne yazılır.
        // 9.87: cevabın kaynağı, şu an ekrandaki satırın BOZUK OKUNMUŞ hâliyse
        // (aynı satır, OCR başka türlü okumuş) cevap geç değil, tam yerindedir.
        val ayniSatirinCevabi = translatingFor != source &&
            tag.contains("YZ") && OcrOnar.ayniGurultulu(source, translatingFor)
        // 9.92 — EKRANDAKİ ÇEVİRİ HER ZAMAN EKRANDAKİ SATIRA AİT OLSUN.
        //
        // 9.86'da "geç gelen doğru çeviri yine de gösterilsin" kuralı vardı:
        // ÖNCEKİ satırın cevabı, yeni satır okunmuşken ekrana basılıyordu.
        // 12 Kasım 9.91 kaydında (Doğru önce) bu 30'dan fazla kez oldu:
        //   OKUNAN: Nah, I'll be taller.
        //   GEÇ GELDİ: "[hafifçe kıkırdar] Yakında Mark kadar uzun olacaksın."
        // Karakter bir şey söylerken ekranda bir önceki repliğin çevirisi
        // duruyordu. Kullanıcı: "alakasız çeviriler meydana geliyor, hiç bir
        // şekilde anlamıyorum". Artık geçmiş satırın cevabı ekrana çıkmaz,
        // yalnız hafızaya girer. Tek istisna aynı satırın bozuk okumasıdır.
        val gecGoster = ayniSatirinCevabi
        if (ayniSatirinCevabi) log("AYNI SATIRIN CEVABI (bozuk okuma farkı, gösteriliyor)")
        if (translatingFor != source && !gecGoster) {
            // Replik değişmiş; ekrana yazmıyoruz ama hafızaya alıyoruz.
            // Aynı cümle tekrar geçtiğinde anında çıkacak.
            if (remember && !value.startsWith("⚠")) saveToMemory(source, value, tag)
            // GEÇ GELEN DÜZELTME BOŞA GİTMESİN. Satır az önce üst sıraya
            // çıktıysa, oradaki taslağın yerine düzeltilmişi geçer: kullanıcı
            // doğrusunu yine de görür. Çok eskiyse dokunulmaz, sonraki
            // sahnenin üstüne düşmesin.
            if (prefs.yigin && tag.contains("YZ") && oncekiKaynak == source &&
                simdi - shownAtMs < GEC_SINIR
            ) {
                ustSiradaGoster(source, value)
                log("GEÇ KALDI (üst sırada gösterildi): ${value.take(50)}")
                return
            }
            log("GEÇ KALDI (hafızaya alındı): ${value.take(50)}")
            return
        }
        val ms = startedAt.remove(source)?.let { simdi - it }
        if (ms != null) sonCeviriMs = ms

        // 199: Dengeli canlı altyazı yalnız bir defa ekrana yazılır. Güçlü
        // motorun daha geç gelen sonucu hafızaya girebilir ama yazıyı zıplatmaz.
        val displayDecision203 = if (runId != null && prefs.ceviriBicimi == Prefs.BICIM_DENGELI) {
            stableCaption199.decide203(runId, subtitleGeneration::isCurrent, simdi,
                isLocalBackup = tag.contains("hızlı yerel yedek"),
                currentCaptionStillVisible = shownSource == source,
                issue = outputIssue)
        } else StableCaptionGate199.Decision.FIRST
        if (displayDecision203 == StableCaptionGate199.Decision.LOCKED) {
            // BEGIN_BENCH202
            log("BENCH202|id=$runId|stage=LOCKED|tag=${SubtitleEngineTrace202.field(tag)}" +
                "|source=${SubtitleEngineTrace202.field(source)}|output=${SubtitleEngineTrace202.field(value)}")
            // END_BENCH202
            if (remember && !value.startsWith("⚠")) saveToMemory(source, value, tag)
            if (prefs.diagnostics) log("GEÇ YANIT (ekran sabit, hafızaya alındı): ${source.take(55)}")
            return
        }
        if (displayDecision203 == StableCaptionGate199.Decision.UPGRADE) {
            log("KALİTE YÜKSELTMESİ 203: aynı replikte tek kontrollü düzeltme")
            // BEGIN_BENCH202
            if (runId != null) log("BENCH202|id=$runId|stage=QUALITY_UPGRADE" +
                "|source=${SubtitleEngineTrace202.field(source)}|output=${SubtitleEngineTrace202.field(value)}")
            // END_BENCH202
        }
        // AYNI YAZIYI YENİDEN ÇİZME. Yapay zekâ çoğu kısa satırda taslağın
        // birebir aynısını döndürüyor ("Evet." -> "Evet.", "Tam içeride." ->
        // "Tam içeride."). Ekranı yine de tazelersek yazı boşuna yanıp sönüyor;
        // kullanıcının gözünde "karışık" görünmesinin başlıca sebebi buydu.
        // Hafızaya ve kayda normal şekilde giriyor, sadece çizim atlanıyor.
        if (shownSource == source && ayniYazi(shownValue, value)) {
            if (remember && !value.startsWith("⚠")) saveToMemory(source, value, tag)
            okumaMs = okumaSuresi(value)
            log("YAZILAN$tag${if (ms != null) " ($ms ms)" else ""} · taslakla aynı, ekran tazelenmedi: $value")
            return
        }

        log(if (ms != null) "YAZILAN$tag ($ms ms): $value" else "YAZILAN$tag: $value")
        // BEGIN_BENCH202
        if (runId != null) log("BENCH202|id=$runId|stage=DISPLAY_ACCEPTED|ms=${ms ?: -1L}" +
            "|tag=${SubtitleEngineTrace202.field(tag)}|source=${SubtitleEngineTrace202.field(source)}" +
            "|output=${SubtitleEngineTrace202.field(value)}")
        // END_BENCH202

        // YIĞIN: yeni bir replik geliyorsa ve öncekinin üstünden az zaman
        // geçtiyse, eski satır silinmek yerine üst sıraya çıkar. Aynı repliğin
        // düzeltilmişi (shownSource == source) üste çıkmaz: aynı cümle iki kez
        // görünürdü.
        if (prefs.yigin && shownSource.isNotEmpty() && shownSource != source &&
            shownValue.isNotEmpty() && simdi - shownAtMs < YIGIN_ICINDE &&
            !benzerMetin(shownValue, value)
        ) {
            oncekiMetin = shownValue
            oncekiKaynak = shownSource
            oncekiyiZamanla()
        } else if (shownSource != source) {
            yiginiBosalt()
        }

        shownSource = source
        shownValue = value
        okumaMs = okumaSuresi(value)
        everShown = true
        if (remember && !value.startsWith("⚠")) saveToMemory(source, value, tag)
        if (!value.startsWith("⚠")) addHistory(source, value)
        showSubtitle(
            value,
            refined = refined ?: tag.contains("YZ"),
            draft = refined != true && tag.contains("taslak"),
        )
    }

    /**
     * DOĞRULUK ÖNCELİKLİ (taslak + düzeltme):
     *  - Hızlı çeviri hemen GRİ (taslak) olarak çıkar, hafızaya yazılmaz.
     *  - Yapay zekâ yarıda KESİLMEZ; bitince turkuaz noktalı doğru çeviri taslağın yerine geçer.
     *    Replik geçmişse ekrana yazmaz ama hafızaya alır: sonraki izlemede anında doğrusu çıkar.
     *  - Birikme olmasın: sırası geldiğinde artık en yeni replik değilse atlanır.
     */
    private fun accurateOnDevice(l: LlmTranslate, source: String) {
        llmLatest = source
        val forAi = aiInput(source)
        val previous = synchronized(recentLines) { recentLines.toList() }
        synchronized(recentLines) {
            recentLines.addLast(source)
            while (recentLines.size > 3) recentLines.removeFirst()
        }
        // ÖNEMLİ: taslak ile yapay zekâ artık AYNI iş parçacığında, sırayla çalışıyor.
        // Eskiden ikisi paralel başlıyordu; yapay zekâyı taslağa bakarak atlayabilmek
        // için önce taslağın bitmesi gerekiyor. Taslak zaten 50 ms sürüyor.
        scope.launch {
            val quick = fastTranslateKeepingName(source)
            // Hızlı çeviri başarısızsa (boş ya da İngilizcesiyle aynı) bozuk taslak gösterme; yapay zekâyı bekle.
            //
            // AYNI METİN HER ZAMAN BAŞARISIZLIK DEĞİL: satır yalnızca özel
            // adlardan oluşuyorsa çevirisi kendisidir. Kullanıcının 11 Kasım
            // kaydında "Raymond, Raymond." tam bu yüzden ekrana gelmedi:
            //   TASLAK GÖSTERİLMEDİ (hızlı çeviri başarısız): Raymond, Raymond.
            // Doğru çeviri "başarısız" sayılıp yapay zekâ beklendi, satır da
            // o arada geçti ("GEÇ KALDI").
            val failed = quick.isNullOrBlank() ||
                (quick.trim().equals(source.trim(), ignoreCase = true) && !AiGuard.yalnizAdMi(source))
            if (failed) log("TASLAK GÖSTERİLMEDİ (hızlı çeviri başarısız): $source")

            // 9.87: satır yalnız DEFTERDEKİ adlardan oluşuyorsa ("Alien.")
            // çevirisi kendisidir; yapay zekâ onu "Yabancı." yapıyordu.
            if (AiGuard.defterAdiMi(source)) {
                log("YZ ATLANDI (satır yalnız özel ad): $source")
                if (translatingFor == source) {
                    publish(source, source, remember = false, tag = " · taslak kesin", refined = true)
                }
                return@launch
            }

            // Taslak sağlamsa yapay zekâyı HİÇ çağırma: gecikme de yanıp sönme de biter,
            // üstelik yapay zekânın doğru taslağı bozma ihtimali ortadan kalkar.
            // Kullanıcı ayardan "her satırda çalışsın" derse bu atlama devre dışı.
            if (!prefs.alwaysAi && !AiGuard.yzGerekli(source, quick)) {
                log("YZ ATLANDI (taslak yeterli): ${quick?.take(50)}")
                if (!failed && translatingFor == source) {
                    // Taslak kesinleşti: TEK çizimde kesin hâliyle çıkıyor ve
                    // hafızaya alınıyor (bir daha hiç beklenmesin). Eskiden
                    // önce taslak sonra düzeltilmiş diye iki kez çiziliyordu.
                    publish(source, quick!!, remember = false, tag = " · taslak kesin", refined = true)
                    saveToMemory(source, quick, "")
                }
                return@launch
            }

            // TASLAK GECİKMELİ ÇIKAR.
            //
            // Yapay zekâ hızlı modelde 1,0-1,5 sn'de cevap veriyor. Taslağı
            // hemen basarsak yazı bir saniye sonra değişiyor; satırların
            // neredeyse tamamında bu oluyor ve ekran sürekli oynuyor.
            // Artık taslak yalnız yapay zekâ GECİKİRSE çıkıyor: cevap
            // zamanında gelirse ekranda tek bir yazı belirir, hiç değişmez.
            // Yapay zekâ yavaşsa taslak yine devreye girer, ekran boş kalmaz.
            // EKRAN BOŞSA BEKLEME YOK. Gecikmenin anlamı, kullanıcı önceki
            // satırı okurken yazının boşuna oynamasını engellemek. Ekranda
            // hiçbir şey yoksa beklemek sadece boş ekran demek; üstelik satır
            // bu sırada geçerse hiç görünmeden kaybolur. Kullanıcının kaydında
            // açılıştaki ilk iki replik tam böyle yok oldu.
            val taslakIsi = if (!failed) {
                if (prefs.yalnizDogru) {
                    // DOĞRU ÇEVİRİ ÖNCE, AMA EKRAN BOŞ KALMAZ (9.90).
                    //
                    // 9.86'da taslak bu modda HİÇ çıkmıyordu. Telefon modeli
                    // satır başına ~3 sn sürdüğü için hızlı konuşmada cevap
                    // hep geç kaldı ve ekran boş kaldı (kullanıcı: "çok geç,
                    // hatta hiç gelmiyor"). Artık yapay zekâya YEDEK_TASLAK_MS
                    // kadar süre veriliyor: yetişirse ekranda yalnız doğru
                    // çeviri görünür; yetişmezse taslak çıkar, doğrusu gelince
                    // yerine geçer.
                    scope.launch {
                        delay(YEDEK_TASLAK_MS)
                        if (translatingFor == source && shownSource != source) {
                            publish(source, quick!!, remember = false, tag = " · taslak (gecikme yedeği)")
                        }
                    }
                } else if (!prefs.dogruyuBekle) {
                    // VARSAYILAN: taslak anında çıkar. Uygulamanın baştan beri
                    // yaptığı şey; gecikme bir tercih, varsayılan değil.
                    if (translatingFor == source) {
                        publish(source, quick!!, remember = false, tag = " · taslak")
                    }
                    null
                } else if (shownValue.isEmpty()) {
                    if (translatingFor == source) {
                        publish(source, quick!!, remember = false, tag = " · taslak")
                    }
                    null
                } else scope.launch {
                    delay(taslakBekle)
                    if (translatingFor == source) {
                        if (shownSource != source) {
                            publish(source, quick!!, remember = false, tag = " · taslak")
                        }
                    } else if (prefs.yigin) {
                        // Satır biz beklerken geçti. Hiç göstermemektense üst
                        // sırada göster: yığın tam bunun için var.
                        ustSiradaGoster(source, quick!!)
                    }
                }
            } else null

            // BURADA DA KESMİYORUZ (refineOnDevice ile aynı gerekçe).
            //
            // Eskiden geçmiş satırın çevirisi kesiliyordu, "yapay zekâ
            // ekrandaki satırla uğraşsın" diye. Ama yapay zekâ ortalama
            // 3808 ms sürüyor ve altyazı 2-4 sn duruyor: kesme kuralı
            // çevirinin neredeyse hiç bitmemesi anlamına geliyordu.
            // Sıra kendiliğinden temizleniyor; aşağıdaki "llmLatest == source"
            // denetimi bekleyen eski satırı kilidi aldığında düşürüyor.
            val t0 = android.os.SystemClock.elapsedRealtime()
            val better = l.translate(forAi, previous, prefs.contentType, prefs.gameName, discardIfStale = false) {
                llmLatest == source
            }
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            if (better.isNullOrBlank()) {
                l.lastError?.let { log("YAPAY ZEKÂ (telefon) hata: $it") }
                // SON ÇARE: taslak da yoksa satır tamamen kayboluyordu. Çevrilemeyen
                // satırı olduğu gibi göstermek boş ekrandan iyi. Hafızaya YAZILMAZ:
                // sonraki izlemede yeniden çevrilmeyi dener.
                if (prefs.yalnizDogru && !failed && translatingFor == source && shownSource != source) {
                    log("YZ CEVAP VERMEDİ · yedek olarak taslak gösteriliyor")
                    publish(source, quick!!, remember = false, tag = " · taslak (yedek)")
                }
                if (failed && translatingFor == source && shownSource != source) {
                    log("ÇEVRİLEMEDİ, KAYNAK GÖSTERİLİYOR: ${source.take(50)}")
                    publish(source, source, remember = false, tag = " · çevrilemedi")
                }
                return@launch
            }
            // Adları geri koy, sonra denetle.
            val duzgun = yzDuzelt(source, better)
            // TASLAK YOKSA denetim gevşer: ekranda kalacak bir şey olmadığı için
            // sıkı denetim satırı tamamen boş bırakıyordu. Kusurlu çeviri boştan iyi.
            val sebep = if (failed) {
                if (AiGuard.yetersizMi(source, duzgun)) "çevrilmemiş" else null
            } else {
                AiGuard.redSebebi(source, quick, duzgun)
            }
            if (sebep != null) {
                log("YZ REDDEDİLDİ ($sebep)${if (failed) " · ekranda bir şey yok" else " · taslak kalıyor"}: ${duzgun.take(50)}")
                // Yalnız-doğru modunda taslak hiç gösterilmedi; reddedilen
                // cevabın yerine ekrana çıkacak tek şey o.
                if (prefs.yalnizDogru && !failed && translatingFor == source && shownSource != source) {
                    publish(source, quick!!, remember = false, tag = " · taslak (yedek)")
                }
                return@launch
            }
            // Cevap kullanılabilir: bekleyen taslak gereksiz, iptal. Yukarıdaki
            // red yollarında BİLEREK iptal edilmiyor; orada ekrana çıkacak tek
            // şey taslak olduğu için zamanı gelince görünmesi gerekiyor.
            taslakIsi?.cancel()
            // Taslak gecikmesi bu ölçüme göre ayarlanıyor (bkz. taslakBekle).
            yzOrtMs = (yzOrtMs * 2 + ms) / 3
            startedAt.remove(source)
            publish(source, aiPrefix(source) + duzgun, remember = true, tag = " · YZ $ms ms (sıra ${l.lastWaitMs} ms, kurulum ${l.lastKurulumMs} ms${if (l.lastSicak) " sıcak" else ""})")
        }
    }

    // ---------------------------------------------- TV: çeviriyi telefona yaptır

    private val bridgeBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var bridgeNext: Pair<String, List<String>>? = null
    private val bridgeSeq = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var bridgeLastWarnMs = 0L

    /**
     * Önce gri taslak (TV'nin kendi hızlı çevirisi), sonra telefondaki yapay zekânın doğru
     * çevirisi. Telefon meşgulken gelen ara replikler atlanır, hep en yenisi gönderilir.
     */
    private fun bridgeTranslate(source: String) {
        llmLatest = source
        val previous = synchronized(recentLines) { recentLines.toList() }
        synchronized(recentLines) {
            recentLines.addLast(source)
            while (recentLines.size > 3) recentLines.removeFirst()
        }
        scope.launch {
            val quick = fastTranslateKeepingName(source)
            val failed = quick.isNullOrBlank() || quick.trim().equals(source.trim(), ignoreCase = true)
            if (!failed && translatingFor == source && shownSource != source) {
                publish(source, quick!!, remember = false, tag = " · taslak")
            }
        }
        if (!bridgeBusy.compareAndSet(false, true)) {
            bridgeNext = source to previous   // telefon meşgul: bitince EN YENİSİ gidecek
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                var job: Pair<String, List<String>>? = source to previous
                while (job != null) {
                    val (src, prev) = job
                    val seq = bridgeSeq.incrementAndGet()
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    val r = PhoneBridge.translate(
                        prefs.bridgeHost, prefs.bridgePin, seq, aiInput(src), prev,
                        prefs.contentType, prefs.gameName
                    )
                    val ms = android.os.SystemClock.elapsedRealtime() - t0
                    when (r) {
                        is PhoneBridge.Result.Ok -> {
                            val duzgun = yzDuzelt(src, r.text)
                            val sebep = AiGuard.redSebebi(src, null, duzgun)
                            if (sebep != null) {
                                log("YZ REDDEDİLDİ ($sebep) · TV taslağı kalıyor: ${duzgun.take(50)}")
                            } else {
                                startedAt.remove(src)
                                publish(src, aiPrefix(src) + duzgun, remember = true, tag = " · YZ telefon $ms ms")
                            }
                        }
                        is PhoneBridge.Result.Skip -> {}
                        is PhoneBridge.Result.Err -> {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - bridgeLastWarnMs > 30_000) {
                                bridgeLastWarnMs = now
                                log("TELEFON ÇEVİRİSİ YOK (${r.reason}) · bu sırada TV'nin hızlı çevirisi kullanılıyor")
                            }
                        }
                    }
                    job = bridgeNext
                    bridgeNext = null
                }
            } finally {
                bridgeBusy.set(false)
            }
        }
    }

    /** Yapay zekâya gidecek metin: konuşmacı adı korunur, OCR hataları düzeltilir, küfür olduğu gibi kalır. */
    private fun aiInput(source: String): String {
        val (name, body) = splitSpeaker(source)
        val fixed = OfflineFix.cleanForAi(body)
        if (fixed != body) log("YZ'YE GİDEN (OCR düzeltildi): $fixed")
        // "In Spanish: ..." dil etiketi yapay zekâya gitmez; çevirinin başına "[İspanyolca]" konur.
        if (name != null && OfflineFix.languageLabel(name) != null) return fixed
        return if (name == null) fixed else "$name: $fixed"
    }

    /** "In Spanish: Shit!" -> "[İspanyolca] " (yapay zekâ çevirisinin başına eklenir). */
    private fun aiPrefix(source: String): String {
        val (name, _) = splitSpeaker(source)
        return name?.let { OfflineFix.languageLabel(it) }?.let { "[$it] " } ?: ""
    }

    /** Telefondaki yapay zekâ ile düzgün çeviri; gelince ekrandakinin üstüne yazar. */
    /**
     * YAPAY ZEKÂ ÇIKTISININ TEK KAPISI.
     *
     * Üç ayrı yerde (hız yolu, doğruluk yolu, TV köprüsü) aynı iki iş
     * yapılıyordu — ama üçünde de EKSİK yapılıyordu: ad geri konuyor,
     * seslenme onarılmıyordu. `AiGuard.hitapDuzelt` yalnız hafızadan gelen
     * kayda uygulanıyordu, yani 9.62'de yazdığım "Yo -> Lan olmaz" düzeltmesi
     * taze yapay zekâ çıktısı için hiç çalışmıyordu; işi istemdeki kural
     * görüyordu. İstem her satırda baştan işlendiği için o kuralı oradan
     * kaldırınca eksik ortaya çıktı.
     *
     * Artık tek fonksiyon: üç yol da buradan geçiyor, biri eklenirse de
     * buradan geçmek zorunda. Koda bağlanan kural istemde yer tutmuyor.
     */
    private fun yzDuzelt(kaynak: String, ham: String): String =
        AiGuard.hitapDuzelt(kaynak, NameFix.koru(kaynak, ham.trim()))

    private fun refineOnDevice(l: LlmTranslate, source: String) {
        llmLatest = source
        val forAi = aiInput(source)
        // YARIDA KESMİYORUZ.
        //
        // Eskiden yeni satır gelince süren çeviri iptal ediliyordu ("sıra
        // ekrandaki satırda olsun"). Ölçüm bunun bedelini gösterdi:
        // kullanıcının 11 Kasım 9.65 kaydında yapay zekâ ortalama 3808 ms
        // sürüyor, altyazı 2-4 sn duruyor; yani çeviri neredeyse HER
        // seferinde bitmeden kesiliyordu. 56 replikten yalnız 5'i
        // düzeltilebildi, 44'ü zayıf taslakla ekranda kaldı.
        //
        // Artık süren çeviri bitiyor. Biten çeviri boşa gitmiyor: satır hâlâ
        // yığının üst sırasındaysa oraya yazılıyor, geçmişse hafızaya
        // giriyor (aynı replik bir daha geçtiğinde anında doğrusu çıkıyor).
        // Sıra kendiliğinden temizleniyor: bekleyen satır kilidi aldığında
        // artık en yeni satır değilse düşüyor, yani kuyruk büyümüyor.
        val previous = synchronized(recentLines) { recentLines.toList() }
        synchronized(recentLines) {
            recentLines.addLast(source)
            while (recentLines.size > 3) recentLines.removeFirst()
        }
        scope.launch {
            val t0 = android.os.SystemClock.elapsedRealtime()
            // Sırada beklerken replik geçtiyse (ve daha yenisi geldiyse) boşuna çalışma.
            // Sadece hâlâ ekrandaki (ya da en son gelen) satır için çalış.
            val better = l.translate(
                forAi, previous, prefs.contentType, prefs.gameName,
                // Biten çeviri atılmaz; publish geç kalıp kalmadığına karar verir.
                discardIfStale = false,
            ) {
                // Bu denetim yalnız SIRADA BEKLEYEN satır için: kilidi
                // aldığında artık en yeni satır değilse boşuna çalışmasın.
                llmLatest == source
            }
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            if (better.isNullOrBlank()) {
                l.lastError?.let { log("YAPAY ZEKÂ (telefon) hata: $it") }
                return@launch
            }
            val duzgun = yzDuzelt(source, better)
            val sebep = AiGuard.redSebebi(source, null, duzgun)
            if (sebep != null) {
                log("YZ REDDEDİLDİ ($sebep): ${duzgun.take(50)}")
                return@launch
            }
            // 10.6 — TELEFON İNTERNETİ KISA BİR SÜRE BEKLER.
            // 13 Kasım 10.5 kaydında aynı satır ekranda üç kez değişti:
            // taslak -> telefon ("Bu adamı neden güvenelim ki?") -> internet.
            // İnternet bu satır için hâlâ yoldaysa telefonun sonucu en çok
            // TELEFON_BEKLE_MS tutulur; internet gelirse telefonunki gösterilmez,
            // gelmezse (hata, kota, gecikme) telefonunki çıkar.
            val bekleyen = internetBekleniyor[source]
            if (bekleyen != null) {
                val internetOldu = withTimeoutOrNull(TELEFON_BEKLE_MS) { bekleyen.await() } ?: false
                if (internetOldu) {
                    internetGeldi.remove(source)
                    log("TELEFON SONUCU ATLANDI (internet önce geldi)")
                    return@launch
                }
            }
            // 9.98: internetin çevirisi bu satır için zaten geldiyse telefonun
            // sonucu onun üstüne yazılmaz (internet modeli daha güçlü).
            if (internetGeldi.remove(source)) {
                log("TELEFON SONUCU ATLANDI (internet önce geldi)")
                return@launch
            }
            startedAt.remove(source)
            publish(source, aiPrefix(source) + duzgun, remember = true, tag = " · YZ $ms ms (sıra ${l.lastWaitMs} ms)")
        }
    }

    /** 10.6: internet cevabı beklenen satırlar (true = internet çevirisi geldi). */
    private val internetBekleniyor =
        java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()

    /** İnternet çevirisi gelen satırlar; telefonun geç sonucu bunların üstüne yazılmaz. */
    private val internetGeldi: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Ağsız, düşük gecikmeli yedek; güçlü zinciri tekrar çağırmaz.
     * Genel metin çevirisidir; belirli film/oyun repliği ezberlemez.
     */
    private suspend fun fastLocalCaption199(source: String): String? {
        val (speaker, body) = splitSpeaker(source)
        // Yerel motor özel adı sözcük sanmasın; Qx jetonları korunamadıysa
        // bozuk taslağı basmak yerine güçlü çeviriyi bekle.
        val shielded = NameShield.shield(GoogleSourcePreparation.prepare(body))
        val raw = when {
            fast?.ready == true -> fast?.translate(shielded.text)
            firefox?.hazir == true -> firefox?.cevir(shielded.text)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (shielded.slots.any { !raw.contains(it.token) }) return null
        val translated = TurkishFix.duzelt(NameShield.restore(raw, shielded.slots))
        if (translated.equals(body.trim(), ignoreCase = true)) return null
        if (TranslationQualityGate.mustReject(TranslationQualityGate.check(body, translated))) return null
        return if (speaker == null) translated else "$speaker: $translated"
    }

    /** A finished request cannot overwrite a newer subtitle, even if the text is identical. */
    private fun publishForRun(
        runId: Long,
        source: String,
        value: String?,
        remember: Boolean = true,
        tag: String = "",
        refined: Boolean? = null,
    ) = publish(source, value, remember, tag, refined, runId)

    private suspend fun runTranslation(source: String, runId: Long) {
        // 9.91 — "HIZLI" BİÇİM: yapay zekâ hiç çalışmaz (telefondaki de,
        // internetteki de). Sözlük, hafıza ve anında çeviri çalışır; altyazı
        // gecikmez, sonradan değişmez.
        val hizli = LiveEnginePolicy.useFastPipeline(prefs.ceviriBicimi, prefs.mode)
        val mode = if (hizli) Prefs.MODE_INSTANT else prefs.mode
        val engine = ai

        // TV, çeviriyi telefondaki yapay zekâya yaptırıyorsa yapay zekâ "hazır" sayılır:
        // hazır sözlük ve kaba çeviri hafızası telefonun doğru çevirisinin önüne geçmesin.
        val bridge = isTv && prefs.bridgeOn && prefs.bridgeHost.isNotBlank() && prefs.bridgePin.isNotBlank()
        val llmReady = mode == Prefs.MODE_INSTANT && !hizli && (llm?.ready == true || bridge)

        // 1) HIZLI MOD: hazır sözlük en üstte; kısa replikler anında çıkar.
        // DENGELİ MOD: güçlü motor varsa otomatik hazır cümleler değil,
        // yalnız kullanıcının kendi sözlüğü kesin öncelik taşır.
        //
        // Eskiden yapay zekâ hazırken hazır sözlük atlanıyordu ("bağlamı
        // bilmez, sabit karşılığı yanlış olabilir"). Bu gerekçe GENEL kelime
        // karşılıkları için doğru, ama bu sözlükteki satırların çoğu zaten
        // "yapay zekâ bunu yanlış çevirdiği için" elle yazıldı. Sonuç ters
        // dönüyordu: düzelttiğimiz satır ekrana hiç gelmiyordu, çünkü bir
        // önceki oturumda hafızaya girmiş bozuk çeviri önce servis ediliyordu.
        // Kullanıcının 11 Kasım 9.61 kaydından (solda hafızanın verdiği,
        // sağda sözlükte YAZILI olan):
        //   "These two say they're for Boobie."
        //     hafıza  "Bu ikisi Boobie'ye ait diyorlar."
        //     sözlük  "Bu ikisi Boobie için geldiklerini söylüyor."
        //   "Yo, we're here to pick up."
        //     hafıza  "Lan, malı almaya geldik."
        //     sözlük  "Hey, ..."                 ("Lan" burada küfür ekliyor)
        //   "Yo Ray, it's him."
        //     hafıza  "Hey Ray, o o."
        //     sözlük  "işte o"
        // AiGuard.yzGerekli zaten "satır tamamen kalıptan geldiyse yapay
        // zekâyı çağırma" diyor; burayı da ona eşitlemek iki katmanın
        // birbiriyle çelişmesini bitiriyor.
        val (nameIn, bodyIn) = splitSpeaker(source)
        // Kaydın birebir doğruladığı teknik menü etiketleri için yanlış
        // "Ruh Öfkesi" gibi çevirileri ve eski taslak hafızasını kullanma.
        if (hizli) {
            GameplayLogRepair299.verifiedUiLabel(bodyIn)?.let { label ->
                publishForRun(runId, source,
                    if (nameIn == null) label else "$nameIn: $label",
                    remember = false, tag = " · doğrulanmış oyun menüsü")
                return
            }
        }
        // Genel çeviri ilkesi: önceki oyun/video kayıtları için yazılmış birebir
        // replik yanıtları artık canlı çeviriyi bypass etmez. Böylece aynı İngilizce
        // cümle farklı bir bağlamda geçmişe ait sabit Türkçeye zorlanmaz.
        // Oturum-içi son üç replik DeepL'in context alanında kalır.
        val modelFirst = NaturalTranslationPolicy10427.modelFirst(
            prefs.ceviriBicimi == Prefs.BICIM_DENGELI,
            OfflineFix.gucluFn != null,
            bodyIn
        )
        // Dengeli + güçlü motor: yalnız kullanıcının açıkça tanımladığı
        // sözlük veya ses etiketi çeviriyi direkt belirler. Genel hazır
        // cümleler (bağlama göre anlamı değişebilir) modeli gölgelemez.
        val dictHit = if (modelFirst) OfflineFix.lookupUserOrSound(bodyIn)
                      else OfflineFix.lookup(bodyIn)
        dictHit?.let { fixed ->
            // Aynı satır için hafızada BAŞKA bir çeviri varsa bayattır: sil.
            // Yoksa sözlüğü değiştirdiğimiz her satır için kullanıcının
            // "Çeviri hafızasını temizle" demesi gerekirdi.
            val tam = if (nameIn == null) fixed else "$nameIn: $fixed"
            // listOfNotNull: "s?.get(...) ?: continue" sonrası Kotlin s'yi
            // hâlâ null olabilir sayıyor ve s.forget(...) derlenmiyor.
            for (s in listOfNotNull(storeAi, store)) {
                val eski = s.get(source) ?: continue
                if (eski.trim() == tam.trim()) continue
                log("HAFIZADAN SİLİNDİ (sözlükte karşılığı var): ${eski.take(50)}")
                s.forget(source)
            }
            publishForRun(runId, source, tam, remember = false)
            return
        }

        // 10.22: Kanıtlı biçimde yarım kalan iki altyazıyı tek metin olarak
        // mevcut ücretsiz motora ver. Önceki altyazı zaten ekranda gösterilmiş
        // olabilir; burada TAM cümle görünür. Tek ağ çağrısı yapılır, ayrı
        // bir bağlam isteği yoktur. Sonuç hafızaya bağımsız satır olarak yazılmaz.
        // Yalnız Google/HIZLI yoluna uygulanır; diğer motorların davranışı aynı.
        if (hizli && prefs.googleCeviri && GoogleCeviri.kullanilabilir && nameIn == null) {
            val joined = dialogueContext.joinedTranslationInput(source)
            if (joined != null) {
                val full = fastTranslateKeepingName(joined)
                if (!full.isNullOrBlank() && !full.trim().equals(joined, ignoreCase = true)) {
                    publishForRun(runId, source, full, remember = false, tag = " · bağlı replik")
                    return
                }
            }
        }

        // 2) Hafıza. Yapay zekâ hazırsa yalnızca YAPAY ZEKÂ hafızası; eski kaba çeviriler kullanılmaz.
        // 10.0 — ESKİ TASLAKLAR YAPAY ZEKÂNIN ÖNÜNÜ KESMESİN.
        // İnternet modunda llmReady yanlış olduğu için taslak hafızası da
        // okunuyordu: Hızlı modda kaydedilmiş bozuk taslak ("Eski Archer
        // oturmuş, kafa, ... eğildi:") 5 ms'de ekrana basılıyor, satır yapay
        // zekâya hiç gitmiyordu. Yapay zekâ yolu varsa (internet ya da telefon)
        // yalnız YAPAY ZEKÂ hafızası okunur.
        val yzYoluVar = !hizli && (mode != Prefs.MODE_INSTANT || llmReady)
        val remembered = if ((modelFirst && NaturalTranslationPolicy10427.dependsOnContext(source)) ||
            !TranslationCachePolicy10427.reusable(source)) null
            else if (yzYoluVar) storeAi?.get(source)
            else (storeAi?.get(source) ?: if (prefs.googleCeviri) null else store?.get(source))
        remembered?.let {
            // KENDİNİ ONARAN HAFIZA. Hafıza denetimden geçmeden ekrana basılıyor;
            // bir kez bozuk kayıt girince sonsuza kadar geri geliyordu
            // ("We going up" -> "Yükseliyoruz"). Korunan kalıbı bozan kayıt
            // siliniyor ve satır aşağıda yeniden çevriliyor.
            val bozuk = AiGuard.hafizaBozuk(source, it)
                ?: TranslationQualityGate.check(source, it)?.name
            if (bozuk == null) {
                // Hafızadaki kayıt da seslenme onarımından geçer: eski
                // oturumlarda "Yo," -> "Lan" diye girmiş kayıtlar var ve
                // hafıza yapay zekâ denetimine uğramadan ekrana basılıyor.
                // Hafızaya geri YAZMIYORUZ: kayıt hangi hafızadan geldiyse
                // orada kalsın. Kaba çeviri hafızasından gelen bir satırı
                // yapay zekâ hafızasına yazmak onu kalıcı olarak "yapay zekâ
                // çevirisi" sayardı. Onarım ucuz ve her seferinde aynı sonucu
                // verdiği için gösterim anında yapılıyor.
                val duzgun = AiGuard.hitapDuzelt(source, it)   // hafıza: ad zaten yerinde
                publishForRun(runId, source, duzgun, remember = false, tag = if (yzYoluVar) " · YZ hafıza" else "")
                return
            }
            log("HAFIZADAN SİLİNDİ ($bozuk): ${it.take(50)}")
            storeAi?.forget(source)
            store?.forget(source)
        }

        if (mode == Prefs.MODE_INSTANT) {
            if (bridge) {
                bridgeTranslate(source)
                return
            }
            val l = llm
            val llmOn = l != null && l.ready && !hizli
            // 9.90: "yalnız doğru çeviri" kutusu "hız öncelikli" ayarında hiç
            // işlemiyordu (12 Kasım 9.89 kaydı: başlıkta kutu açık, her satırda
            // taslak ekranda). Kutu açıksa denetimli yoldan gidilir.
            if (l != null && llmOn && (prefs.accuracyFirst || prefs.yalnizDogru)) {
                accurateOnDevice(l, source)
                return
            }
            val quick = fastTranslateKeepingName(source)
            if (!quick.isNullOrBlank()) {
                // Model hazırsa kaba çeviri hafızaya YAZILMAZ; hafızaya düzgün olan girer.
                publishForRun(runId, source, quick, remember = !llmOn && !prefs.googleCeviri)
                if (l != null && llmOn) refineOnDevice(l, source)
                return
            }
            if (l != null && llmOn) {
                refineOnDevice(l, source)
                return
            }
            // Sessiz kalmıyoruz: sebebini hem ekrana hem kayda yazıyoruz.
            val reason = fast?.lastError
            log("TELEFON ÇEVİRİSİ YOK: ${reason ?: "model hazır değil"}")
            if (prefs.diagnostics) {
                publishForRun(runId, source, "⚠ Türkçe model hazır değil · ${reason ?: "indirilmedi"}")
            } else {
                // Çevrimdışı modda da boş geçmiyoruz: çeviri çıkmadıysa satırın
                // kendisi görünüyor. Hafızaya yazılmaz, sonra yeniden denenir.
                publishForRun(runId, source, source, remember = false, tag = " · çevrilemedi")
            }
            return
        }

        if (mode == Prefs.MODE_HYBRID) {
            val quick = fastTranslateKeepingName(source)
            if (!quick.isNullOrBlank()) publishForRun(runId, source, quick)
        }

        // 10.7: Google Çeviri açıkken anahtar ve kota hiç kullanılmaz; satır
        // doğrudan Google'ın (Chrome'daki) çevirisiyle çıkar.
        if (prefs.googleCeviri || prefs.activeKey.isBlank() || engine == null) {
            if (!prefs.googleCeviri) log("API anahtarı yok, telefon çevirisine düşülüyor")
            val quick = fastTranslateKeepingName(source)
            if (!quick.isNullOrBlank()) publishForRun(runId, source, quick)
            else if (prefs.diagnostics) publishForRun(runId, source, "⚠ API anahtarı girilmemiş")
            return
        }

        // 9.90: İNTERNET CEVABI GECİKİRSE EKRAN BOŞ KALMASIN.
        // 12 Kasım 9.89 kaydında ilk iki satır hiç görünmedi: cevap satır
        // geçtikten sonra geldi ("GEÇ KALDI"), o arada ekranda bir şey yoktu.
        // 9.95 — İNTERNET GECİKİNCE TELEFONDAKİ YAPAY ZEKÂ DEVREYE GİRER.
        // Telefondaki model internet modunda da yüklü ve boşta bekliyordu;
        // 12 Kasım 9.94 kaydında internet hiç cevap vermedi, her satır taslak
        // kaldı. Artık gecikme yedeğiyle birlikte telefona da verilir: hangisi
        // önce gelirse o görünür.
        val telefon = llm
        val telefonHazir = !hizli && telefon != null && telefon.ready
        val telefonaVerildi = java.util.concurrent.atomic.AtomicBoolean(false)
        fun telefonaVer() {
            if (telefonHazir && telefonaVerildi.compareAndSet(false, true) && translatingFor == source) {
                log("TELEFON YAPAY ZEKÂSI DA BAŞLADI (internetle birlikte)")
                refineOnDevice(telefon!!, source)
            }
        }
        // 9.98 — TELEFON GOOGLE'I BEKLEMEZ, İLK ANDA BİRLİKTE BAŞLAR.
        // 12 Kasım 9.97 kaydında telefon 1,8 sn bekledikten sonra başlıyordu;
        // doğru çeviriler ("Bu çok boktan!", "Brian eskiden bayağı bir
        // yakışıklıydı.") tam bu 1,8 sn kadar geç kalıp ekrana çıkamadı.
        // Artık ikisi aynı anda çalışır; önce gelen görünür. İnternet önce
        // gelirse telefonun sonucu ekrandaki çevirinin üstüne YAZILMAZ.
        if (mode != Prefs.MODE_HYBRID) telefonaVer()
        // 10.3: DENGELİ'de taslak ANINDA. 13 Kasım 10.2 kaydında kısa, art arda
        // gelen satırlar ("Well.", "I don't mind.", "Watch out!") hiç görünmedi:
        // taslak 1,5 sn bekletiliyordu, doğru çeviri 0,8 sn'de gelene kadar
        // satır değişmişti. Artık Dengeli'de taslak hemen çıkar, doğru çeviri
        // gelince yerine geçer. "Doğru önce" biçiminde bekleme sürer.
        // 10.5: 10.2'nin temiz akışına dönüş. Taslağı her satırda hemen göstermek
        // (10.3) ekranda önce makine çevirisi, sonra düzeltme oynatıyordu.
        // Artık yalnız KISA satırlarda (en çok 3 kelime: "Well.", "Watch out!",
        // "I don't mind.") taslak hemen çıkar; bunlar ekranda çok kısa kalır ve
        // taslakları zaten doğrudur. Uzun satırlar 10.2'deki gibi doğru çeviriyi
        // 1,5 sn bekler.
        val kisaSatir = source.split(Regex("\\s+")).count { it.any(Char::isLetter) } <= 3
        val taslakBekleme = if (!prefs.yalnizDogru && kisaSatir) 0L else YEDEK_INTERNET_MS
        val gecikmeYedegi = if (mode != Prefs.MODE_HYBRID) scope.launch {
            delay(taslakBekleme)
            if (translatingFor == source && shownSource != source) {
                val q = fastTranslateKeepingName(source)
                if (!q.isNullOrBlank() && translatingFor == source && shownSource != source) {
                    publishForRun(runId, source, q, remember = false, tag = " · taslak (gecikme yedeği)")
                }
            }
            telefonaVer()
        } else null
        val internetSonucu = kotlinx.coroutines.CompletableDeferred<Boolean>()
        internetBekleniyor[source] = internetSonucu
        val better = try { engine.translate(source) } catch (e: Throwable) {
            internetSonucu.complete(false); internetBekleniyor.remove(source, internetSonucu); throw e
        }
        gecikmeYedegi?.cancel()
        if (!better.isNullOrBlank()) {
            // API MODU DA AYNI KAPIDAN GEÇER.
            //
            // 11 Kasım taraması: bu yol hiçbir denetimden geçmiyordu. Ad
            // koruma, seslenme onarımı ve AiGuard'ın tamamı (küfür düştü,
            // küfür eklendi, ad bozuldu, içerik düştü, sayı düştü) atlanıyor,
            // üstelik denetimsiz çıktı hafızaya da yazılıyordu. Çevrim dışı
            // modda aynı denetimler yıllardır var; iki mod ayrışmıştı.
            //
            // Taslak yok (API yolunda henüz üretilmedi), o yüzden redSebebi
            // taslak=null ile gevşek çalışıyor: yalnız ölçülebilir bozulmalar
            // elenir, kusurlu çeviri boş ekrandan iyidir.
            val duzgun = yzDuzelt(source, better)
            val sebep = AiGuard.redSebebi(source, null, duzgun)
            if (sebep == null) {
                engine.remember(source, duzgun)
                internetGeldi.add(source)
                internetSonucu.complete(true)
                internetBekleniyor.remove(source, internetSonucu)
                publishForRun(runId, source, duzgun, tag = " · YZ internet")
                return
            }
            log("YZ REDDEDİLDİ ($sebep) · hızlı çeviriye düşülüyor: ${duzgun.take(50)}")
        }

        // Yapay zekâ modunda sessizce kaba çeviriye düşmüyoruz; yanlış çeviri
        // göstermektense sebebini yazmak daha dürüst.
        // Oyun sırasında teknik hata göstermiyoruz: sessizce telefon çevirisine
        // düşüyoruz. Sebep yine kayda yazılıyor, ayarlardan görülebiliyor.
        internetSonucu.complete(false)
        internetBekleniyor.remove(source, internetSonucu)
        val err = engine.lastError
        log("YAPAY ZEKÂ BAŞARISIZ: ${err ?: "boş yanıt"}")

        if (mode != Prefs.MODE_HYBRID) {
            // Ekranda bu satır için bir şey yoksa önce taslak; sonra telefon.
            // Telefonun cevabı zaten geldiyse taslak onun üstüne YAZILMAZ.
            if (shownSource != source) {
                val quick = fastTranslateKeepingName(source)
                if (!quick.isNullOrBlank() && shownSource != source) {
                    publishForRun(runId, source, quick, remember = !telefonHazir, tag = " · taslak")
                }
            }
            telefonaVer()
            if (shownSource == source) return
        }
        if (prefs.diagnostics) publishForRun(runId, source, "⚠ ${err ?: "çeviri boş döndü"}")
    }

    // ----------------------------------------------------- menü / görev yazısı

    /**
     * Menü, görev ve tuş yazıları: her biri kendi yerinde, telefonun içindeki
     * çeviriyle anında çevrilir. Aynı yazı ikinci kez çevrilmez, hafızada tutulur.
     */
    private suspend fun processMenu(full: Bitmap) {
        val scale = min(1f, MENU_WIDTH.toFloat() / max(full.width, 1))
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                full,
                max(2, (full.width * scale).toInt()),
                max(2, (full.height * scale).toInt()),
                true
            )
        } else {
            full
        }

        val found = ArrayList<Triple<String, Int, Int>>()
        val subtitleTop = prefs.topPct.coerceIn(0, 98) / 100f

        try {
            val lines = ocrLines(small)
            for (line in lines) {
                val box = line.second ?: continue
                val raw = line.first.trim()
                if (raw.length < 3) continue
                if (isControllerLabel(raw)) continue
                if (raw.count { it.isLetter() } < 3) continue

                val relY = box.top.toFloat() / small.height
                if (relY >= min(subtitleTop, MENU_ZONE)) continue   // altyazı bölgesi ayrı işleniyor
                if (isOurOwnText(raw)) continue                      // kendi çevirimizi çevirmeyelim
                if (displayed.contains(normalize(raw))) continue  // kendi yazdığımız çeviri

                val screenX = (box.left.toFloat() / small.width * screenWidth).toInt()
                val screenY = (box.bottom.toFloat() / small.height * screenHeight).toInt()
                found.add(Triple(raw, screenX, screenY))
                if (found.size >= MAX_LABELS) break
            }
        } finally {
            if (small != full) small.recycle()
        }

        val key = found.joinToString("|") { it.first }
        if (key == lastMenuKey) return
        lastMenuKey = key

        if (found.isEmpty()) {
            displayed.clear()
            // EKRANDAKİ ÇEVİRİ (Türkçe) eklenir, İNGİLİZCE KAYNAK değil.
            // Burada eskiden shownSource vardı; "displayed" listesi "bu yazıyı
            // biz çizdik, yeniden okuyup çevirme" demek. İngilizce kaynağı
            // koyunca oyunun KENDİ altyazısı kendi yazımız sanılıp atlanıyordu:
            //   ÇEVRİLMEDİ (kendi yazımız): I've always been there for you.
            shownValue.takeIf { it.isNotEmpty() }?.let { displayed.add(normalize(it)) }
            overlay.clearLabels()
            return
        }

        val labels = ArrayList<Overlay.Label>()
        for ((text, x, y) in found) {
            val translated = translateMenuText(text) ?: continue
            labels.add(Overlay.Label(translated, x, y))
            displayed.add(normalize(translated))
        }
        overlay.setLabels(labels)
    }

    private suspend fun translateMenuText(text: String): String? {
        menuCache[text]?.let { return it }
        val (name, body) = splitSpeaker(text)
        val translated = fast?.translate(body) ?: return null
        if (translated.isBlank()) return null
        val result = if (name == null) translated else "$name: $translated"
        if (menuCache.size > 300) {
            val oldest = synchronized(menuCache) { menuCache.keys.firstOrNull() }
            if (oldest != null) menuCache.remove(oldest)
        }
        menuCache[text] = result
        return result
    }

    // --------------------------------------------------------------------- OCR

    private suspend fun ocrLines(bitmap: Bitmap): List<Pair<String, android.graphics.Rect?>> =
        suspendCancellableCoroutine { cont ->
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val out = ArrayList<Pair<String, android.graphics.Rect?>>()
                        for (block in result.textBlocks) {
                            for (line in block.lines) {
                                val t = line.text.trim()
                                if (t.isNotEmpty()) out.add(t to line.boundingBox)
                            }
                        }
                        if (cont.isActive) cont.resume(out)
                    }
                    .addOnFailureListener { error ->
                        diagOcrErrors++
                        if (prefs.diagnostics) log("OCR HATA: ${error.javaClass.simpleName}")
                        if (cont.isActive) cont.resume(emptyList())
                    }
            } catch (error: Throwable) {
                diagOcrErrors++
                if (prefs.diagnostics) log("OCR HATA: ${error.javaClass.simpleName}")
                if (cont.isActive) cont.resume(emptyList())
            }
        }

    private suspend fun runOcr(bitmap: Bitmap, filterEdges: Boolean): String {
        val lines = ocrLines(bitmap)
        val width = bitmap.width.toFloat()
        val kept = ArrayList<SubtitleBlocks.Line>()
        // Şerit koordinatı -> ekran pikseli (yalnızca ana şerit okumasında anlamlı).
        fun ekranY(y: Int): Int = ((ocrBandTop + y / ocrBandScale) * ocrScreenPerCap).toInt()
        val altta = filterEdges && prefs.subtitlePos == Prefs.POS_BELOW
        lastMlKitRaw = lines.joinToString(" / ") { it.first }.take(240)
        var rejectedOwn = 0
        var rejectedHud = 0
        var rejectedNoise = 0
        var rejectedEdge = 0

        for ((text, box) in lines) {
            // Never discard every line below the Turkish overlay's TOP edge.
            // The overlay is placed outside the captured English band instead;
            // only actual own-text/HUD/noise lines are filtered here.
            if (isControllerLabel(text)) { rejectedHud++; continue }
            if (isOurOwnText(text)) { rejectedOwn++; continue }
            if (OfflineFix.isNoiseLine(text)) { rejectedNoise++; continue }
            if (filterEdges && box != null) {
                val centerX = (box.left + box.right) / 2f / width
                val lineWidth = box.width() / width
                // Altyazı daima ortalıdır. Ortadan belirgin kaymış satırlar
                // görev yazısı, tuş etiketi veya kenar arayüzüdür.
                if (kotlin.math.abs(centerX - 0.5f) > 0.30f) { rejectedEdge++; continue }
                if (lineWidth < 0.18f && kotlin.math.abs(centerX - 0.5f) > 0.20f) { rejectedEdge++; continue }
            }
            val top = box?.top ?: 0
            kept.add(SubtitleBlocks.Line(top, box?.bottom ?: (top + 1), text))
        }

        lastFilteredOcr = kept.joinToString(" / ") { it.text }.take(240)
        val blok = SubtitleBlocks.bottomBlock(kept)
        // Distinguish: ML Kit saw no text vs. app filtered the recognized lines.
        lastOcrTrace = "ham=${lines.size} kalan=${kept.size} secilen=${blok.size} " +
            "kendi=$rejectedOwn hud=$rejectedHud gurultu=$rejectedNoise kenar=$rejectedEdge"
        if (altta && blok.isNotEmpty()) {
            textBottomScreenPx = ekranY(blok.maxOf { it.bottom })
            textBottomAt = android.os.SystemClock.elapsedRealtime()
        }
        return blok.joinToString(" ") { it.text }
    }

    /**
     * Şeritte birden fazla yazı bloğu olabilir: gerçek replik ve onun
     * üstündeki X-Ray oyuncu listesi gibi. Hepsini birleştirmek cümleleri
     * iç içe geçiriyordu ("There are an awful lot Hamish Linklater - Bruce
     * Wayne of unanswered questions").
     *
     * Altyazı satırları birbirine yakın durur, diğer bloklar arada belirgin
     * boşluk bırakır. Bu yüzden aşağıdan yukarı yürüyüp ilk büyük boşlukta
     * duruyoruz: geriye yalnızca en alttaki blok kalıyor.
     */
    private fun enAlttakiKume(
        satirlar: List<Pair<Int, String>>,
        seritYuksekligi: Int,
    ): List<Pair<Int, String>> {
        if (satirlar.size <= 1) return satirlar

        // İki satır arası bu kadar açılırsa ayrı blok sayılır.
        val boslukEsigi = (seritYuksekligi * 0.14f).toInt().coerceAtLeast(12)

        val sonuc = ArrayList<Pair<Int, String>>()
        var oncekiUst = satirlar.last().first
        for (i in satirlar.indices.reversed()) {
            val satir = satirlar[i]
            if (sonuc.isNotEmpty() && oncekiUst - satir.first > boslukEsigi) break
            sonuc.add(satir)
            oncekiUst = satir.first
            // Altyazı en fazla üç satırdır; fazlası başka bir şeydir.
            if (sonuc.size >= 3) break
        }
        return sonuc.reversed()
    }

    // ------------------------------------------------------------- yardımcılar

    /**
     * Şeridin kaba bir parmak izini çıkarıp öncekiyle karşılaştırır.
     * Altyazı ekranda dururken aynı kareyi tekrar tekrar okumak telefonu
     * yoruyor ve oyun akışını bozuyordu; değişmeyen kare artık atlanıyor.
     */
    private fun bandDegismedi(band: Bitmap): Boolean {
        if (band.isRecycled || band.width < 4 || band.height < 4) return false

        // 24x8'den biraz daha sık örnekleme; gereksiz OCR/CPU yükü oluşturmadan
        // kısa yazı değişikliklerinin yakalanma ihtimalini artırıyoruz.
        val sutun = 32
        val satir = 12
        val toplam = sutun * satir

        var piksel = imzaPiksel
        if (piksel == null || piksel.size != toplam) {
            piksel = IntArray(toplam)
            imzaPiksel = piksel
        }

        var i = 0
        for (y in 0 until satir) {
            val py = (band.height - 1) * y / (satir - 1)
            for (x in 0 until sutun) {
                val px = (band.width - 1) * x / (sutun - 1)
                val renk = band.getPixel(px, py)
                piksel[i++] =
                    ((renk shr 16 and 0xFF) + (renk shr 8 and 0xFF) + (renk and 0xFF)) / 3
            }
        }

        val onceki = bandImza
        if (onceki == null || onceki.size != toplam) {
            bandImza = piksel.copyOf()
            return false
        }

        var enBuyukFark = 0
        for (j in 0 until toplam) {
            val fark = kotlin.math.abs(piksel[j] - onceki[j])
            if (fark > enBuyukFark) enBuyukFark = fark
        }

        return if (enBuyukFark < 7) {
            true
        } else {
            System.arraycopy(piksel, 0, onceki, 0, toplam)
            false
        }
    }

    /** English subtitles with a name such as "Ben" must not be treated as
     *  our Turkish overlay on the basis of a single shared word. */
    private fun zatenTurkce(text: String): Boolean = OwnTextLanguage299.looksTurkish(text)

    /** Ekranda bizim yazdığımız Türkçe metin mi? Öyleyse tekrar çevrilmemeli. */
    private fun isOurOwnText(text: String): Boolean {
        if (zatenTurkce(text)) return true
        val simdi = normalize(text)
        if (simdi.isEmpty()) return false
        if (displayed.contains(simdi)) return true
        // Kendi yazımız yeniden okunup eskisinin üstüne eklenince metin her
        // turda büyüyerek geri geliyor: "bcventr" -> "bcventrlbcventr" -> ...
        // Tam eşleşme bunu yakalayamaz; parça eşleşmesi döngüyü kırar.
        for (eski in displayed) {
            if (eski.length >= 6 && (simdi.contains(eski) || eski.contains(simdi))) {
                return true
            }
        }
        return false
    }

    /** Ayarlardaki görünüm tercihlerini çizim katmanının anlayacağı hâle getirir. */
    /** Ayarlardaki görünüm tercihlerini çizim katmanının anlayacağı hâle getirir. */
    private fun gorunumStili(): Overlay.Stil {
        val alfa = SubtitleStyle.arkaAlfa(prefs.subtitleBg)
        val tv = prefs.tvMode
        return Overlay.Stil(
            // TV: 2-3 metreden okunsun diye ~1,5 kat büyük (en az 26).
            boyutSp = if (tv) maxOf(prefs.subtitleSize * 1.5f, 26f) else prefs.subtitleSize.toFloat(),
            yaziRengi = SubtitleStyle.yaziRengi(prefs.subtitleColor),
            // TV: parlak sahnelerde kaybolmasın diye arka plan koyu.
            // İngilizceyi örten konumda kutu tamamen koyu olsun; yoksa arkadaki yazı içinden görünüyor.
            arkaAlfa = when {
                prefs.subtitlePos == Prefs.POS_COVER && prefs.subtitleBg != Prefs.BG_NONE -> 255
                tv -> maxOf(alfa, 215)
                else -> alfa
            },
            arkaRenk = SubtitleStyle.arkaRenk(prefs.subtitleBgColor),
            // Arka plan yokken kenarlık okunurluk için şart.
            kenarlik = tv || prefs.subtitleOutline || alfa == 0,
            kenarPayYuzde = if (tv) 6 else 0,
        )
    }

    /**
     * Bu satır altyazı değil de ekrandaki başka bir yazı mı?
     *
     * İki şeyi yakalar:
     *  1. Bilinen arayüz/uyarı yazıları (güvenlik uyarısı, "Dialogue Boost"…).
     *  2. Oyuncu kadrosu listesi. Bunlar baş harfleri büyük, peş peşe dizilmiş
     *     özel adlardan oluşur ve içinde cümle kuran kelime geçmez.
     */
    private fun altyaziDegil(text: String): Boolean {
        val kucuk = text.lowercase()
        for (kalip in BLOCK_PHRASES) {
            if (kucuk.contains(kalip)) return true
        }

        val kelimeler = text.split(Regex("\\s+")).filter { it.isNotBlank() }

        // Dil göstergesi ("EN • TR") ve benzeri rozetler altyazı değil.
        val sikisik = kucuk.filter { !it.isWhitespace() }
        if (sikisik.contains("en•tr") || sikisik.contains("tr•en")) return true

        // Telefonun kendi arayüzü: indirme bildirimi, dosya adı, boyut.
        // ("altyazi-apk.zip ZIP 51 MB" gibi satırlar kayda düşmüştü.)
        if (Regex("\\.(zip|apk|rar|pdf|mp4|png|jpg)\\b").containsMatchIn(kucuk)) return true
        if (Regex("\\d+[.,]?\\d*\\s?(mb|kb|gb)\\b").containsMatchIn(kucuk)) return true
        // Durum çubuğu: sayı + kısaltılmış birim ("4,2 Mn", "23 sa.", "12 dk").
        // Diyalogda böyle iki ölçü yan yana gelmez.
        if (Regex("\\d+[.,]?\\d*\\s?(mn|sa|dk|sn|hr|min)\\b").findAll(kucuk).count() >= 2) return true

        // Anahtar, karma ya da adres parçası: harf ve rakam karışık, uzun.
        // Diyalogda böyle bir kelime geçmez.
        for (k in text.split(Regex("\\s+"))) {
            if (k.length < 16) continue
            if (k.any { it.isDigit() } && k.any { it.isLetter() }) return true
        }

        // Parçalanmış okuma: "f h b C V EN• TR" gibi, çoğu tek-iki harflik
        // kırıntıdan oluşan satırlar. Gerçek bir replik böyle görünmez.
        //
        // GERÇEK KELİME KIRINTI SAYILMAZ. Eskiden yalnızca uzunluğa bakılıyordu
        // ve İngilizcenin kendi kısa kelimeleri satırı çöp yaptırıyordu.
        // Kullanıcının 11 Kasım kaydından iki gerçek replik bu yüzden düştü:
        //   "Put it on speakerphone."        -> it, on   = 4 kelimenin 2'si
        //   "It is what it is, my friend."   -> It, is, it, my = 7 kelimenin 4'ü
        // İkisi de "ÇEVRİLMEDİ (altyazı değil)" diye kayda geçip ekrana hiç
        // gelmedi. OCR çöpünün ayırt edici yanı kısalık değil, kelimelerin
        // GERÇEK OLMAMASI: "f h b C V" kelime değil, "it is on my" kelimedir.
        if (kelimeler.size >= 3) {
            val kirinti = kelimeler.count { k ->
                val sade = k.lowercase().trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…')
                sade.length <= 2 && sade !in KISA_KELIMELER
            }
            // Mutlak alt sınır da var: 3 kelimelik bir satır iki kırıntıyla
            // çöpe gitmesin. Gerçek parçalanmış okumalarda kırıntı bol olur.
            if (kirinti >= 3 && kirinti * 2 >= kelimeler.size) return true
        }

        if (kelimeler.size < 5) return false

        // Cümle kuran bir kelime varsa bu bir repliktir, kadro listesi değil.
        for (k in kelimeler) {
            val sade = k.lowercase().trim('.', ',', '!', '?', ':', ';', '"', '\'')
            if (sade in SENTENCE_WORDS) return false
        }

        // Cümle noktalaması bolsa yine repliktir.
        if (text.count { it == '.' || it == '!' || it == '?' } >= 2) return false

        // Baş harfi büyük, gerisi küçük: "Hamish", "Linklater", "Bonilla"...
        val ozelAd = kelimeler.count { k ->
            k.length > 1 && k[0].isUpperCase() &&
                k.drop(1).all { it.isLowerCase() || it == '.' || it == '-' }
        }
        return ozelAd >= 5
    }

    private fun isControllerLabel(text: String): Boolean {
        val t = text.trim().trim('.', ',', ':', ';')
        if (t.uppercase() in UI_WORDS) return true
        if (t.length > 6) return false
        if (t.uppercase() in CONTROLLER_LABELS) return true
        if (t.all { it.isDigit() || it == 'm' || it == 'M' || it == ' ' }) return true
        return false
    }

    /**
     * "JAMES: Merhaba" gibi konuşan adını ayırır. Ad çeviriye hiç girmez,
     * böylece Hope, Faith, Rose gibi isimler yanlışlıkla çevrilmez.
     */
    private fun splitSpeaker(text: String): Pair<String?, String> {
        val idx = text.indexOf(':')
        if (idx !in 1..24) return null to text
        val name = text.substring(0, idx).trim()
        val rest = text.substring(idx + 1).trim()
        if (rest.length < 2) return null to text
        if (name.any { !it.isLetter() && it != ' ' && it != '.' && it != '\'' && it != '-' }) {
            return null to text
        }
        return name to rest
    }

    private suspend fun fastTranslateKeepingName(source: String): String? {
        val (name, body) = splitSpeaker(source)
        // Sözlük + OCR düzeltme + argo/küfür kuralları + telefon çevirisi (OfflineFix.kt)
        val f = fast
        val ff = firefox
        val google = prefs.googleCeviri
        if (f == null && ff == null && !google) return null
        // 10.7: sıra Google Çeviri (anahtarsız) -> Firefox -> yerleşik motor.
        // 10.2: Firefox motoru bağlıysa parça ona gider; cevap vermezse
        // aynı parça yerleşik motora düşer.
        val preferModel = NaturalTranslationPolicy10427.modelFirst(
            prefs.ceviriBicimi == Prefs.BICIM_DENGELI,
            OfflineFix.gucluFn != null,
            body
        )
        // BEGIN_BENCH202
        val benchFallback = kotlinx.coroutines.currentCoroutineContext()[SubtitleEngineTrace202]
        // END_BENCH202
        val translated = OfflineFix.translate(body, preferModel = preferModel) { parca ->
            val yerTutucu = YER_TUTUCU.findAll(parca).map { it.value }.toSet()
            // Korunan adların yer tutucusu ("Qx1") çeviride kaybolduysa ad geri
            // konamaz: o parça sıradaki motora gider.
            // 10.10: Google'a ham cümle OfflineFix.gucluFn ile gidiyor; buraya
            // düşen parça Google'ın cevap vermediği ya da adı bozduğu parçadır.
            val gSonuc = if (google && OfflineFix.gucluFn == null) GoogleCeviri.cevir(parca) else null
            if (gSonuc != null && yerTutucu.all { gSonuc.contains(it) }) {
                gSonuc
            } else {
                // BEGIN_BENCH202
                val benchFFStart = android.os.SystemClock.elapsedRealtime()
                // END_BENCH202
                val ffSonuc = if (ff != null && ff.hazir) ff.cevir(parca) else null
                // BEGIN_BENCH202
                if (ff != null && ff.hazir) benchFallback?.result("Firefox-fallback",
                    android.os.SystemClock.elapsedRealtime() - benchFFStart, ffSonuc,
                    ffSonuc != null && yerTutucu.all { ffSonuc.contains(it) })
                // END_BENCH202
                if (ffSonuc != null && yerTutucu.all { ffSonuc.contains(it) }) ffSonuc else {
                    // BEGIN_BENCH202
                    val benchMlStart = android.os.SystemClock.elapsedRealtime()
                    // END_BENCH202
                    val local = f?.translate(parca)
                    // BEGIN_BENCH202
                    if (f != null) benchFallback?.result("MLKit-fallback",
                        android.os.SystemClock.elapsedRealtime() - benchMlStart, local, !local.isNullOrBlank())
                    // END_BENCH202
                    local
                }
            }
        } ?: return null
        if (translated.isBlank()) return null

        // Motorun çıktısındaki bilinen Türkçe hatalarını düzelt.
        // Seslenme kuralı taslakta da geçerli ("Yo" için sabit "Lan" yok).
        // General structural guard: when Google invents a question or leaves
        // obvious English text, ask the offline model for a second opinion.
        // Never replace a translation unless the alternative passes the same gate.
        // High-confidence polarity and imperative errors must not reach the
        // overlay. Try a DIFFERENT installed engine, never retry Google here.
        // Fixes are evaluated AFTER Turkish post-processing as that is the
        // actual string that would be displayed.
        fun render(candidate: String): String =
            GameplayLogRepair299.repairTranslation(
                body,
                DialogueDisambiguation1038.repair(
                    body, AiGuard.hitapDuzelt(body, NameFix.koru(body, TurkishFix.duzelt(candidate)))
                )
            )
        var duzgun = render(translated)
        val issue = TranslationQualityGate.check(body, duzgun)
        // 10.42.2: the old gate only handled two QUESTIONS. A statement may
        // lose a following statement ("I'll be back for dessert") or the
        // second speaker's sentence. Retry at most TWO independent clauses.
        // Run the retry only when there is observable structural evidence;
        // keep a one-sentence fluent original if the split still fails.
        if (ClauseRecovery1042.needsSecondAttempt(
                body, duzgun, issue == TranslationQualityGate.Problem.LOST_PROFANITY ||
                    issue == TranslationQualityGate.Problem.MISSING_DETAIL)) {
            val clauses = ClauseRecovery1042.clauses(body)
            if (clauses != null) {
                val separate = clauses.map { piece ->
                    // Exact source-verified dialogue always wins over a weaker
                    // machine answer, including profanity in a mixed subtitle.
                    fastTranslateKeepingName(piece)
                }
                if (separate.all { !it.isNullOrBlank() } &&
                    separate.indices.all { i ->
                        TranslationQualityGate.check(clauses[i], separate[i]!!) == null
                    }) {
                    val complete = separate.joinToString(" ") { it!! }
                    if (TranslationQualityGate.check(body, complete) == null) {
                        log("EKSİK CÜMLE ONARILDI: iki bağımsız replik")
                        return if (name == null) complete else "$name: $complete"
                    }
                }
                log("EKSİK CÜMLE: ikinci deneme doğrulanamadı")
            }
        }
        if (issue == TranslationQualityGate.Problem.MISSING_QUESTION) {
            // One MT answer dropped a question. Retry exactly two independent
            // questions; do not recursively re-run a one-question fragment.
            // This is a bounded repair, used only on a detected loss.
            val questions = body.trim().split(Regex("""(?<=\?)\s+(?=[\p{Lu}\[])"""))
            if (questions.size == 2 && questions.all { it.endsWith('?') }) {
                val separate = questions.map { fastTranslateKeepingName(it) }
                if (separate.all { !it.isNullOrBlank() } &&
                    separate.indices.all { i -> TranslationQualityGate.check(questions[i], separate[i]!!) == null }) {
                    val complete = separate.joinToString(" ") { it!! }
                    log("EKSİK SORU ONARILDI: iki ayrı çeviri")
                    return if (name == null) complete else "$name: $complete"
                }
                log("EKSİK SORU UYARISI: ikinci soru kurtarılamadı")
            }
        }
        if (TranslationQualityGate.mustReject(issue)) {
            log("ANLAM KORUMASI: $issue; farklı motor deneniyor")
            val ffAlternative = if (ff != null && ff.hazir) {
                try { ff.cevir(body) } catch (_: Exception) { null }
            } else null
            val localAlternative = if (ffAlternative == null ||
                TranslationQualityGate.mustReject(TranslationQualityGate.check(body, render(ffAlternative)))) {
                try { f?.translate(body) } catch (_: Exception) { null }
            } else null
            val rescue = listOfNotNull(ffAlternative, localAlternative)
                .map(::render)
                .firstOrNull { !TranslationQualityGate.mustReject(TranslationQualityGate.check(body, it)) &&
                    !it.trim().equals(body.trim(), ignoreCase = true) }
            if (rescue == null) {
                log("ANLAM KORUMASI: Güvenli çeviri yok, hatalı metin gösterilmiyor")
                return null
            }
            duzgun = rescue
        } else if (issue != null && issue != TranslationQualityGate.Problem.LOST_PROFANITY &&
            LiveEnginePolicy.secondOpinionForWarning(prefs.ceviriBicimi) &&
            ff != null && ff.hazir) {
            // Only a measured warning triggers a second opinion. E4B never
            // participates here; the benchmark showed it takes ~3.3 seconds.
            val alternative = try { ff.cevir(body) } catch (_: Exception) { null }
            // Compare exactly the strings the overlay would display. Calling
            // choose() on raw Firefox output could approve text that TurkishFix
            // subsequently changes, or reject an otherwise valid repair.
            duzgun = TranslationQualityGate.choose(body, duzgun, alternative?.let(::render))
        }
        if (name != null) OfflineFix.languageLabel(name)?.let { return "[$it] $duzgun" }
        return if (name == null) duzgun else "$name: $duzgun"
    }

    /**
     * 10.8: uzun kullanım ölçümü. Her TANI satırında bellek, pil ve ısı
     * yazılır; 30–60 dakikalık tek bir oturum kaydı kaynak tüketimini gösterir.
     */
    private fun kaynakDurumu(): String = try {
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val yerelMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val pil = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val seviye = pil?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val olcek = pil?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val isi = (pil?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val dakika = (System.currentTimeMillis() - oturumBasi) / 60_000
        "KAYNAK: ${dakika} dk · bellek ${javaMb} MB (+${yerelMb} MB yerel) · pil %" +
            (if (seviye >= 0 && olcek > 0) seviye * 100 / olcek else -1) + " · ısı ${isi}°C" +
            (if (prefs.opusMtEnabled) " · OPUS kabul=${opusAccepted.get()}, yedek=${opusRejected.get()}" else "") +
            (if (prefs.deepLEnabled) " · DeepL kabul=${deepLAccepted.get()}, yedek=${deepLRejected.get()}" else "")
    } catch (t: Throwable) {
        "KAYNAK: ölçülemedi (${t.javaClass.simpleName})"
    }

    private val oturumBasi = System.currentTimeMillis()

    private fun clean(raw: String): String {
        // OCR onarımı kaynakta: taslak, yapay zekâ, hafıza ve kayıt aynı düzeltilmiş metni görür.
        // Sözlüklü onarım EN BAŞTA: "Iwonder" -> "I wonder", "thal" -> "that",
        // "lvories" -> "ivories". Sözlük henüz yüklenmediyse metne dokunmaz.
        val collapsed = SubtitlePreparation.repair(SoundTags.repair(OcrTextCleaner.clean(TextFix.clean(
            KonusanOnEki.uygula(AdDefteri.acikEtiketKapat(EtiketOnar.onar(OcrOnar.onar(raw))))))))
        if (collapsed.length < 3) return ""
        val letters = collapsed.count { it.isLetter() }
        if (letters < 3 && !SubtitleReadGuard299.shortSpokenLine(collapsed)) return ""
        if (letters.toFloat() / collapsed.length < 0.45f) return ""
        // Tuş uyarısı altyazıya yapışmış olabilir:
        //   "Touchpad only -said the folks there hadmet...trouble."
        // Satırın tamamı uyarı değil, o yüzden süzgeç yakalamıyor; parçayı atıyoruz.
        val atilmis = HUD_PARCA.replace(collapsed, " ").replace(Regex("\\s{2,}"), " ")
            .trim().trimStart('-', '–', '—', ':', ',').trim()
        // Geriye doğru düzgün bir replik kaldıysa onunla devam et (özyineleme yok).
        val collapsed2 = if (atilmis.length >= 3) atilmis else collapsed
        // OCR köşeli parantezi yemiş olabilir: "suspenseful music continues".
        // Parantezi geri koyunca ses etiketi olarak doğru çevriliyor.
        if (SoundTags.parantezsizEtiket(collapsed2)) return "[$collapsed2]"
        if (arayuzYazisi(collapsed2)) return ""
        if (turkceYazi(collapsed2)) return ""
        // Sözlük geldiğinden beri ölçebiliyoruz: satırın yarısından fazlası
        // İngilizce değilse bu OCR çöpüdür, çevirmek yerine temiz okumayı bekle.
        if (OcrOnar.cokBozuk(collapsed2)) {
            // HER ZAMAN kayda yazılıyor, yalnız tanı modunda değil. Bu eleme
            // sessiz olduğu için "Yeah. Go inside and ask for Raymond, bruh."
            // satırının neden hiç çevrilmediği üç denemede anlaşılamadı:
            // kayıtta ne OKUNAN ne ÇEVRİLMEDİ satırı vardı.
            dustu(collapsed2, "çöp okuma sanıldı")
            return ""
        }
        // SÜSLÜ YAZI TİPİ ÇÖPÜ. Kelime oranı temiz çıkar, çünkü satırın
        // yarısı gerçekten doğru İngilizce: fragman süslü yazıyı da düz
        // altyazıyı da aynı karede basıyor, OCR ikisini birleştiriyor.
        // Satırı atmak yerine kırpıyoruz; çöpün ardındaki gerçek altyazı
        // kalıyor. Kullanıcının 11 Kasım 9.73 kaydından:
        //   "ch At be w As worthy o F DArrying che Drince who had to prove
        //    to the King of Far Far Away"   ->  ekrana "CH AT OLDUĞU KRAL
        //    KRALI OLDU" çıkıyordu.
        val susluKuyruk = if (OcrOnar.susluCop(collapsed2)) OcrOnar.temizKuyruk(collapsed2) else null
        // 9.87: kuyruktan önceki kelimeler de gerçek İngilizceyse bu süslü yazı
        // değil, ince yazıdan bozuk okunmuş GERÇEK satırdır; kırpma, atma.
        if (!GameplayLogRepair299.isGameplayInstruction(collapsed2) &&
            OcrOnar.susluCop(collapsed2) && !OcrOnar.onEkGercek(collapsed2, susluKuyruk)) {
            val kuyruk = susluKuyruk
            if (kuyruk != null) {
                log("SÜSLÜ YAZI KIRPILDI: \"${collapsed2.take(40)}\" -> \"${kuyruk.take(40)}\"")
                return kuyruk
            }
            dustu(collapsed2, "süslü yazı çöpü")
            return ""
        }
        // Çöp ön ek: "hegd ahtdd Surprise." -> "Surprise."
        OcrOnar.copOnEki(collapsed2)?.let { kuyruk ->
            log("ÇÖP ÖN EK KIRPILDI: \"${collapsed2.take(40)}\" -> \"${kuyruk.take(40)}\"")
            return kuyruk
        }
        return collapsed2
    }

    /**
     * Oyun arayüzü yazısı mı (tuş uyarısı, sayaç, bölge adı)?
     *
     * Kayıttan: "Sprint", "Osatint", "Gsprint" — üçü de koşma tuşu uyarısıydı,
     * altyazı sanılıp çevrildi ("Koş!", "Orta", "Sprint yap!") ve ekranda yanıp söndü.
     * Gerçek altyazı cümledir: ya birden çok kelimedir ya da noktalamayla biter.
     */
    // DİKKAT: sınıf gövdesinde "const val" olmaz (yalnız object/companion/üst düzeyde).
    private val TURKCE_HARF = "çğışöüÇĞİŞÖÜ"

    /**
     * Satır zaten Türkçe mi? Telefonun kendi arayüzü ("Osil I Seçenekler" —
     * kullanıcının 6 Kasım 22:53 kaydından) ya da Türkçe altyazı çevrilmemeli.
     *
     * OfflineFix.isAlreadyTurkish iki Türkçe harf arıyor; "Seçenekler"de tek
     * "ç" var, o yüzden kaçıyordu. Beş harften uzun tek bir kelimede Türkçe
     * harf olması da yeter: İngilizce altyazıda böyle bir kelime bulunmaz.
     */
    private fun turkceYazi(s: String): Boolean {
        if (OfflineFix.isAlreadyTurkish(s)) return true
        return s.split(Regex("[^\\p{L}]+"))
            .any { k -> k.length >= 5 && k.any { it in TURKCE_HARF } }
    }

    /** Altyazıya yapışabilen sabit oyun arayüzü parçaları. */
    private val HUD_PARCA = Regex("\\b(touchpad only|(?:press|hold|tap) \\S{1,3} to skip|skip intro)\\b", RegexOption.IGNORE_CASE)

    private val TUS_UYARISI = Regex(
        "(?i)\\b(press|hold|tap|push)\\s+(?:and\\s+hold\\s+)?(?:the\\s+)?" +
            "([a-z]|[lr][123]|△|○|□|✕|circle|square|triangle|cross|touchpad|options)\\b"
    )

    /** Tek başına duran büyük harf (düğme simgesi). I ve A hariç (zamir/artikel). */
    private val TEK_HARF_SIMGE = Regex("(?<![\\p{L}'])[B-HJ-Z](?![\\p{L}'])")

    private fun arayuzYazisi(metin: String): Boolean {
        val s = metin.trim()
        if (s.isEmpty()) return true
        // Noktalamayla biten satır replik sayılır.
        if (s.last() in ".!?…,:;\"'’") return false
        // Ses/sahne etiketi ("[kapı sesi]") altyazıdır.
        if (s.startsWith("[") || s.startsWith("(")) return false
        // "Press O to throw...", "Hold R2 to aim" türü tuş uyarıları.
        // Fiilden sonra TUŞ gibi bir şey aranıyor; "Press on!" gibi gerçek
        // replikler bozulmasın diye tek harf / L1-R3 / şekil adı şart.
        if (TUS_UYARISI.containsMatchIn(s)) return true
        // Harita/menü kontrol çubuğu: "O Move O Zoom ỏ Reset View A World Map O Back".
        // Tek başına duran büyük harfler (daire/düğme simgeleri, OCR'da O/G/A...)
        // iki veya daha çok geçiyorsa bu bir HUD çubuğudur, replik değil.
        // "I" ve "A" sayılmaz: zamir ve artikel olarak gerçek repliklerde geçer.
        if (TEK_HARF_SIMGE.findAll(s).count() >= 2) return true
        val kelimeler = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Tamamı büyük harfli gerçek kısa replikler HUD sanılıp kayboluyordu.
        // Geniş bir serbest geçiş değil: yalnız bilinen konuşma/emir ifadeleri.
        if (SubtitleReadGuard299.shortSpokenLine(s)) return false
        // Bir-iki kelime + noktalama yok -> tuş uyarısı / sayaç.
        // Kayıttan: "Sprint", "A Carry", "Touchpad only", "SARemedies full".
        // Gerçek kısa replikler noktalama taşıyor: "Run!", "No.", "Quiet!"
        if (kelimeler.size <= 2) return true
        // "HOLD X TO OPEN" türü: kısa, tamamı büyük harf, noktalama yok.
        val harfler = s.filter { it.isLetter() }
        if (kelimeler.size <= 4 && harfler.isNotEmpty() && harfler.all { it.isUpperCase() }) return true
        return false
    }

    // Türkçe harfler katlanır: kendi yazımız "artıyor" OCR'da "artiyor" okununca yine tanınsın.
    private fun normalize(s: String): String = s.lowercase()
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace('â', 'a').replace('î', 'i').replace('û', 'u')
        .filter { it.isLetterOrDigit() }

    private val konusanEtiketi = Regex("^\\p{Lu}[\\p{L}'.]*(?: \\p{Lu}[\\p{L}'.]*)?:\\s*")

    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return a == b
        // "Peter: It's so good for you." ile "It's so good for you." aynı replik: etiket bazen okunup
        // bazen okunmuyor; ikisi ayrı sayılınca aynı satır iki kez çevriliyor, sıra uzuyordu.
        val ka = konusanEtiketi.replace(a, "")
        val kb = konusanEtiketi.replace(b, "")
        if ((ka != a || kb != b) && ka.isNotBlank() && kb.isNotBlank() && benzerMetin(ka, kb)) return true
        // Konuşan adı İKİ NOKTA OLMADAN başa yapışıyor:
        //   "D-don't come any closer!"  /  "Old Man Gen D-don't come any closer!"
        // Aynı replik bir okumada adla bir okumada adsız geliyordu; ikisi ayrı
        // satır sanılıp üst üste çevriliyor, ekrandaki yazı sürekli değişiyordu.
        if (adEkliMi(a, b) || adEkliMi(b, a)) { adOgren(a, b); return true }
        if (ayniBasMi(a, b)) { adOgren(a, b); return true }
        return benzerMetin(a, b)
    }

    /** Oyunda görülen konuşan adları. Okumalardan öğreniliyor, en çok 20 tane tutuluyor. */
    private val konusanAdlari = LinkedHashSet<String>()

    private val AKSAN = Regex("\\p{Mn}+")

    /** OCR aksan artıklarını siler: "Žuo" -> "Zuo", "Tianmião" -> "Tianmiao". */
    private fun aksansiz(s: String): String =
        AKSAN.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "")

    /**
     * İki okuma AYNI replik çıktıysa, birinde olup diğerinde olmayan baştaki
     * büyük harfli kelime dizisi konuşan adıdır.
     *
     * Kullanıcının kaydında bu ad çeviriye giriyordu:
     *   "Old Man Gen I asked where she came from."
     *   -> "Yaşlı adam gen nereden geldiğini sordum."
     * Oysa kural belli: isimler çevrilmez, konuşan ayrı durur. Oyun adı iki
     * nokta olmadan yazdığı için ayrılamıyordu; artık okumalardan öğreniyoruz.
     */
    private fun adOgren(a: String, b: String) {
        val wa = a.trim().split(Regex("\\s+"))
        val wb = b.trim().split(Regex("\\s+"))
        val uzun = if (wa.size >= wb.size) wa else wb
        val kisa = if (wa.size >= wb.size) wb else wa
        val fark = uzun.size - kisa.size
        if (fark !in 1..4 || kisa.size < 2) return
        for (i in 0 until fark) {
            if (uzun[i].firstOrNull()?.isUpperCase() != true) return
            // Ad kelimesi harf dışında bir şey taşımamalı ("D-don't" ad değildir).
            if (uzun[i].any { !it.isLetter() && it != '.' && it != '\'' }) return
        }
        val kalanMetin = uzun.subList(fark, uzun.size).joinToString(" ")
        val kisaMetin = kisa.joinToString(" ")
        if (normalize(kalanMetin) != normalize(kisaMetin) && !ortakBas(kalanMetin, kisaMetin)) return
        // OCR aksan artığını temizle: "Žuo Shang" -> "Zuo Shang",
        // "Mu Tianmião" -> "Mu Tianmiao". Aksi hâlde temiz okuma geldiğinde
        // öğrenilen adla eşleşmiyor ve ad ayrılamıyordu.
        val ad = aksansiz(uzun.subList(0, fark).joinToString(" "))
        if (ad.length !in 2..32 || !GameplayLogRepair299.plausibleSpeakerName(ad)) return
        synchronized(konusanAdlari) {
            if (konusanAdlari.add(ad)) {
                log("KONUŞAN ADI ÖĞRENİLDİ: $ad")
                while (konusanAdlari.size > 20) {
                    konusanAdlari.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
                }
            }
        }
    }

    /**
     * Öğrenilmiş konuşan adı başta duruyorsa ayırır: ad çeviriye girmez.
     * Ayardan "konuşanın adını göster" kapalıysa ad tamamen atılır.
     */
    private fun adiAyir(text: String): String {
        if (text.isBlank() || text.contains(':')) return text
        val adlar = synchronized(konusanAdlari) { konusanAdlari.toList() }
        // Karşılaştırma aksansız: OCR aynı adı bir kez "Zuo", bir kez "Žuo" okuyor.
        val duz = aksansiz(text)
        for (ad in adlar) {
            if (text.length <= ad.length + 2) continue
            if (!duz.startsWith(ad, ignoreCase = true)) continue
            // "Adam" adını "Ad" ile, "Archer"ı "Arch" ile kesme.
            if (duz.getOrNull(ad.length)?.isLetterOrDigit() == true) continue
            val kalan = text.substring(ad.length).trimStart(' ', ':', '-', '.', ',')
            if (kalan.length < 3) continue
            return if (prefs.showSpeaker) "$ad: $kalan" else kalan
        }
        return text
    }

    /**
     * OCR aynı altyazıyı okuyor ama SON birkaç kelimeyi her karede başka türlü
     * bozuyor. Kullanıcının 6 Kasım 22:53 kaydında tek bir replik on iki kez,
     * her seferinde farklı kuyrukla okundu:
     *
     *   "...valley outpost... and.. tho camninthe NOOds nhh"
     *   "...valley outpost... and... the. cam pin the WOode mhh"
     *   "...valley outpost... and... the camn in the t M00d"
     *
     * Levenshtein eşiği bunu kaçırıyor: bozuk kuyruk satırın beşte biri kadar.
     * Ama BAŞ taraf birebir aynı. Uzun ve aynı bir başlangıç paylaşıyorlarsa
     * bu aynı repliktir.
     *
     * Konuşan adı da ("Old Man Gen") bir okumada var bir okumada yok; o yüzden
     * baştan 1-4 büyük harfli kelime atılmış hâller de denenir.
     */
    private fun ayniBasMi(a: String, b: String): Boolean {
        for (x in basAdaylari(a)) {
            for (y in basAdaylari(b)) {
                if (ortakBas(x, y)) return true
            }
        }
        return false
    }

    /** Metnin kendisi + baştan 1-4 büyük harfli kelime atılmış hâlleri. */
    private fun basAdaylari(s: String): List<String> {
        val out = ArrayList<String>(5)
        out.add(s)
        val w = s.trim().split(Regex("\\s+"))
        for (n in 1..4) {
            if (n >= w.size) break
            if (w[n - 1].firstOrNull()?.isUpperCase() != true) break
            out.add(w.subList(n, w.size).joinToString(" "))
        }
        return out
    }

    /** İki metin uzun ve birebir aynı bir başlangıç paylaşıyor mu? */
    private fun ortakBas(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        val kisa = minOf(na.length, nb.length)
        if (kisa < ORTAK_BAS_EN_AZ) return false
        var i = 0
        while (i < kisa && na[i] == nb[i]) i++
        if (i >= ORTAK_BAS_MUTLAK) return true
        return i >= ORTAK_BAS_EN_AZ && i >= kisa * ORTAK_BAS_ORAN
    }

    /**
     * [uzun], [kisa] repliğinin başına bir konuşan adı eklenmiş hâli mi?
     *
     * Eklenen baş kısmın EN FAZLA 4 kelime olması ve her kelimenin büyük harfle
     * başlaması şart ("Old Man Gen", "Blue Clad Young Master"). Böylece gerçekten
     * farklı iki replik ("It's too dangerous. I have to find her...") birleşmiyor:
     * oradaki fazla kelimelerden biri küçük harfle başlıyor.
     */
    private fun adEkliMi(kisa: String, uzun: String): Boolean {
        val nk = normalize(kisa)
        val nu = normalize(uzun)
        if (nk.length < 12 || nu.length <= nk.length) return false
        if (!nu.endsWith(nk)) return false
        val fazla = nu.length - nk.length
        if (fazla > nk.length) return false      // ad replikten uzun olamaz
        var birikim = 0
        var adet = 0
        for (w in uzun.trim().split(Regex("\\s+"))) {
            if (birikim >= fazla) break
            if (w.firstOrNull()?.isUpperCase() != true) return false
            birikim += normalize(w).length
            adet++
            if (adet > 4) return false
        }
        return adet in 1..4 && birikim == fazla
    }

    private fun benzerMetin(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (na.isEmpty() || nb.isEmpty()) return false
        val longer = if (na.length >= nb.length) na else nb
        val shorter = if (na.length >= nb.length) nb else na
        // Yarım okuma: kısa olan, uzun olanın tam başlangıcı.
        // "Problem is he only worried about" / "...about himself."
        if (shorter.length >= 20 && longer.startsWith(shorter)) return true
        if (shorter.length.toFloat() / longer.length < 0.8f) return false
        val mesafe = levenshtein(shorter, longer)
        // Kısa satırlarda oran acımasız: "nowloading" ile "novvloading" arasında
        // iki harf fark var ama oran %17 çıkıyor. 8 harften uzun satırlarda
        // iki harfe kadar farka izin veriyoruz.
        if (longer.length >= 8 && mesafe <= maxOf(2, (0.12f * longer.length).toInt())) return true
        return mesafe.toFloat() / longer.length <= 0.12f
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // --------------------------------------------------------------- kapanış

    private fun stopEverything() {
        running = false
        pipelineOn.set(false)
        try {
            scope.cancel()
        } catch (_: Throwable) {
        }
        subtitleGeneration.invalidate()
        releaseDisplay()
        try {
            projection?.stop()
        } catch (_: Throwable) {
        }
        projection = null
        fast?.close()
        fast = null
        firefox?.birak()
        firefox = null
        opusMt?.close()
        opusMt = null
        GoogleCeviri.trace = null
        OfflineFix.gucluFn = null
        llm?.close()
        llm = null
        ai = null
        store = null
        overlay.clearLabels()
        overlay.setSubtitle(null, 0)
        overlay.hide()
        try {
            recognizer.close()
        } catch (_: Throwable) {
        }
        recognizerClosed = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseDisplay()
        try {
            projection?.stop()
        } catch (_: Throwable) {
        }
        // Bekleyen yığın işi kaldırılmazsa servis kapandıktan sonra çalışıp
        // yok edilmiş overlay'e dokunabilir.
        yiginiBosalt()
        overlay.hide()
        super.onDestroy()
    }
}
