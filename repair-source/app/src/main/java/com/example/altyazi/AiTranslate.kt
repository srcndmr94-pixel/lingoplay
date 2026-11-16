package com.example.altyazi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Oyun adını ve son replikleri bağlam olarak verip Anthropic API'sinden
 * daha doğru, karakter tutarlı bir Türkçe çeviri ister.
 */
class AiTranslate(private val prefs: Prefs) {

    // Altyazı için geç gelen çeviri işe yaramaz: replik çoktan geçmiştir.
    // Uzun bekleme hem ekranı geciktiriyor hem üç çeviri kanalını kilitliyordu.
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Son çevrilen replikler; karakter ve konu tutarlılığı için gönderilir. */
    private val history = ArrayDeque<Pair<String, String>>()
    private val historyLock = Any()

    /** İstek hazırlanırken geçmişin eşzamanlı değiştirilmesini önleyen anlık kopya. */
    private fun historySnapshot(): List<Pair<String, String>> =
        synchronized(historyLock) { history.toList() }

    @Volatile
    var lastError: String? = null
        private set

    companion object Usage {
        const val FALLBACK_MODEL = "claude-3-5-haiku-latest"

        /** Bu oturumda harcanan jetonlar; ana ekranda gösterilir. */
        @Volatile var requests = 0
        @Volatile var inputTokens = 0
        @Volatile var outputTokens = 0
        @Volatile var cacheHits = 0

        /** Aynı repliği ikinci kez sunucuya göndermemek için. */
        private val cache = LinkedHashMap<String, String>()

        fun cached(text: String): String? = synchronized(cache) { cache[text] }

        fun remember(text: String, translation: String) = synchronized(cache) {
            if (cache.size > 400) cache.remove(cache.keys.first())
            cache[text] = translation
        }

        /**
         * Model ya da anahtar reddedilmişse yapay zekâyı HER REPLİKTE yeniden
         * denemek, her satıra boşa giden bir sunucu turu ekler — altyazı
         * geriden gelir. İki ardışık başarısızlıktan sonra bir süre hiç
         * denemiyoruz; hızlı çeviri anında devreye giriyor.
         */
        const val DEVRE_SURESI_MS = 60_000L

        @Volatile var ardArdaHata = 0
        @Volatile var ardArdaZamanAsimi = 0
        const val ZAMAN_ASIMI_DEVRE_MS = 30_000L
        // 10.1: 1200 -> 700 ms. 12 Kasım 9.99 kaydında asıl modelin cevapları
        // 0,7–1,1 sn, yedekten gelenler 1,2–1,8 sn'ydi: farkın tamamı bu
        // bekleme. Asıl model 0,7 sn'de dönmediyse büyük olasılıkla takıldı.
        const val YEDEK_ISTEK_MS = 700L
        const val YEDEK_ISTEK_SAKIN_MS = 1200L
        const val TAKILMA_PENCERESI_MS = 60_000L
        @Volatile var sonTakilmaMs = 0L
        const val ISTEK_UST_SINIR_MS = 5500L
        const val GUNLUK_KOTA = "Günlük ücretsiz kota doldu."
        val VARSAYILAN_YEDEK = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.5-flash-lite")
        // 10.6 — GÜNLÜK KOTASI BİTEN MODEL GÜN BOYU KENARA ALINIR.
        // Ücretsiz kota MODEL BAŞINADIR. 13 Kasım 10.5 kaydında asıl modelin
        // kotası bitti; her satır önce ona gidip "kota doldu" aldı, yedek
        // 0,7 sn sonra çalıştı (çeviri 1,3–1,6 sn), iki başarısızlıktan sonra
        // internet 60 sn tamamen durduruldu. Oysa diğer modellerin kotası
        // duruyordu. Kotası biten model Google'ın sıfırlama saatine
        // (Pasifik gece yarısı) kadar hiç denenmez; sıradaki model asıl olur.
        val tukenen = java.util.concurrent.ConcurrentHashMap<String, Long>()

        fun tukendi(model: String) {
            val pt = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            pt.add(java.util.Calendar.DAY_OF_YEAR, 1)
            pt.set(java.util.Calendar.HOUR_OF_DAY, 0)
            pt.set(java.util.Calendar.MINUTE, 0)
            pt.set(java.util.Calendar.SECOND, 0)
            pt.set(java.util.Calendar.MILLISECOND, 0)
            tukenen[model] = pt.timeInMillis
        }

        fun tukenmisMi(model: String): Boolean {
            val bitis = tukenen[model] ?: return false
            if (System.currentTimeMillis() >= bitis) { tukenen.remove(model); return false }
            return true
        }

        @Volatile var yedekIstek = 0
        @Volatile var yedekKazandi = 0
        @Volatile var devreSuresiMs = DEVRE_SURESI_MS
        @Volatile var devreKapali = false
        @Volatile var devreZamani = 0L

        const val DUSUNME_BUTCE = "bütçe-0"
        const val DUSUNME_SEVIYE = "seviye-minimal"
        const val DUSUNME_YOK = "ayarsız"

        /** Model adı -> o modelde 400 vermeyen düşünme ayarı. */
        val dusunmeAyari = java.util.concurrent.ConcurrentHashMap<String, String>()

        /** Devre neden kapandı? Kullanıcıya sebebi söyleyebilmek için. */
        @Volatile var devreSebebi: String? = null

        /** Ayarlar değişince ya da servis yeniden başlayınca çağrılır. */
        fun devreyiAc() {
            ardArdaHata = 0
            ardArdaZamanAsimi = 0
            devreKapali = false
            devreZamani = 0L
            devreSebebi = null
        }

        private fun basariliOldu() {
            ardArdaHata = 0
            ardArdaZamanAsimi = 0
            devreKapali = false
            devreSebebi = null
        }

        private fun basarisizOldu(sebep: String?) {
            // 9.92 — ZAMAN AŞIMI DEVREYİ KAPATMAZ.
            // 12 Kasım 9.91 kaydı: iki satır üst üste zaman aşımına uğrayınca
            // yapay zekâ BİR DAKİKA kapatıldı; o dakikadaki bütün satırlar
            // bozuk taslakla çıktı, oysa sunucu çalışıyordu (hemen sonraki
            // istekler 0,7–1,3 sn'de cevaplandı). Devre kesici anahtar, kota
            // ve model hataları içindir; tek tük geciken cevap için değil.
            if (sebep != null && (sebep.contains("timeout", ignoreCase = true) ||
                    sebep.contains("timed out", ignoreCase = true))
            ) {
                // 9.95 — AMA ÜST ÜSTE ZAMAN AŞIMI SUNUCUNUN CEVAP VERMEDİĞİ DEMEK.
                // 12 Kasım 9.94 kaydı: 35 isteğin 35'i 4,0 sn'de zaman aşımı,
                // biri "503 yoğun talep". Her satır boşuna 4 sn bekledi. Üç
                // zaman aşımından sonra internet 30 sn denenmez; satırlar
                // beklemeden telefondaki yapay zekâya gider.
                ardArdaZamanAsimi++
                if (ardArdaZamanAsimi >= 3) {
                    devreKapali = true
                    devreZamani = System.currentTimeMillis()
                    devreSuresiMs = ZAMAN_ASIMI_DEVRE_MS
                    devreSebebi = sebep
                    ardArdaZamanAsimi = 0
                }
                return
            }
            if (sebep != null && sebep.startsWith("503")) {
                // Sunucu "yoğun" diyorsa da aynı: kısa ara.
                devreKapali = true
                devreZamani = System.currentTimeMillis()
                devreSuresiMs = ZAMAN_ASIMI_DEVRE_MS
                devreSebebi = sebep
                return
            }
            ardArdaHata++
            devreSuresiMs = DEVRE_SURESI_MS
            if (ardArdaHata >= 2) {
                devreKapali = true
                devreZamani = System.currentTimeMillis()
                devreSebebi = sebep
            }
        }

        fun summary(): String =
            "Bu oturum: $requests istek · ${inputTokens} giriş · ${outputTokens} çıkış jetonu · " +
                "$cacheHits tekrar ücretsiz karşılandı · yedek model $yedekIstek istek, $yedekKazandi kurtarma"
    }

    fun remember(english: String, turkish: String) {
        if (english.isBlank() || turkish.isBlank()) return
        synchronized(historyLock) {
            history.addLast(english to turkish)
            while (history.size > 2) history.removeFirst()
        }
    }

    fun clearHistory() = synchronized(historyLock) { history.clear() }

    suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        val key = prefs.activeKey
        if (key.isBlank()) {
            lastError = "API anahtarı girilmemiş. aistudio.google.com/api-keys " +
                "adresinden alıp Gelişmiş bölümündeki kutuya yapıştırın."
            return@withContext null
        }

        Usage.cached(text)?.let {
            Usage.cacheHits++
            lastError = null
            return@withContext it
        }

        // Devre kapalıysa sunucuya hiç gitmiyoruz. Bekleme yok, anında
        // hızlı çeviriye düşülür. Süre dolunca bir kez daha deneriz.
        if (Usage.devreKapali) {
            if (System.currentTimeMillis() - Usage.devreZamani < Usage.devreSuresiMs) {
                // Sessizce dönmüyoruz: sebebi söylemezsek ekranda
                // "başarısız: bilinmeyen" yazıyor ve kimse ne olduğunu bilmiyor.
                lastError = "İnternet yapay zekâsı " + (Usage.devreSuresiMs / 1000) + " sn " +
                    "duraklatıldı. Sebep: " +
                    (Usage.devreSebebi ?: "bilinmiyor") +
                    " — bu sürede telefondaki yapay zekâ kullanılıyor."
                return@withContext null
            }
            Usage.devreKapali = false
        }

        val secilen =
            if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel else prefs.model
        // 10.6: seçilen modelin bugünkü kotası bittiyse kotası olan ilk yedek asıl olur.
        val activeModel = if (Usage.tukenmisMi(secilen)) (yedekModel(secilen, key) ?: secilen) else secilen
        if (Usage.tukenmisMi(activeModel)) {
            lastError = GUNLUK_KOTA + " Bütün ücretsiz modellerin bugünkü kotası bitti; telefondaki yapay zekâ kullanılıyor."
            return@withContext null
        }
        val (first, anaHata) = ikiliIste(text, activeModel, key)
        if (first != null) {
            Usage.basariliOldu()
            lastError = null
            return@withContext first
        }
        // Aşağıdaki "model bulunamadı" kararı yalnız ASIL modelin hatasına bakar.
        lastError = anaHata ?: lastError

        // Model adı kabul edilmediyse hesapta gerçekte hangi modeller varsa onu sor.
        val err = lastError ?: ""
        if (err.contains("404") || err.contains("not_found", ignoreCase = true) ||
            err.contains("Model bulunamadı")
        ) {
            // 9.89 — HATA SİLİNİYORDU, AYNI MODEL YENİDEN SEÇİLİYORDU.
            //
            // Kullanıcının 12 Kasım 9.88 kaydı (model: gemini-2.5-flash):
            //   YAPAY ZEKÂ BAŞARISIZ: boş yanıt
            //   ... Sebep: bilinmiyor
            // Sunucu "model bulunamadı" demiş, biz model listesini istemişiz;
            // liste başarıyla gelince fetchModels hatayı SIFIRLAMIŞ, pickBest
            // de listeden yine aynı modeli seçmiş. Sonuç: hiçbir şey denenmedi,
            // sebep de kayboldu. Google bir modeli listede gösterip isteği
            // reddedebiliyor (kapatılan modellerde olan bu).
            //
            // Artık: reddedilen model adaylardan ÇIKARILIYOR, sıradaki en çok
            // üç aday deneniyor, ilk çalışan kaydediliyor. Hiçbiri çalışmazsa
            // asıl hata ve denenenler kayda yazılıyor.
            val asilHata = lastError
            val adaylar = siraliAdaylar(fetchModels(key)).filter { it != activeModel }.take(3)
            for (aday in adaylar) {
                val second = request(text, aday, key)
                if (second != null) {
                    if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel = aday
                    else prefs.model = aday
                    Usage.basariliOldu()
                    return@withContext second
                }
            }
            lastError = "Model kullanılamıyor ($activeModel). Denenen: " +
                (if (adaylar.isEmpty()) "aday yok" else adaylar.joinToString(", ")) +
                " · son hata: " + (lastError ?: asilHata ?: "bilinmiyor")
        }

        // Sebep ASLA boş kalmasın: kayıtta "bilinmiyor" görünce hiçbir şey çözülemiyor.
        if (lastError == null) lastError = "sunucudan çeviri alınamadı · model=" + activeModel
        // 10.6: tek modelin günlük kotası bitti diye interneti durdurmuyoruz;
        // sıradaki satır kotası olan modelle gider.
        if ((anaHata ?: "").startsWith(GUNLUK_KOTA) && yedekModel(activeModel, key) != null) return@withContext null
        Usage.basarisizOldu(lastError)
        null
    }

    // ------------------------------------------------------------------
    // 9.99 — İKİNCİ MODELE YEDEK İSTEK (ücretsiz).
    //
    // Google cevap verdiğinde çeviri 0,7–1,5 sn'de ve doğru geliyor; ama 12
    // Kasım kayıtlarında isteklerin yaklaşık yarısı hiç dönmedi (4 sn zaman
    // aşımı, bazen "503 yoğun talep"). Ücretsiz katmanın istek sınırı model
    // başınadır. Asıl model YEDEK_ISTEK_MS içinde cevap vermezse aynı satır
    // İKİNCİ bir Gemini modeline de gönderilir; hangisi önce dönerse o
    // kullanılır. İstekler beklenmez, ayrı bir kapsamda koşar: geç dönen
    // cevap kimseyi bekletmez.
    // ------------------------------------------------------------------
    private val istekKapsami = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var yedekAday: String? = null
    @Volatile private var yedekListesi: List<String> = emptyList()
    @Volatile private var yedekArandi = false
    private val kotuYedek = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun yedekModel(ana: String, key: String): String? {
        if (prefs.provider != Prefs.PROVIDER_GEMINI) return null
        if (!yedekArandi) {
            yedekArandi = true
            istekKapsami.launch {
                val liste = try { siraliAdaylar(fetchModels(key)) } catch (_: Throwable) { emptyList() }
                yedekAday = liste.firstOrNull { it != ana && it !in kotuYedek }
                yedekListesi = liste
            }
        }
        return ((yedekAday?.let { listOf(it) } ?: emptyList()) + yedekListesi + VARSAYILAN_YEDEK)
            .firstOrNull { it != ana && it !in kotuYedek && !Usage.tukenmisMi(it) }
    }

    /** (sonuç, asıl modelin hatası). Sonuç null ise ikisi de başarısız oldu. */
    private suspend fun ikiliIste(text: String, ana: String, key: String): Pair<String?, String?> {
        val sonuc = CompletableDeferred<String?>()
        val biten = java.util.concurrent.atomic.AtomicInteger(0)
        val anaHata = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val yedek = yedekModel(ana, key)
        val kac = if (yedek != null) 2 else 1
        fun bitir(r: String?) {
            if (r != null) sonuc.complete(r)
            else if (biten.incrementAndGet() >= kac) sonuc.complete(null)
        }
        // 10.4: Yedek, asıl model son bir dakikada takıldıysa 0,7 sn'de,
        // sakin dönemde 1,2 sn'de gönderilir. Asıl modelin normal cevabı
        // 0,6–1,1 sn; 0,7 sn'lik sabit bekleme satırların yarısında yedeği de
        // çalıştırıp günlük kotayı iki kat hızlı bitiriyordu.
        val yedekBekle = if (System.currentTimeMillis() - Usage.sonTakilmaMs < TAKILMA_PENCERESI_MS)
            YEDEK_ISTEK_MS else YEDEK_ISTEK_SAKIN_MS
        // 10.6: asıl model hemen hata dönerse (kota, 503) yedek beklemeden çıkar.
        val anaDustu = CompletableDeferred<Unit>()
        istekKapsami.launch {
            val r = request(text, ana, key)
            if (r == null) {
                anaHata.set(lastError)
                Usage.sonTakilmaMs = System.currentTimeMillis()
                anaDustu.complete(Unit)
            }
            bitir(r)
        }
        if (yedek != null) {
            istekKapsami.launch {
                withTimeoutOrNull(yedekBekle) { anaDustu.await() }
                if (sonuc.isCompleted) { bitir(null); return@launch }
                Usage.yedekIstek++
                val r = request(text, yedek, key)
                if (r == null && (lastError ?: "").let { it.startsWith("404") || it.contains("Model bulunamadı") }) {
                    kotuYedek.add(yedek)
                }
                if (r != null) {
                    Usage.yedekKazandi++
                    Usage.sonTakilmaMs = System.currentTimeMillis()
                }
                bitir(r)
            }
        }
        val r = withTimeoutOrNull(ISTEK_UST_SINIR_MS) { sonuc.await() }
        return r to anaHata.get()
    }

    /** Hesabın gerçekten erişebildiği model adlarını getirir. */
    /**
     * Anahtar HTTP başlığına konulacağı için yalnızca ASCII olabilir.
     * Yanlış metin yapıştırıldığında uygulamanın çökmemesi gerekiyor.
     */
    /**
     * Google'ın belgelediği yöntem x-goog-api-key. Bazı anahtar türlerinde
     * bu reddedilirse (401) Bearer ile ikinci bir deneme yapılıyor.
     */
    private fun geminiBasliklari(key: String): List<Pair<String, String>> = listOf(
        "x-goog-api-key" to key,
        "Authorization" to "Bearer $key",
    )

    /** Tek bir başlıkla dener. Başarılıysa liste, değilse null döner. */
    private fun fetchGeminiModels(ad: String, deger: String): List<String>? {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .get()
            .addHeader(ad, deger)
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastError = shortError(resp.code, raw)
                    return null
                }
                val arr = JSONObject(raw).optJSONArray("models") ?: return null
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val yontemler = item.optJSONArray("supportedGenerationMethods")
                    var uygun = yontemler == null
                    if (yontemler != null) {
                        for (j in 0 until yontemler.length()) {
                            if (yontemler.optString(j) == "generateContent") uygun = true
                        }
                    }
                    if (!uygun) continue
                    val id = item.optString("name").removePrefix("models/")
                    if (id.isNotBlank()) out.add(id)
                }
                lastError = null
                out
            }
        } catch (e: Throwable) {
            lastError = e.message ?: "bağlantı hatası"
            null
        }
    }

    private fun safeKey(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            lastError = "API anahtarı girilmemiş. aistudio.google.com/api-keys " +
                "adresinden alıp Gelişmiş bölümündeki kutuya yapıştırın."
            return null
        }
        if (trimmed.any { it.code < 33 || it.code > 126 }) {
            lastError = "API anahtarı hatalı: içinde boşluk veya özel karakter var. " +
                "Kutuyu temizleyip anahtarı yeniden yapıştırın."
            return null
        }
        if (trimmed.length < 20) {
            lastError = "API anahtarı çok kısa, eksik yapıştırılmış olabilir"
            return null
        }
        return trimmed
    }

    fun fetchModels(key: String): List<String> {
        val clean = safeKey(key) ?: return emptyList()
        val gemini = prefs.provider == Prefs.PROVIDER_GEMINI
        if (gemini) {
            for ((ad, deger) in geminiBasliklari(clean)) {
                val sonuc = fetchGeminiModels(ad, deger)
                if (sonuc != null) return sonuc
                if (lastError?.startsWith("401") != true) return emptyList()
            }
            return emptyList()
        }
        val builder = Request.Builder()
            .url("https://api.anthropic.com/v1/models?limit=100")
            .get()
            .addHeader("x-api-key", clean)
            .addHeader("anthropic-version", "2023-06-01")
        val req = builder.build()

        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastError = shortError(resp.code, raw)
                    return emptyList()
                }
                val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) out.add(id)
                }
                lastError = null
                out
            }
        } catch (e: Throwable) {
            lastError = e.message ?: "bağlantı hatası"
            emptyList()
        }
    }

    /** Altyazı için en uygunu: en hızlı olan. Yoksa eldeki ilk model. */
    fun pickBest(models: List<String>): String? = siraliAdaylar(models).firstOrNull()

    /**
     * Modelleri altyazıya uygunluk sırasına dizer.
     *
     * Gemini'de yalnız KARARLI metin modelleri: "gemini-<sürüm>-flash-lite"
     * ve "gemini-<sürüm>-flash". Ses, görüntü, canlı, önizleme adları
     * (…-tts, …-live, …-image, …-preview) altyazı çevirmez ya da kısa sürede
     * kapatılır. En yeni sürüm başta: eski sürümler listede görünse bile
     * kapatılmış olabiliyor. Lite önce gelir, çünkü daha hızlı ve ücretsiz
     * kotası daha geniş.
     */
    fun siraliAdaylar(models: List<String>): List<String> {
        if (models.isEmpty()) return emptyList()
        if (prefs.provider != Prefs.PROVIDER_GEMINI) {
            val haiku = models.filter { it.contains("haiku", ignoreCase = true) }
            val sonnet = models.filter { it.contains("sonnet", ignoreCase = true) }
            return (haiku + sonnet + models).distinct()
        }
        fun surum(ad: String): Double =
            KARARLI_FLASH.matchEntire(ad)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val kararli = models.filter { KARARLI_FLASH.matches(it) && surum(it) >= 3.0 }
        val lite = kararli.filter { it.endsWith("-lite") }.sortedByDescending { surum(it) }
        val flash = kararli.filter { !it.endsWith("-lite") }.sortedByDescending { surum(it) }
        val eski = models.filter { KARARLI_FLASH.matches(it) && surum(it) < 3.0 }
            .sortedByDescending { surum(it) }
        val kalan = models.filter {
            it.startsWith("gemini") && it.contains("flash") && !ISE_YARAMAZ.containsMatchIn(it)
        }
        return (lite + flash + eski + kalan).distinct()
    }

    private val KARARLI_FLASH = Regex("^gemini-(\\d+(?:\\.\\d+)?)-flash(?:-lite)?$")
    private val ISE_YARAMAZ =
        Regex("tts|live|image|audio|embedding|thinking|transcribe|computer|robotics|omni|banana")

    private fun request(text: String, model: String, key: String): String? =
        if (prefs.provider == Prefs.PROVIDER_GEMINI) {
            requestGemini(text, if (model.startsWith("gemini")) model else prefs.geminiModel, key)
        } else {
            requestAnthropic(text, model, key)
        }

    /**
     * Google Gemini: ücretsiz katmanı olduğu için varsayılan sağlayıcı.
     * Aynı sistem talimatını ve son replik geçmişini kullanır.
     */
    private fun requestGemini(text: String, model: String, rawKey: String): String? {
        val key = safeKey(rawKey) ?: return null
        val contents = JSONArray()
        for ((en, tr) in historySnapshot()) {
            contents.put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", en)))
            )
            contents.put(
                JSONObject().put("role", "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", tr)))
            )
        }
        contents.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )

        // 9.88 — DÜŞÜNME AYARI MODELE GÖRE DEĞİŞİYOR.
        //
        // Kullanıcının 12 Kasım 9.87 kaydında internet modundaki HER istek
        //   "400: Request contains an invalid argument."
        // ile döndü; tek bir satır bile yapay zekâdan geçmedi. Sebep bu
        // gövdedeki sabit ayar: {"thinkingBudget": 0} yalnız Gemini 2.x
        // ailesinde geçerli. Gemini 3.x bunun yerine "thinkingLevel" istiyor
        // ve eski alanı geçersiz argüman sayıyor.
        //
        // Hangi modelin hangisini kabul ettiğini ada bakarak TAHMİN ediyoruz,
        // ama tahmine güvenmiyoruz: 400 gelirse sıradaki ayarı deniyoruz, en
        // sonda ayarı hiç göndermiyoruz. Tutan ayar model başına hatırlanıyor;
        // sonraki satırlar tek istekle gidiyor.
        val bilinen = Usage.dusunmeAyari[model]
        val sira = if (bilinen != null) listOf(bilinen) else dusunmeSirasi(model)
        for (ayar in sira) {
            val body = geminiGovde(contents, ayar)
            var sonuc: String? = null
            for ((ad, deger) in geminiBasliklari(key)) {
                sonuc = geminiIstek(text, model, ad, deger, body)
                if (sonuc != null) break
                if (lastError?.startsWith("401") != true) break
            }
            if (sonuc != null) {
                Usage.dusunmeAyari[model] = ayar
                return sonuc
            }
            // Yalnız "geçersiz argüman" başka ayarla düzelir; kota, anahtar,
            // bağlantı hatasında yeniden denemek boşa beklemektir.
            if (lastError?.startsWith("400") != true) return null
            lastError = lastError + " · model=" + model + " · düşünme=" + ayar
        }
        if (bilinen != null) Usage.dusunmeAyari.remove(model)
        return null
    }

    /** Denenecek düşünme ayarları, en olası olan başta. */
    private fun dusunmeSirasi(model: String): List<String> =
        if (model.startsWith("gemini-2") || model.startsWith("gemini-1")) {
            listOf(DUSUNME_BUTCE, DUSUNME_YOK, DUSUNME_SEVIYE)
        } else {
            listOf(DUSUNME_SEVIYE, DUSUNME_YOK, DUSUNME_BUTCE)
        }

    private fun geminiGovde(contents: JSONArray, ayar: String): okhttp3.RequestBody {
        val uretim = JSONObject()
        when (ayar) {
            // Düşünme kapalı: altyazı çevirisi kısa bir iş, düşünme hem cevabı
            // geciktiriyor hem de cevap sınırını tüketip boş yanıt döndürüyor.
            DUSUNME_BUTCE -> uretim.put("maxOutputTokens", 512)
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            DUSUNME_SEVIYE -> uretim.put("maxOutputTokens", 1024)
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
            // Ayar gönderilmiyor: model kendi varsayılanıyla düşünebilir, bu
            // yüzden cevap sınırı geniş; yoksa düşünme sınırı yer, cevap boş döner.
            else -> uretim.put("maxOutputTokens", 2048)
        }
        return JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt())))
            )
            .put("contents", contents)
            .put("generationConfig", uretim)
            .toString()
            .toRequestBody("application/json".toMediaType())
    }

    /** Tek bir başlıkla Gemini isteği. */
    private fun geminiIstek(
        text: String,
        model: String,
        ad: String,
        deger: String,
        body: okhttp3.RequestBody,
    ): String? {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader(ad, deger)
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        val basla = android.os.SystemClock.elapsedRealtime()
        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                Usage.requests++
                if (!resp.isSuccessful) {
                    val hata = shortError(resp.code, raw)
                    lastError = hata
                    if (hata.startsWith(GUNLUK_KOTA)) Usage.tukendi(model)
                    return null
                }
                val json = JSONObject(raw)
                val cand = json.optJSONArray("candidates")?.optJSONObject(0)
                val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
                val out = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        out.append(parts.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
                val result = out.toString().trim().trim('"')
                if (result.isBlank()) {
                    val finish = cand?.optString("finishReason").orEmpty().ifBlank { "?" }
                    lastError = "boş yanıt · model=$model · bitiş=$finish"
                    null
                } else {
                    lastError = null
                    json.optJSONObject("usageMetadata")?.let { u ->
                        Usage.inputTokens += u.optInt("promptTokenCount")
                        Usage.outputTokens += u.optInt("candidatesTokenCount")
                    }
                    Usage.remember(text, result)
                    result
                }
            }
        } catch (e: Throwable) {
            // 9.94: "timeout" tek başına bir şey söylemiyordu. Süre, model ve
            // hata türü yazılıyor: bağlanamadı mı, sunucu mu geç cevap verdi?
            val gecen = android.os.SystemClock.elapsedRealtime() - basla
            val tur = when (e) {
                is java.net.SocketTimeoutException ->
                    if ((e.message ?: "").contains("connect", ignoreCase = true)) "bağlanma zaman aşımı"
                    else "sunucu cevabı gecikti"
                is java.net.UnknownHostException -> "internet yok (adres bulunamadı)"
                is java.io.InterruptedIOException -> "istek kesildi"
                else -> e.javaClass.simpleName
            }
            lastError = "timeout · $tur · ${gecen} ms · model=$model" +
                (e.message?.let { if (it != "timeout") " · $it" else "" } ?: "")
            null
        }
    }

    /** Her iki sağlayıcıda da aynı talimat kullanılıyor; kısa tutuluyor. */
    private fun systemPrompt(): String {
        val tur = prefs.contentType
        val ad = prefs.gameName.ifBlank { "bilinmeyen bir ${tur.lowercase()}" }
        val sb = StringBuilder()
        sb.append(tur).append(" altyazısı çevirmeni. ").append(tur).append(": ").append(ad).append(". ")
        sb.append("OCR ile okunmuş İngilizce repliği doğal Türkçeye çevir. ")
        sb.append("Özel isimleri çevirme. OCR hatalarını bağlamdan, anlamı değiştirmeden düzelt; ")
        sb.append("bağlamda anlamsız kalan bir okumayı birebir çevirme (OCR 'alive'ı 'olive' okumuş olabilir). ")
        // Telefon tarafındaki talimatla aynı maddeler: ikisi ayrışırsa aynı
        // satır iki modda farklı çıkıyor ve hangisinin doğru olduğu belirsizleşiyor.
        sb.append("Kelime kelime değil, anlamıyla çevir: deyim ve argo için Türkçede gerçekten söylenen karşılığı kullan. ")
        sb.append("Çokluk Türkçede fiil ekinde taşınır: İngilizce birden fazla kişiye sesleniyorsa ")
        sb.append("(y'all, you guys) çoğul eki kullan, tekil fiille karıştırma. ")
        sb.append("'Yo' bağlama göre selamlama/dikkat çekmedir; otomatik 'Lan' deme. Gerekirse 'Hey', gerekmiyorsa hitabı atla. Diğer seslenmeler: Hey/Ay='Hey', Look='Bak', ")
        sb.append("Listen='Dinle', Like='Yani', Man='Ya', Well='Şey'. İngilizcesi saldırgan değilse ")
        sb.append("bunları 'Lan' ya da 'Ulan' diye çevirme. ")
        sb.append("'What the fuck?'='Bu ne lan?', 'What the fuck is going on?'='Ne oluyor lan?', ")
        sb.append("'What the fuck are you doing?'='Ne yapıyorsun lan?': 'the fuck' pekiştiricisini 'lan' ile ver, ")
        sb.append("'sikim' kelimesini kullanma. ")
        sb.append("Yalnız adlardan oluşan satır Türkçede de aynıdır: boş dönme, olduğu gibi ver. ")
        sb.append("Küfür, hakaret ve argoyu ASLA sansürleme, yumuşatma, atlama ya da yıldızlama: ")
        sb.append("aynı sertlikte, Türk dublajında kullanılan doğal karşılığıyla, cümledeki yerinde çevir. ")
        sb.append("Karşılığı bağlama göre seç (örn. 'shit' ünlemse 'siktir', eşya anlamındaysa 'eşyalar'; ")
        sb.append("'fucking' sıfatsa 'lanet olası', fiil önündeyse cümleye 'amına koyayım' gibi doğal biçimde katılır). ")
        if (prefs.showSpeaker) {
            sb.append("Replikte konuşan adı varsa 'İsim: çeviri' biçiminde koru. ")
        }
        sb.append("Altyazı tek bakışta okunacak, kısa ve akıcı olsun. ")
        sb.append("Sadece çeviriyi yaz, açıklama yazma, mutlaka bir çeviri ver.")
        return sb.toString()
    }

    private fun requestAnthropic(text: String, model: String, rawKey: String): String? {
        val key = safeKey(rawKey) ?: return null
        val messages = JSONArray()
        for ((en, tr) in historySnapshot()) {
            messages.put(JSONObject().put("role", "user").put("content", en))
            messages.put(JSONObject().put("role", "assistant").put("content", tr))
        }
        messages.put(JSONObject().put("role", "user").put("content", text))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 400)
            .put("system", systemPrompt())
            .put("messages", messages)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastError = shortError(resp.code, raw)
                    return null
                }
                val json = JSONObject(raw)
                json.optJSONObject("usage")?.let { u ->
                    Usage.requests++
                    Usage.inputTokens += u.optInt("input_tokens")
                    Usage.outputTokens += u.optInt("output_tokens")
                }
                val content = json.optJSONArray("content")
                val out = StringBuilder()
                val types = StringBuilder()
                if (content != null) {
                    for (i in 0 until content.length()) {
                        val part = content.optJSONObject(i) ?: continue
                        val type = part.optString("type")
                        if (types.isNotEmpty()) types.append(",")
                        types.append(type)
                        // Bazı modeller önce düşünme bloğu döndürür; metin bloklarını topluyoruz.
                        if (type == "text") out.append(part.optString("text"))
                    }
                }
                val result = out.toString().trim().trim('"')
                if (result.isBlank() || result == "-") {
                    // Boş dönüşün sebebini görebilmek için özetini saklıyoruz.
                    val stop = json.optString("stop_reason", "?")
                    lastError = "boş yanıt · model=$model · stop=$stop · blok=$types"
                    null
                } else {
                    lastError = null
                    Usage.remember(text, result)
                    result
                }
            }
        } catch (e: Throwable) {
            lastError = e.message ?: "bağlantı hatası"
            null
        }
    }

    /** Sunucu hatasını, ne yapılacağını söyleyen bir cümleye çevirir. */
    private fun anlasilirHata(code: Int, mesaj: String): String = when (code) {
        401 -> "Anahtar geçersiz veya iptal edilmiş. " +
            "aistudio.google.com/api-keys adresinden yeni anahtar alıp yapıştırın."
        // 9.90: 429 her zaman GÜNLÜK kota değil. Ücretsiz anahtarda dakikalık
        // istek sınırı da 429 döner ve bir dakika sonra kendiliğinden açılır.
        // 12 Kasım 9.89 kaydında dört istekten sonra "günlük kota doldu" yazdı;
        // hangisi olduğunu sunucunun iletisinden ayırıyoruz.
        429 -> if (mesaj.contains("PerDay", ignoreCase = true) ||
            mesaj.contains("per day", ignoreCase = true) ||
            mesaj.contains("daily", ignoreCase = true)
        ) {
            GUNLUK_KOTA + " Yarın yenilenir; bu sürede kotası olan diğer ücretsiz model kullanılır."
        } else {
            "İstek sınırı aşıldı (429): ücretsiz anahtar dakikada sınırlı sayıda istek kabul " +
                "ediyor. Bir dakika sonra yeniden denenir. Sunucu: " + mesaj.take(90)
        }
        404 -> "Model bulunamadı. MODELLERİ BUL tuşuna basıp listeden seçin."
        403 -> "Bu anahtarın Gemini yetkisi yok. AI Studio'dan yeni anahtar alın."
        else -> "$code: ${mesaj.take(120)}"
    }

    private fun shortError(code: Int, raw: String): String {
        val msg = try {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Throwable) {
            ""
        }
        return anlasilirHata(code, if (msg.isBlank()) raw else msg)
    }

}
