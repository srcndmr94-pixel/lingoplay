package com.example.altyazi

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** İndirilebilir model: dosya adı, adres, boyut, ekranda görünen ad. */
class ModelSpec(val file: String, val url: String, val bytes: Long, val label: String)

/**
 * Telefonun İÇİNDE çalışan yapay zekâ çevirisi (Gemma 4 E2B, LiteRT-LM).
 * İnternet, anahtar, jeton yok. Model bir kez indirilir (~2,6 GB).
 *
 * Model uygulama açık kaldığı sürece BİR KEZ yüklenir ve bellekte kalır:
 * Durdur/Başlat yapınca 30 sn yeniden yükleme beklenmez, iki kopya da açılmaz.
 */
class LlmTranslate(context: Context) {

    private val ctx: Context = context.applicationContext

    val ready: Boolean get() = shared.ready
    val lastError: String? get() = shared.lastError
    val backendName: String get() = shared.backendName

    fun hasModel(): Boolean = hasModel(ctx)

    /** Modeli yükler (ilk seferde ~10-30 sn). Önce GPU, olmazsa CPU. Zaten yüklüyse hemen döner. */
    @OptIn(ExperimentalApi::class)
    @Suppress("DEPRECATION")
    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        shared.lock.withLock {
            val wanted = modelFile(ctx).absolutePath
            if (shared.ready && shared.loadedPath == wanted) return@withLock true
            // Daha iyi model sonradan indiyse eskisini kapatıp yenisini yükle.
            if (shared.engine != null) {
                try { shared.sicak?.close() } catch (_: Throwable) {}
                shared.sicak = null
                try { shared.engine?.close() } catch (_: Throwable) {}
                shared.engine = null
                shared.ready = false
            }
            if (!hasModel()) {
                shared.lastError = "model dosyası yok"
                return@withLock false
            }
            // Hızlandırma (çoklu kelime tahmini, MTP): model dosyası destekliyorsa GPU'da açılır.
            val mtp = try {
                ModelInfo.from(wanted).use { (it as? ModelInfo.Llm)?.hasSpeculativeDecodingSupport() == true }
            } catch (_: Throwable) {
                false
            }
            // GPU+MTP, GPU, CPU sırasıyla denenir; ilk çalışan kullanılır.
            val attempts: List<Pair<Backend, Boolean>> =
                (if (mtp) listOf<Pair<Backend, Boolean>>(Backend.GPU() to true) else emptyList()) +
                    listOf(Backend.GPU() to false, Backend.CPU() to false)
            for ((b, fast) in attempts) {
                val name = (if (b is Backend.GPU) "GPU" else "CPU") + (if (fast) " · hızlı" else "")
                ExperimentalFlags.enableSpeculativeDecoding = fast
                try {
                    val e = Engine(
                        EngineConfig(
                            modelPath = wanted,
                            backend = b,
                            cacheDir = ctx.cacheDir.path
                        )
                    )
                    e.initialize()
                    shared.engine = e
                    shared.backendName = name + " · " + (if (modelFile(ctx).name == E4B.file) "E4B" else "E2B")
                    shared.ready = true
                    shared.loadedPath = wanted
                    shared.lastError = null
                    return@withLock true
                } catch (t: Throwable) {
                    shared.lastError = "$name: ${t.message ?: t.javaClass.simpleName}"
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = false
                }
            }
            false
        }
    }

    /**
     * Bir satırı çevirir. [stillWanted] sıra gelince tekrar sorulur; replik
     * çoktan geçtiyse boşuna çalışılmaz. [contentType] ve [title] ana ekrandaki
     * "Ne izliyorsunuz" alanlarıdır (ör. "Oyun", "Batman").
     */
    suspend fun translate(
        line: String,
        previous: List<String>,
        contentType: String,
        title: String,
        discardIfStale: Boolean = true,
        stillWanted: () -> Boolean
    ): String? = withContext(Dispatchers.IO) {
        val asked = System.currentTimeMillis()
        shared.lock.withLock {
            shared.lastError = null
            lastWaitMs = System.currentTimeMillis() - asked
            val e = shared.engine ?: return@withLock null
            if (!stillWanted()) return@withLock null
            try {
                // ÖNCEDEN HAZIRLANMIŞ ("sıcak") konuşma varsa onu kullan.
                // Her satırda yeni Conversation açmak, talimatın (SYSTEM)
                // modele yeniden okutulması demek. Onu satırlar ARASINDAKİ
                // boş sürede hazırlıyoruz; satır geldiğinde yalnız repliğin
                // kendisi işlenir. Kazanç cihazda ölçülecek: kayıttaki
                // "kurulum X ms" bu adımın süresidir, "sıcak" ise hazır
                // konuşmanın kullanıldığını gösterir.
                val k0 = System.currentTimeMillis()
                val hazir = shared.sicak
                shared.sicak = null
                lastSicak = hazir != null
                val conv = hazir ?: yeniKonusma(e)
                lastKurulumMs = System.currentTimeMillis() - k0
                shared.current = conv
                shared.currentLine = line
                val out = try {
                    conv.sendMessage(
                        buildPrompt(line, previous, contentType, title),
                        maxOutputToken = 100
                    ).toString()
                } finally {
                    shared.current = null
                    shared.currentLine = null
                    try { conv.close() } catch (_: Throwable) {}
                }
                // Hız modunda: replik geçtiyse (çeviri kesilmiş olabilir) sonucu atma.
                // Doğruluk modunda kesmiyoruz; sonuç tam, hafızaya alınsın diye döndürülür.
                isit()
                if (discardIfStale && !stillWanted()) return@withLock null
                clean(out, line)
            } catch (t: Throwable) {
                // Altyazı değişince bilerek kestiğimiz çeviri hata sayılmaz.
                val msg = t.message ?: t.javaClass.simpleName
                if (!msg.contains("CANCELLED", ignoreCase = true)) shared.lastError = msg
                null
            }
        }
    }

    /** Son çeviride sırada beklenen süre (ms). */
    @Volatile var lastWaitMs = 0L
        private set

    /** Son çeviride konuşmayı kurmanın süresi (ms) ve hazır konuşma kullanıldı mı. */
    @Volatile var lastKurulumMs = 0L
        private set
    @Volatile var lastSicak = false
        private set

    private fun yeniKonusma(e: Engine): Conversation = e.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(SYSTEM),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
        )
    )

    /**
     * Bir sonraki satır için konuşmayı ARKA PLANDA hazırlar. Kilidi aldığı
     * için yeni gelen çeviri isteği hazırlığın bitmesini bekler; o hazırlık
     * zaten yapılacaktı, yalnız öne çekilmiş olur. Aynı anda en çok BİR
     * konuşma açık kalır (önceki kapatıldıktan sonra çağrılıyor). Hata
     * olursa sessizce vazgeçilir; çeviri eski yoldan sürer.
     */
    private fun isit() {
        isitmaScope.launch {
            shared.lock.withLock {
                if (shared.sicak != null) return@withLock
                val e = shared.engine ?: return@withLock
                shared.sicak = try { yeniKonusma(e) } catch (_: Throwable) { null }
            }
        }
    }

    private val isitmaScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 10.8: model yüklenir yüklenmez ilk konuşmayı hazırla. Eskiden "sıcak"
     * konuşma yalnız bir çeviriden SONRA açılıyordu; oturumun ilk satırı
     * kurulum süresini de bekliyordu.
     */
    fun hazirla() {
        if (shared.ready) isit()
    }

    /**
     * Servis durunca modeli KAPATMIYORUZ: bir sonraki Başlat'ta beklemesiz
     * kullanılsın. Android bellek isterse uygulamayı zaten kendisi kapatır.
     */
    fun close() {}

    private fun buildPrompt(line: String, previous: List<String>, contentType: String, title: String): String {
        val sb = StringBuilder()
        val kind = when (contentType.trim().lowercase()) {
            "oyun" -> "a video game"
            "dizi" -> "a TV series"
            "film" -> "a film"
            "belgesel" -> "a documentary"
            else -> "a video"
        }
        sb.append("Source: ").append(kind)
        if (title.isNotBlank()) sb.append(" titled \"").append(title.trim()).append('"')
        sb.append(".\n\n")

        // ÖNCEKİ SATIRLAR YALNIZ DEVAM SATIRINDA GÖNDERİLİR.
        //
        // Ölçüm: modele giden istem ~2206 karakter, repliğin kendisi 40.
        // Yani %98'i kalıp ve hepsi HER SATIRDA baştan işleniyor. "Önceki
        // satırlar" bloğu bunun ~290 karakteri ve satırların çoğunda hiçbir
        // işe yaramıyor — çünkü çoğu satır bir öncekinin devamı değil.
        //
        // Devam satırının iki işareti var: önceki satır cümle noktalamasıyla
        // bitmemiştir ya da bu satır küçük harfle başlar. İkisi de yoksa
        // bağlam gereksizdir. Gecikme zamanındalığın tek darboğazı olduğu
        // için (yapay zekâ 2-5,7 sn, altyazı 2-4 sn) bu kesinti doğrudan
        // kapsamaya yazılıyor.
        val oncekiSatir = previous.lastOrNull()?.trim()
        val devamMi = oncekiSatir != null && oncekiSatir.isNotEmpty() && (
            oncekiSatir.last() !in ".!?…\"" ||
                line.trimStart().firstOrNull()?.isLowerCase() == true
            )
        if (devamMi) {
            sb.append("Previous line (context only, do NOT translate):\n- ")
                .append(oncekiSatir).append('\n')
            sb.append("If this line continues that sentence, translate only the new part.\n\n")
        }

        val hints = Slang.hintsFor(line)
        if (hints.isNotEmpty()) {
            sb.append("Slang here:\n")
            for (h in hints) sb.append("- ").append(h).append('\n')
            sb.append('\n')
        }
        sb.append("Line:\n").append(line)
        return sb.toString()
    }

    /** Modelin cevabından sadece çeviriyi bırakır; saçma cevapsa null. */
    private fun clean(raw: String, source: String): String? {
        var t = raw.trim()
        t = t.removePrefix("```").removeSuffix("```").trim()
        t = t.replace(Regex("^(Türkçe|Turkish|Çeviri|Translation)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        t = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        if (t.length >= 2 && t.first() == '"' && t.last() == '"' && !source.trim().startsWith("\"")) {
            t = t.substring(1, t.length - 1).trim()
        }
        // Kaynakta köşeli parantez yoksa model kendiliğinden eklememeli: "[Bu kokuyor!]" -> "Bu kokuyor!"
        if (!source.contains('[') && t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length - 1).trim()
        if (t.isEmpty()) return null
        if (t.length > source.length * 3 + 40) return null   // açıklama yazmaya başlamış
        return t
    }

    /** Uygulama boyunca tek motor. */
    private class Shared {
        val lock = Mutex()
        @Volatile var engine: Engine? = null
        @Volatile var ready = false
        @Volatile var lastError: String? = null
        @Volatile var backendName = ""
        @Volatile var loadedPath = ""
        // Yalniz tani icin tutuluyor: yarida kesme kaldirildigindan (bkz.
        // CaptureService.refineOnDevice) bunlari okuyan kod kalmadi.
        @Volatile var current: Conversation? = null
        @Volatile var currentLine: String? = null
        /** Bir sonraki satır için önceden açılmış konuşma (bkz. isit). */
        @Volatile var sicak: Conversation? = null
    }

    companion object {
        private val shared = Shared()

        /** Yükleme servisten BAĞIMSIZ: Durdur'a basılsa da yarıda kesilmez. */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var loadJob: Job? = null

        /** Ana ekranda gösterilen yükleme durumu. */
        @Volatile var loadState: String = ""
            private set

        fun isLoading(): Boolean = loadJob?.isActive == true

        fun isReady(context: Context): Boolean =
            shared.ready && shared.loadedPath == modelFile(context).absolutePath

        /** Model telefondaysa arka planda belleğe yükler (zaten yüklüyse/yükleniyorsa bir şey yapmaz). */
        fun preload(context: Context) {
            val c = context.applicationContext
            if (!hasModel(c) || isReady(c) || isLoading()) return
            loadJob = appScope.launch {
                loadState = "yükleniyor"
                val t0 = System.currentTimeMillis()
                val ok = try {
                    LlmTranslate(c).prepare()
                } catch (e: Throwable) {
                    shared.lastError = e.message ?: e.javaClass.simpleName
                    false
                }
                val sec = (System.currentTimeMillis() - t0) / 1000
                loadState = if (ok) "hazır (${shared.backendName}, $sec sn)" else "hata: ${shared.lastError}"
            }
        }


        /** Büyük model: argo ve bağlamı daha iyi anlar, ~2 kat yavaş. 12 GB+ bellek. */
        val E4B = ModelSpec(
            "gemma-4-E4B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/" +
                "28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm?download=true",
            3_659_530_240L, "Gelişmiş (E4B, 3,7 GB)"
        )

        /** Küçük model: hızlı ama argoyu daha az bilir. */
        val E2B = ModelSpec(
            "gemma-4-E2B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm?download=true",
            2_583_085_056L, "Hızlı (E2B, 2,6 GB)"
        )

        val MODELS = listOf(E4B, E2B)

        private fun dir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

        fun fileOf(context: Context, m: ModelSpec): File = File(dir(context), m.file)

        fun isComplete(context: Context, m: ModelSpec): Boolean =
            fileOf(context, m).let { it.exists() && it.length() >= m.bytes * 95 / 100 }

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences("model_indirme", Context.MODE_PRIVATE)

        /** Kullanıcının seçtiği model ("E4B" ya da "E2B"); seçmediyse gelişmiş olan. */
        fun preferred(context: Context): ModelSpec =
            if (prefs(context).getString("tercih", "E4B") == "E2B") E2B else E4B

        fun setPreferred(context: Context, m: ModelSpec) {
            prefs(context).edit().putString("tercih", if (m === E2B) "E2B" else "E4B").apply()
        }

        /** Kullanılacak model: tercih edilen telefondaysa o, değilse telefondaki diğeri. */
        fun active(context: Context): ModelSpec? {
            val p = preferred(context)
            if (isComplete(context, p)) return p
            return MODELS.firstOrNull { isComplete(context, it) }
        }

        fun modelFile(context: Context): File = fileOf(context, active(context) ?: E2B)

        fun hasModel(context: Context): Boolean = MODELS.any { isComplete(context, it) }

        /** Seçilen dosyanın boyutuna göre hangi model olduğunu bulur. */
        fun guessModel(sizeBytes: Long): ModelSpec? =
            MODELS.firstOrNull { sizeBytes in (it.bytes * 95 / 100)..(it.bytes * 105 / 100) }

        // Kısa tutuldu: her satırda yeniden işleniyor, uzun talimat her satırı yavaşlatır.
        /**
         * TEK EN BÜYÜK KALDIRAÇ — ama her satırda yeniden yüklenen bir maliyet.
         *
         * Her çeviride yeni bir Conversation açılıyor, yani bu talimat HER
         * SATIRDA baştan işleniyor. 9.64'te maddeleri ekleyince uzunluğu üç
         * katına çıkardım; ölçüm bunun bedelini gösterdi:
         *   9.63 yapay zekâ süreleri: 1682-3826 ms, ortalama ~2800 ms
         *   9.65 yapay zekâ süreleri: 3384-4825 ms, ortalama  3808 ms
         * Gecikme bir saniye artınca yapay zekâ altyazı tempsuna yetişemedi:
         * 56 replikten yalnız 5'ini düzeltebildi.
         *
         * Bu yüzden maddelerin HİÇBİRİ atılmadı, hepsi KISALTILDI: aynı
         * kurallar, telgraf üslubuyla, 2202 yerine ~1500 karakter. Her madde
         * kullanıcının kayıtlarında ölçülmüş bir hatadan geliyor.
         */
        private const val SYSTEM =
            "Translate the English subtitle line into natural spoken Turkish, like a Turkish dubbing studio. " +
                // 9.63: OCR "alive"i "olive" okudu, model uyarilmadigi icin "Zeytin mi?" dedi.
                "Input is OCR text: it may have misread characters (l/I/1, O/0, rn/m) or words " +
                "that make no sense here. Fix them from context; never translate a nonsensical " +
                "reading literally. " +
                // 9.63: "I beg to differ" -> "Farkli olmak icin yalvariyorum".
                "Translate meaning, not words: idioms and slang get the phrase a Turkish speaker " +
                "would really say. Never reverse negation or prohibition; keep who acts on whom. " +
                "Translate orders as Turkish imperatives, not infinitives. " +
                // 9.61: "Y'all know Boobie?" -> "Hepiniz ... biliyor musun?" (tekil fiil).
                "If the English addresses several people (y'all, you guys), use Turkish plural " +
                "endings throughout; never mix plural with a singular verb. " +
                // SESLENME KURALI BİLEREK BURADA DEĞİL.
                // "Yo"nun zorunlu "lan" olmaması YoContext10427 ile sağlanıyor. Aynı
                // kuralı isteme de yazmak her satırda ~150 karakter maliyet
                // demekti, karşılığı yok. İsteme yalnız kodun GARANTİ
                // ETMEDİĞİ kurallar giriyor.
                "Keep profanity equally strong, in place, chosen by context; never censor, soften " +
                "or omit. 'fucking' is an intensifier, not a noun: 'He fucking knows.'='Biliyor lan.', " +
                // 9.98: kullanıcı: "what the fuck - bu ne lan demek, sikim değil".
                "'What the fuck?'='Bu ne lan?', 'What the fuck is going on?'='Ne oluyor lan?'; " +
                "render 'the fuck' with 'lan', never 'sikim'. " +
                "'Don't fucking move!'='Kıpırdama lan!', 'Holy shit!'='Hassiktir!', 'Fuck!'='Siktir!'. " +
                "Keep names, nicknames, places and brands as they are, capitalized. " +
                // 9.63: "[Batman] Vinny DeLeon, Roger Marshall," ekrana hic gelmedi.
                "A line of only names is already correct: return it unchanged, never empty. " +
                "Titles: Commissioner=Komiser, Detective=Dedektif, Officer=Memur, Sergeant=Çavuş, " +
                "General=General, Colonel=Albay, Mr.=Bay, Mrs./Ms./Miss=Bayan, Doctor=Doktor, " +
                "Captain=Kaptan. " +
                "Square-bracket text is a sound or speaker label: short Turkish, keep brackets. " +
                "Keep it short, readable at a glance. " +
                "Output ONLY the Turkish translation: no explanation, no quotes, no added brackets."
    }
}
