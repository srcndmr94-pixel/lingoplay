package com.example.altyazi

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ana ekrandaki "Gelişmiş çevrim dışı çeviri" kartı.
 * - Gelişmiş modeli (E4B, 3,7 GB) indirir; inene kadar standart model (E2B) çalışır.
 * - Telefondaki .litertlm dosyasını kopyalar (boyutundan hangi model olduğunu anlar).
 * - Gelişmiş model inince eski küçük modeli silip yer açmayı önerir.
 */
class ModelSetup(
    private val activity: AppCompatActivity,
    private val pickFile: () -> Unit
) {
    private val ctx: Context = activity.applicationContext
    private val sp = ctx.getSharedPreferences("model_indirme", Context.MODE_PRIVATE)
    private val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var tvState: TextView
    private lateinit var btnDownload: Button
    private lateinit var btnPick: Button
    private lateinit var btnDeleteSmall: Button
    private lateinit var btnSwitch: Button
    @Volatile private var copying: String? = null
    private var benchmarkJob: Job? = null
    private var benchmarkButton: Button? = null
    private var benchmarkStatus: TextView? = null

    /** İndirilecek model: seçili model telefonda yoksa o. */
    private val target: ModelSpec
        get() = sp.getString("indirilen", null)?.let { if (it == "E2B") LlmTranslate.E2B else LlmTranslate.E4B }
            ?: LlmTranslate.preferred(ctx)

    /** Kartı [parent] içine, [index] sırasına ekler. */
    fun attach(parent: LinearLayout, index: Int) {
        val pad = (12 * ctx.resources.displayMetrics.density).toInt()
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, pad)
        }
        box.addView(TextView(activity).apply {
            text = "Gelişmiş çevrim dışı çeviri (telefondaki yapay zekâ)"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(activity).apply {
            text = "E4B, Pixel 9 Pro testinde yaklaşık 3,3 saniye/replik sürdü. " +
                "Canlı Hızlı ve Dengeli modlarda yüklenmez. " +
                "Yalnız deneysel Doğru önce modunda veya karşılaştırma testinde çalışır. " +
                "Bu model için internet ya da API anahtarı gerekmez."
            textSize = 13f
        })
        tvState = TextView(activity).apply { textSize = 13f; setPadding(0, pad / 2, 0, pad / 2) }
        box.addView(tvState)
        btnDownload = Button(activity).apply {
            text = "SEÇİLİ MODELİ İNDİR"
            setOnClickListener {
                if (sp.getLong("id", -1L) != -1L) cancelDownload() else askAndDownload()
            }
        }
        box.addView(btnDownload)
        btnPick = Button(activity).apply {
            text = "İNDİRİLMİŞ DOSYAYI SEÇ (.litertlm)"
            setOnClickListener { pickFile() }
        }
        box.addView(btnPick)
        btnSwitch = Button(activity).apply {
            setOnClickListener {
                val now = LlmTranslate.preferred(ctx)
                LlmTranslate.setPreferred(ctx, if (now === LlmTranslate.E4B) LlmTranslate.E2B else LlmTranslate.E4B)
                // Merely switching preferred model must not allocate native GPU memory.
                refresh()
            }
        }
        box.addView(btnSwitch)
        btnDeleteSmall = Button(activity).apply {
            text = "ESKİ KÜÇÜK MODELİ SİL (2,6 GB boşalt)"
            // Real confirmation listener is installed in refresh(), once the unused model is known.
            setOnClickListener { refresh() }
        }
        box.addView(btnDeleteSmall)
        box.addView(Button(activity).apply {
            text = "ÇEVİRİ HAFIZASINI TEMİZLE"
            setOnClickListener {
                // Modelleri karşılaştırırken aynı sahne hafızadan değil, seçili modelden çevrilsin.
                try { TranslationStore.clearAll(ctx) } catch (_: Throwable) {}
                OfflineFix.clearCache()
                tvState.text = "✓ Çeviri hafızası temizlendi. Aynı sahne şimdi seçili modelle yeniden çevrilecek."
                refresh(keepText = true)
            }
        })
        // 10.36: ara rapor ilk saniyeden itibaren kaydedilir.
        benchmarkStatus = TextView(activity).apply {
            textSize = 13f
            text = "Model testi hazır. Canlı çeviriyi durdurup karşılaştırmayı başlatın."
            setPadding(0, pad / 2, 0, pad / 2)
        }
        box.addView(benchmarkStatus)
        // Ekran yakalama devam ederken GPU/LLM kilidini asla isgal etme.
        benchmarkButton = Button(activity).apply {
            text = "OPUS / E4B / ML KIT / GOOGLE / DEEPL TESTI (11 REPLIK)"
            setOnClickListener {
                if (benchmarkJob?.isActive == true) {
                    benchmarkJob?.cancel()
                    text = "TEST IPTAL EDILDI - TEKRAR BASLAT"
                    return@setOnClickListener
                }
                if (CaptureService.running) {
                    benchmarkStatus?.text = "Canlı çeviri açık. Önce ana ekranda DURDUR'a basın."
                    android.widget.Toast.makeText(ctx, "Once canli ceviriyi DURDUR.", android.widget.Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // E4B yoksa da Google/ML Kit testi geçerli; rapor bunun nedenini yazar.
                benchmarkJob = scope.launch {
                    try {
                        text = "TEST CALISIYOR - IPTAL ET"
                        benchmarkStatus?.text = "Test başladı. Raporu istediğiniz an gösterebilirsiniz."
                        val result = EngineBenchmark1035.run(ctx) { progress ->
                            withContext(Dispatchers.Main) {
                                benchmarkStatus?.text = "Model testi: $progress"
                            }
                        }
                        showBenchmarkResult(result)
                    } catch (_: CancellationException) {
                        benchmarkStatus?.text = "Test iptal edildi. Kısmi raporu gösterebilirsiniz."
                    } catch (t: Throwable) {
                        benchmarkStatus?.text = "Model testi hatası: ${t.message ?: t.javaClass.simpleName}. Kısmi raporu kontrol edin."
                    } finally {
                        text = "OPUS / E4B / ML KIT / GOOGLE / DEEPL TESTI (11 REPLIK)"
                    }
                }
            }
        }
        box.addView(benchmarkButton)
        box.addView(Button(activity).apply {
            text = "SON MODEL TESTI RAPORUNU GOSTER"
            setOnClickListener {
                val result = EngineBenchmark1035.lastReport(ctx)
                if (result == null) android.widget.Toast.makeText(ctx, "Testi önce BAŞLATIN; rapor hemen oluşur.", android.widget.Toast.LENGTH_LONG).show()
                else showBenchmarkResult(result)
            }
        })
        parent.addView(box, index.coerceIn(0, parent.childCount))
        refresh()
    }

    fun onResume() {
        // 10.37: Visiting Settings must never preload a multi-GB E4B model.
        // The actual request (benchmark or explicit DOĞRU mode) owns loading.
        refresh()
    }

    fun onPause() {
        main.removeCallbacksAndMessages(null)
        // 10.35'te onPause testi sessizce iptal ediyordu. Ekran kilidi,
        // uygulama geçişi veya izin penceresinde testin kısmi raporu kaybolmasın.
        // Görev, kullanıcı IPTAL'e basana veya Activity tamamen yok olana kadar sürer.
    }

    fun onDestroy() {
        benchmarkJob?.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun showBenchmarkResult(report: String) {
        val view = TextView(activity).apply {
            text = report
            textSize = 12f
            setPadding(28, 20, 28, 20)
            setTextIsSelectable(true)
        }
        val scroll = android.widget.ScrollView(activity).apply { addView(view) }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Model testi - ara / nihai rapor")
            .setView(scroll)
            .setPositiveButton("RAPORU KOPYALA") { _, _ ->
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("GameLingo model testi", report))
                android.widget.Toast.makeText(ctx, "Test raporu kopyalandi.", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("KAPAT", null)
            .show()
    }

    private fun partFile(): File = File(LlmTranslate.fileOf(ctx, target).parentFile, target.file + ".part")

    /** Wi-Fi mi, mobil veri de olur mu? Mobil veri seçilirse boyutu açıkça söyler. */
    private fun askAndDownload() {
        val m = LlmTranslate.preferred(ctx)
        val gb = String.format(java.util.Locale.US, "%.1f", m.bytes / 1_000_000_000.0).replace('.', ',')
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("${m.label} indirilsin")
            .setMessage("Dosya yaklaşık $gb GB. Mobil veriyle indirirsen tarifenden $gb GB gider.")
            .setPositiveButton("Sadece Wi-Fi") { _, _ -> startDownload(metered = false) }
            .setNeutralButton("Mobil veri de olur") { _, _ -> startDownload(metered = true) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun cancelDownload() {
        val id = sp.getLong("id", -1L)
        try { if (id != -1L) dm.remove(id) } catch (_: Throwable) {}
        partFile().delete()
        sp.edit().remove("id").remove("indirilen").apply()
        tvState.text = "İndirme iptal edildi."
        refresh(keepText = true)
    }

    private fun startDownload(metered: Boolean) {
        val target = LlmTranslate.preferred(ctx)
        sp.edit().putString("indirilen", if (target === LlmTranslate.E2B) "E2B" else "E4B").apply()
        LlmTranslate.fileOf(ctx, target).parentFile?.mkdirs()
        partFile().delete()
        try {
            val req = DownloadManager.Request(Uri.parse(target.url))
                .setTitle("Lingo çeviri modeli")
                .setDescription(target.label)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(metered)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(ctx, null, target.file + ".part")
            sp.edit().putLong("id", dm.enqueue(req)).apply()
        } catch (e: Throwable) {
            tvState.text = "İndirme başlatılamadı: ${e.message}"
            return
        }
        refresh()
    }

    /** Dosya seçiciden gelen dosyayı uygulamanın klasörüne kopyalar. */
    fun copyFrom(uri: Uri) {
        if (copying != null) return
        copying = "başlıyor"
        refresh()
        scope.launch {
            val result: String? = withContext(Dispatchers.IO) {
                try {
                    val dir = LlmTranslate.fileOf(ctx, target).parentFile!!
                    dir.mkdirs()
                    val tmp = File(dir, "secilen.litertlm.copy")
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(1 shl 20)
                            var total = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                total += n
                                copying = "${total / (1024 * 1024)} MB"
                            }
                        }
                    }
                    val m = LlmTranslate.guessModel(tmp.length())
                    if (m == null) {
                        tmp.delete()
                        null
                    } else {
                        val dest = LlmTranslate.fileOf(ctx, m)
                        dest.delete()
                        if (tmp.renameTo(dest)) m.label else null
                    }
                } catch (e: Throwable) {
                    CrashLog.note(ctx, "model kopyalama", e)
                    null
                }
            }
            copying = null
            tvState.text = if (result != null) "✓ $result hazır. BAŞLAT'a basın." else
                "✗ Dosya tanınmadı ya da eksik. gemma-4-E4B-it.litertlm veya gemma-4-E2B-it.litertlm seçin."
            refresh(keepText = true)
        }
    }

    private fun refresh(keepText: Boolean = false) {
        main.removeCallbacksAndMessages(null)
        if (!::tvState.isInitialized) return
        val finalFile = LlmTranslate.fileOf(ctx, target)
        val part = partFile()

        copying?.let {
            tvState.text = "Kopyalanıyor… $it"
            btnDownload.isEnabled = false; btnPick.isEnabled = false
            main.postDelayed({ refresh() }, 1000)
            return
        }

        val id = sp.getLong("id", -1L)
        if (id != -1L) {
            var status = -1
            var done = 0L
            var reason = 0
            try {
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (c.moveToFirst()) {
                        status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                }
            } catch (_: Throwable) {
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    sp.edit().remove("id").remove("indirilen").apply()
                    if (part.exists() && part.length() >= target.bytes * 95 / 100) {
                        finalFile.delete(); part.renameTo(finalFile)
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    sp.edit().remove("id").remove("indirilen").apply()
                    part.delete()
                    tvState.text = "✗ İndirme başarısız (kod $reason). Bağlantıyı kontrol edip tekrar deneyin " +
                        "ya da dosyayı Chrome ile indirip 'DOSYAYI SEÇ' ile gösterin."
                    refresh(keepText = true)
                    return
                }
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                    val pct = done * 100 / target.bytes
                    val mb = done / (1024 * 1024)
                    val totalMb = target.bytes / (1024 * 1024)
                    tvState.text = (if (status == DownloadManager.STATUS_PAUSED || (status == DownloadManager.STATUS_PENDING && done == 0L))
                        "${target.label} indirme bekliyor (Wi-Fi ya da bağlantı bekleniyor) · %$pct ($mb MB)"
                    else "${target.label} iniyor… %$pct ($mb MB / $totalMb MB)") +
                        (if (LlmTranslate.hasModel(ctx)) "\nBu sırada telefondaki model çalışmaya devam eder." else "")
                    btnDownload.isEnabled = true
                    btnDownload.text = "İNDİRMEYİ İPTAL ET"
                    btnPick.isEnabled = false
                    btnSwitch.isEnabled = false
                    btnSwitch.text = "İNDİRME SÜRÜYOR"
                    btnDeleteSmall.visibility = android.view.View.GONE
                    main.postDelayed({ refresh() }, 1000)
                    return
                }
                else -> sp.edit().remove("id").remove("indirilen").apply()
            }
        }

        btnPick.isEnabled = true
        btnSwitch.isEnabled = true
        val hasBig = LlmTranslate.isComplete(ctx, LlmTranslate.E4B)
        val hasSmall = LlmTranslate.isComplete(ctx, LlmTranslate.E2B)
        val pref = LlmTranslate.preferred(ctx)
        val active = LlmTranslate.active(ctx)
        val mem = when {
            LlmTranslate.isLoading() -> "\n⏳ Yapay zekâ belleğe yükleniyor… (ilk seferde 1-2 dk, BAŞLAT'tan önce bekleyin)"
            LlmTranslate.isReady(ctx) -> "\n✓ Yapay zekâ " + LlmTranslate.loadState
            LlmTranslate.loadState.startsWith("hata") -> "\n✗ Yapay zekâ yüklenemedi: " + LlmTranslate.loadState
            else -> ""
        }
        if (LlmTranslate.isLoading()) main.postDelayed({ refresh() }, 1000)

        val other = if (pref === LlmTranslate.E4B) LlmTranslate.E2B else LlmTranslate.E4B
        btnSwitch.text = if (pref === LlmTranslate.E4B) "HIZLI MODELE GEÇ (E2B · ~2 kat hızlı)"
            else "GELİŞMİŞ MODELE GEÇ (E4B · argoyu daha iyi anlar)"
        btnDeleteSmall.visibility = android.view.View.GONE
        btnDeleteSmall.text = "KULLANILMAYAN ${other.label} MODELİNİ SİL"
        if (hasBig && hasSmall) btnDeleteSmall.visibility = android.view.View.VISIBLE
        btnDeleteSmall.setOnClickListener {
            if (CaptureService.running || EngineBenchmark1035.running || LlmTranslate.isLoading()) {
                android.widget.Toast.makeText(ctx, "Önce canlı çeviriyi ve testi durdurun.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("${other.label} silinsin mi?")
                    .setMessage("Bu model dosyası silinecek. Tekrar kullanmak için yeniden yüklemen gerekir. Çeviri hafızası korunur.")
                    .setNegativeButton("İPTAL", null)
                    .setPositiveButton("MODELİ SİL") { _, _ ->
                        if (!CaptureService.running && !EngineBenchmark1035.running && !LlmTranslate.isLoading()) {
                            val success = LlmTranslate.fileOf(ctx, other).delete()
                            android.widget.Toast.makeText(ctx, if (success) "Model silindi" else "Model silinemedi", android.widget.Toast.LENGTH_LONG).show()
                            refresh()
                        }
                    }.show()
            }
        }

        val prefHas = LlmTranslate.isComplete(ctx, pref)
        btnDownload.isEnabled = !prefHas
        btnDownload.text = if (prefHas) "SEÇİLİ MODEL YÜKLÜ" else "SEÇİLİ MODELİ İNDİR (${pref.label})"
        if (!keepText) tvState.text = when {
            active == null -> "Model yok. Seçili modeli indirmek için Wi-Fi'ye bağlanın."
            prefHas -> "✓ Seçili model: ${pref.label}. Telefonda hazır."
            else -> "Seçili model ${pref.label} telefonda yok, indirilebilir.\nŞimdilik ${active.label} kullanılıyor."
        } + mem
    }
}
