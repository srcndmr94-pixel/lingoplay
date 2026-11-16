package com.example.altyazi

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 10.36: Model testi tamamlanmasa bile ilk saniyeden itibaren rapor verir.
 * Normal çeviri hattına / önbelleğe dokunmaz; yalnız ham motorları ölçer.
 * Motorları sırayla çalıştırır, ekran yakalama ile eşzamanlı çalışamaz.
 */
object EngineBenchmark1035 {
    data class Case(
        val source: String,
        val reference: String,
        val previous: List<String> = emptyList()
    )

    val cases: List<Case> = listOf(
        Case("Dude could be a cop for all we know.", "Bu adam polis bile olabilir, nereden bilelim?"),
        Case("I don't think so.", "Sanmıyorum.", listOf("Ever tell you about the fairground?")),
        Case("and get you both home as soon as possible.", "ve ikinizi de bir an önce evinize gönderebilelim.",
            listOf("But let's hope we can end this war quickly")),
        Case("Yeah, yeah. You gotta know when to get off, Jason.", "Ne zaman çekileceğini bilmelisin, Jason."),
        Case("Leave no one alive.", "Kimseyi sağ bırakmayın!"),
        Case("Our planetary defense network is back up.", "Gezegen savunma ağımız yeniden devrede."),
        Case("This is so fucked!", "Bu iş fena halde boktan!"),
        Case("He wants it on the quiet. He only wants one of you.", "Bu işin gizli kalmasını istiyor. Sadece birinizi istiyor."),
        Case("Well, Brian was quite the stud back in the day.", "Brian eskiden tam bir çapkındı."),
        Case("And I fucking jumped.", "Ben de atladım, amına koyayım."),
        Case("It started going faster and faster and it was getting harder to hold on.", "Gitgide hızlandı, tutunmak zorlaştı.")
    )

    data class Observation(val engine: String, val text: String?, val elapsedMs: Long, val error: String? = null)
    private val active = AtomicBoolean(false)
    val running: Boolean get() = active.get()
    private const val REPORT = "GameLingo_model_test_latest.txt"
    private const val PREVIOUS_REPORT = "GameLingo_model_test_10_35.txt"

    suspend fun run(context: Context, onProgress: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        check(active.compareAndSet(false, true)) { "Zaten bir model testi çalışıyor" }
        val out = StringBuilder()
        val observations = linkedMapOf<String, MutableList<Observation>>()
        val start = SystemClock.elapsedRealtime()
        var state = "BAŞLATILIYOR"
        var completed = false
        val dest = File(context.filesDir, REPORT)

        fun snapshot(): String = buildString {
            appendLine("GameLingo OPUS-MT - MODEL KARSILASTIRMA")
            appendLine("DURUM: $state")
            appendLine("Cihaz: ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}")
            appendLine("Önemli: Bu rapor tamamlanana kadar KISMI sonuç içerir.")
            appendLine("11 referans metin modele GONDERILMEZ; sadece kaliteyi insan değerlendirmesi içindir.")
            appendLine("OCR, sabit replikler ve uygulama ceviri hafizasi test disidir.")
            appendLine()
            append(out)
            appendLine("=== MEVCUT OZET ===")
            for ((label, results) in observations) {
                val success = results.filter { it.text != null }
                val stats = BenchmarkStats1035.summarize(success.map { it.elapsedMs })
                appendLine("$label: ${success.size}/${results.size} basarili; ortanca ${stats.median ?: "-"} ms; p90 ${stats.p90 ?: "-"} ms; 500 ms alti ${stats.under500}/${stats.count}")
            }
            appendLine("Toplam gecen sure: ${SystemClock.elapsedRealtime() - start} ms")
            if (!completed) appendLine("Test tamamlanmadi; bekleyen motorlarin ciktisi henüz yok.")
        }

        fun persist() {
            val temp = File(context.filesDir, "$REPORT.tmp")
            temp.writeText(snapshot())
            // Aynı dizinde yeniden adlandırma: eski rapor ile boş dosya arasında kalmayalım.
            if (!temp.renameTo(dest)) {
                dest.writeText(temp.readText())
                temp.delete()
            }
        }

        suspend fun progress(message: String) {
            state = message
            persist()
            onProgress(message)
        }

        suspend fun measure(engine: String, source: String, action: suspend () -> String?): Observation {
            currentCoroutineContext().ensureActive()
            val t0 = SystemClock.elapsedRealtime()
            return try {
                val response = withTimeoutOrNull(if (engine.startsWith("GPU") || engine.startsWith("CPU")) 25_000L else 12_000L) {
                    action()?.trim()?.takeIf { it.isNotBlank() }
                }
                Observation(engine, response, SystemClock.elapsedRealtime() - t0,
                    if (response == null) "bos yanit veya sure asimi" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Observation(engine, null, SystemClock.elapsedRealtime() - t0,
                    e.message ?: e.javaClass.simpleName)
            }
        }

        suspend fun phase(name: String, action: suspend (Case) -> String?) {
            for ((i, case) in cases.withIndex()) {
                currentCoroutineContext().ensureActive()
                progress("$name: ${i + 1}/${cases.size} replik")
                val result = measure(name, case.source) { action(case) }
                observations.getOrPut(name) { mutableListOf() }.add(result)
                out.appendLine("[$name ${i + 1}/${cases.size}] EN: ${case.source}")
                out.appendLine("Hedef TR: ${case.reference}")
                out.appendLine("${result.elapsedMs} ms: ${result.text ?: "BASARISIZ (${result.error})"}")
                if (result.text != null) {
                    MeaningSafety.risk(case.source, result.text)?.let { out.appendLine("! ANLAM GUVENLIGI: $it") }
                }
                out.appendLine()
                persist() // Her replikten hemen sonra rapor mevcut; 11 replik bitmesi gerekmez.
            }
        }

        try {
            progress("Test başladı; rapor kaydedildi")
            if (CaptureService.running) {
                progress("DURDURULDU: Önce canlı çeviriyi durdurun")
                return@withContext snapshot()
            }
            val prefs = Prefs(context)
            if (prefs.googleCeviri) {
                progress("Google: test başlıyor")
                phase("Google") { case -> GoogleCeviri.cevir(case.source, kullanHafiza = false) }
            } else {
                out.appendLine("Google: AYARLARDA KAPALI; atlandı.")
                persist()
            }

            // User-selected DeepL benchmark: raw text only, no glossary/translation cache.
            // Never transmit reference translations. It consumes the user's API quota.
            val deeplKey = if (prefs.deepLEnabled) DeepLCredentials.read(context) else ""
            if (prefs.deepLEnabled && deeplKey.isNotBlank()) {
                progress("DeepL: test başlıyor (11 API isteği, kota kullanılabilir)")
                phase("DeepL API") { case -> DeepLCeviri.translate(case.source, deeplKey) }
            } else {
                out.appendLine("DeepL: KAPALI veya API anahtarı yok; test atlandı.")
                persist()
            }

            val fast = FastTranslate()
            try {
                progress("ML Kit: dil paketi kontrol ediliyor (en çok 30 sn)")
                val fastStart = SystemClock.elapsedRealtime()
                val fastAvailable = try {
                    withTimeoutOrNull(30_000L) { fast.prepare() } ?: false
                } catch (e: CancellationException) { throw e }
                catch (t: Throwable) {
                    out.appendLine("ML Kit hazırlama hatası: ${t.message ?: t.javaClass.simpleName}")
                    false
                }
                out.appendLine("ML Kit hazırlama: ${SystemClock.elapsedRealtime() - fastStart} ms")
                if (fastAvailable) phase("ML Kit") { case -> fast.translate(case.source) }
                else {
                    out.appendLine("ML Kit HAZIR DEGIL: ${fast.lastError ?: "model indirilemedi veya 30 sn asildi"}")
                    persist()
                }
            } finally {
                fast.close()
            }

            // Direct on-device OPUS model, independent of Google, Firefox or cached phrases.
            if (OpusModelPack.isInstalled(context)) {
                val opus = OpusMtEngine(context)
                try {
                    progress("OPUS-MT: model hazırlanıyor")
                    val startOpus = SystemClock.elapsedRealtime()
                    val ok = opus.prepare()
                    out.appendLine("OPUS-MT model hazırlama: ${SystemClock.elapsedRealtime() - startOpus} ms")
                    if (ok) phase("OPUS-MT ONNX") { case -> opus.translate(case.source) }
                    else out.appendLine("OPUS-MT yükleme başarısız: ${opus.lastError}")
                    persist()
                } finally { opus.close() }
            } else {
                out.appendLine("OPUS-MT model paketi yüklü değil; test atlandı.")
                persist()
            }

            val llm = LlmTranslate(context)
            if (llm.hasModel()) {
                progress("E4B/E2B: model hazırlanıyor; ilk yükleme uzun sürebilir")
                val boot = SystemClock.elapsedRealtime()
                val llmAvailable = try {
                    withTimeoutOrNull(120_000L) { llm.prepare() } ?: false
                } catch (e: CancellationException) { throw e }
                catch (t: Throwable) {
                    out.appendLine("E4B/E2B yükleme hatası: ${t.message ?: t.javaClass.simpleName}")
                    false
                }
                out.appendLine("E4B/E2B hazırlama: ${SystemClock.elapsedRealtime() - boot} ms")
                if (llmAvailable) phase(llm.backendName) { case ->
                    llm.translate(case.source, case.previous, "Oyun", "",
                        discardIfStale = false, stillWanted = { true })
                } else {
                    out.appendLine("E4B/E2B HAZIR DEGIL: ${llm.lastError ?: "120 sn süresi aşıldı"}")
                    persist()
                }
            } else {
                out.appendLine("E4B/E2B model dosyası bulunamadı; test atlandı.")
                persist()
            }
            completed = true
            progress("TAMAMLANDI: rapor hazır")
            snapshot()
        } catch (e: CancellationException) {
            progress("İPTAL EDİLDİ: kısmi rapor saklandı")
            throw e
        } catch (t: Throwable) {
            progress("HATA: ${t.message ?: t.javaClass.simpleName}; kısmi rapor saklandı")
            snapshot()
        } finally {
            try { persist() } finally { active.set(false) }
        }
    }

    fun lastReport(context: Context): String? =
        File(context.filesDir, REPORT).takeIf { it.exists() }?.readText()
            ?: File(context.filesDir, PREVIOUS_REPORT).takeIf { it.exists() }?.readText()
}
