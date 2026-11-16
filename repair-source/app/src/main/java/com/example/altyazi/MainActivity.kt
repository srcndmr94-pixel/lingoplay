package com.example.altyazi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val TURLER = listOf("Oyun", "Dizi", "Film", "Belgesel", "Video")

    /** Yazı boyutu çubuğunun başladığı değer; çubuk 0'dan saydığı için gerekli. */
    private val SUB_SIZE_MIN = 12

    private lateinit var prefs: Prefs

    private lateinit var etGame: EditText
    private lateinit var spType: Spinner
    private lateinit var etKey: EditText
    private lateinit var etGeminiKey: EditText
    private lateinit var rgProvider: RadioGroup
    private lateinit var etModel: EditText
    private lateinit var rgMode: RadioGroup
    private lateinit var cbSpeaker: CheckBox
    private lateinit var rgBicim: RadioGroup
    private lateinit var cbAccuracy: CheckBox
    private lateinit var cbDiag: CheckBox
    private lateinit var sbMinShow: SeekBar
    private lateinit var cbDogruyuBekle: CheckBox
    private lateinit var cbYalnizDogru: CheckBox
    private lateinit var cbYigin: CheckBox
    private lateinit var cbAlwaysAi: CheckBox
    private lateinit var cbFirefox: CheckBox
    private lateinit var cbGoogle: CheckBox
    private lateinit var cbOpus: CheckBox
    private lateinit var cbDeepL: CheckBox
    private lateinit var etDeepLKey: EditText

    /** Offline Translator kurulu mu, ayar ekranında yazar. */
    private fun firefoxDurumYaz() {
        val kurulu = FirefoxCeviri.kurulu(this)
        findViewById<TextView>(R.id.tvFirefoxDurum).text =
            if (kurulu) "Offline Translator kurulu. Uygulamada İngilizce ve Türkçe dil paketlerini indirdiğinizden emin olun."
            else "Offline Translator kurulu değil. Aşağıdaki düğmeyle F-Droid'den ücretsiz indirin, açıp İngilizce ve Türkçe dil paketlerini indirin."
        findViewById<Button>(R.id.btnFirefoxIndir).visibility = if (kurulu) View.GONE else View.VISIBLE
    }
    private lateinit var cbMenu: CheckBox
    private lateinit var sbTop: SeekBar
    private lateinit var sbBottom: SeekBar
    private lateinit var sbSpeed: SeekBar
    private lateinit var tvRegion: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private lateinit var tvModels: TextView
    private lateinit var boxAdvanced: android.widget.LinearLayout
    private lateinit var tvAdvancedHeader: TextView
    private lateinit var btnToggle: Button
    private lateinit var tvCheck: TextView

    // Altyazı görünümü
    private lateinit var sbSubSize: SeekBar
    private lateinit var tvSubSize: TextView
    private lateinit var cgSubColor: ChipGroup
    private lateinit var cgSubBg: ChipGroup
    private lateinit var cgSubBgColor: ChipGroup
    private lateinit var tvPreview: TextView
    private lateinit var rgSubPos: RadioGroup
    private lateinit var cbOutline: CheckBox

    private lateinit var projectionLauncher: ActivityResultLauncher<Intent>

    /** Telefondaki yapay zekâ modeli kartı (indir / dosyadan seç). */
    private var modelSetup: ModelSetup? = null
    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) modelSetup?.copyFrom(uri)
    }
    private val pickOpusPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            findViewById<TextView>(R.id.tvOpusStatus).text = "OPUS-MT modeli içe aktarılıyor..."
            Thread {
                val error = OpusModelPack.import(applicationContext, uri)
                runOnUiThread {
                    findViewById<TextView>(R.id.tvOpusStatus).text = if (error == null)
                        "OPUS-MT paketi yüklendi. 11 replik karşılaştırmasında hızını ölçün."
                    else "OPUS-MT yüklenemedi: $error"
                    if (error == null) findViewById<CheckBox>(R.id.cbOpus).isChecked = true
                }
            }.start()
        }
    }

    /** Arayüz kurulduysa true. Kurulamadıysa güvenli ekrandayız. */
    private var uiReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
            uiReady = true
        } catch (e: Throwable) {
            CrashLog.note(this, "acilis ekrani", e)
            showSafeScreen()
        }
        // Animasyonlu giriş: yalnızca uygulama ilk açılırken (ekran dönünce tekrar oynamasın).
        if (savedInstanceState == null) {
            try { GirisAnimasyonu.goster(this) } catch (_: Throwable) {}
        }
    }

    /** Arayüz kurulamazsa uygulama kapanmasın; hatayı ekranda göster. */
    private fun showSafeScreen() {
        val info = TextView(this).apply {
            setPadding(40, 60, 40, 40)
            textSize = 13f
            text = "Uygulama açılırken bir sorun çıktı.\n\n" +
                (CrashLog.read(this@MainActivity) ?: "Ayrıntı kaydedilemedi.") +
                "\n\nBu yazıyı kopyalayıp sohbete yapıştırın."
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(info) }
        setContentView(scroll)
    }

    private fun buildUi() {
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)

        etGame = findViewById(R.id.etGame)
        spType = findViewById(R.id.spType)
        spType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, TURLER
        )
        spType.setSelection(TURLER.indexOf(prefs.contentType).coerceAtLeast(0))
        etKey = findViewById(R.id.etKey)
        etGeminiKey = findViewById(R.id.etGeminiKey)
        rgProvider = findViewById(R.id.rgProvider)
        etModel = findViewById(R.id.etModel)
        rgMode = findViewById(R.id.rgMode)
        cbSpeaker = findViewById(R.id.cbSpeaker)
        cbFirefox = findViewById(R.id.cbFirefox)
        cbGoogle = findViewById(R.id.cbGoogle)
        cbOpus = findViewById(R.id.cbOpus)
        cbDeepL = findViewById(R.id.cbDeepL)
        etDeepLKey = findViewById(R.id.etDeepLKey)
        etDeepLKey.setText(DeepLCredentials.read(this))
        findViewById<Button>(R.id.btnModelCleanup).setOnClickListener { confirmModelCleanup() }
        findViewById<TextView>(R.id.tvOpusStatus).text = OpusModelPack.status(this)
        findViewById<Button>(R.id.btnOpusImport).setOnClickListener {
            pickOpusPack.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        firefoxDurumYaz()
        findViewById<Button>(R.id.btnFirefoxIndir).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(FirefoxCeviri.INDIRME)))
            } catch (_: Throwable) {
                Toast.makeText(this, FirefoxCeviri.INDIRME, Toast.LENGTH_LONG).show()
            }
        }
        rgBicim = findViewById(R.id.rgBicim)
        cbAccuracy = findViewById(R.id.cbAccuracy)
        cbDiag = findViewById(R.id.cbDiag)
        sbMinShow = findViewById(R.id.sbMinShow)
        sbMinShow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = minShowEtiketi()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { prefs.minShowSec = sbMinShow.progress }
        })
        cbDogruyuBekle = findViewById(R.id.cbDogruyuBekle)
        cbYalnizDogru = findViewById(R.id.cbYalnizDogru)
        cbYigin = findViewById(R.id.cbYigin)
        cbAlwaysAi = findViewById(R.id.cbAlwaysAi)
        cbMenu = findViewById(R.id.cbMenu)
        sbTop = findViewById(R.id.sbTop)
        sbBottom = findViewById(R.id.sbBottom)
        sbSpeed = findViewById(R.id.sbSpeed)
        tvRegion = findViewById(R.id.tvRegion)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvStatus = findViewById(R.id.tvStatus)
        val tvCihaz = TvMode.isTvDevice(this)
        try {
            val box = findViewById<LinearLayout>(R.id.boxModel)
            // TV'de yapay zekâ yok (bellek yetmez): 3,6 GB model indirme kartı gösterilmez.
            if (tvCihaz) box.visibility = View.GONE
            else modelSetup = ModelSetup(this) { pickModel.launch(arrayOf("*/*")) }.also { it.attach(box, 0) }
        } catch (e: Throwable) {
            CrashLog.note(this, "model karti", e)
        }
        if (tvCihaz) {
            try { findViewById<View>(R.id.cbAccuracy)?.visibility = View.GONE } catch (_: Throwable) {}
        }
        try {
            baglaGezinti()
        } catch (e: Throwable) {
            CrashLog.note(this, "gezinti", e)
        }
        findViewById<TextView>(R.id.tvVersion).text =
            "sürüm " + (packageManager.getPackageInfo(packageName, 0).versionName ?: "?")

        etGame.setText(prefs.gameName)
        etKey.setText(prefs.apiKey)
        etGeminiKey.setText(prefs.geminiKey)
        findViewById<RadioButton>(
            if (prefs.provider == Prefs.PROVIDER_ANTHROPIC) R.id.rbAnthropic else R.id.rbGemini
        ).isChecked = true
        // Kutuda, seçili sağlayıcının modeli görünsün.
        etModel.setText(
            if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel else prefs.model
        )
        // 9.91: eski kutu bileşimleri temizlenir, ekranda tek seçim görünür.
        prefs.bicimiOnar()
        findViewById<RadioButton>(
            when (prefs.ceviriBicimi) {
                Prefs.BICIM_HIZLI -> R.id.rbBicimHizli
                Prefs.BICIM_DOGRU -> R.id.rbBicimDogru
                else -> R.id.rbBicimDengeli
            }
        ).isChecked = true
        findViewById<RadioButton>(
            when (prefs.mode) {
                Prefs.MODE_AI -> R.id.rbAi
                Prefs.MODE_HYBRID -> R.id.rbHybrid
                else -> R.id.rbInstant
            }
        ).isChecked = true
        cbSpeaker.isChecked = prefs.showSpeaker
        cbFirefox.isChecked = prefs.firefoxMotoru
        cbGoogle.isChecked = prefs.googleCeviri
        cbOpus.isChecked = prefs.opusMtEnabled
        cbDeepL.isChecked = prefs.deepLEnabled
        cbAccuracy.isChecked = prefs.accuracyFirst
        cbDiag.isChecked = prefs.diagnostics
        sbMinShow.progress = prefs.minShowSec
        minShowEtiketi()
        cbDogruyuBekle.isChecked = prefs.dogruyuBekle
        cbYalnizDogru.isChecked = prefs.yalnizDogru
        cbYigin.isChecked = prefs.yigin
        cbAlwaysAi.isChecked = prefs.alwaysAi
        cbMenu.isChecked = prefs.menuTranslate
        sbTop.progress = prefs.topPct
        sbBottom.progress = prefs.bottomPct
        sbSpeed.progress = (prefs.intervalMs - 100).coerceIn(0, 900)
        updateRegionLabel()
        updateSpeedLabel()

        val regionListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = updateRegionLabel()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        sbTop.setOnSeekBarChangeListener(regionListener)
        sbBottom.setOnSeekBarChangeListener(regionListener)
        // Tek dokunuşla en sık durum: altyazı ekranın en altında (YouTube, çoğu oyun).
        findViewById<Button>(R.id.btnRegionBottom)?.setOnClickListener {
            sbTop.progress = 72
            sbBottom.progress = 95
            updateRegionLabel()
            save()
            Toast.makeText(this, "Altyazı bölgesi: ekranın %72 – %95 arası", Toast.LENGTH_SHORT).show()
        }
        // 9.93: hızlı konumlar — üst bölge ve tam ekran
        findViewById<Button>(R.id.btnRegionTop)?.setOnClickListener {
            sbTop.progress = 4
            sbBottom.progress = 28
            updateRegionLabel()
            save()
            Toast.makeText(this, "Altyazı bölgesi: ekranın üstü (%4 – %28)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnRegionFull)?.setOnClickListener {
            sbTop.progress = 0
            sbBottom.progress = 100
            updateRegionLabel()
            save()
            Toast.makeText(this, "Altyazı bölgesi: tam ekran (daha yavaş okur)", Toast.LENGTH_SHORT).show()
        }

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = updateSpeedLabel()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ------------------------------------------- altyazı görünümü
        sbSubSize = findViewById(R.id.sbSubSize)
        tvSubSize = findViewById(R.id.tvSubSize)
        cgSubColor = findViewById(R.id.cgSubColor)
        cgSubBg = findViewById(R.id.cgSubBg)
        cgSubBgColor = findViewById(R.id.cgSubBgColor)
        tvPreview = findViewById(R.id.tvPreview)
        rgSubPos = findViewById(R.id.rgSubPos)
        cbOutline = findViewById(R.id.cbOutline)

        sbSubSize.progress = (prefs.subtitleSize - SUB_SIZE_MIN).coerceIn(0, sbSubSize.max)
        updateSubSizeLabel()
        sbSubSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) =
                updateSubSizeLabel()

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Chip>(
            when (prefs.subtitleColor) {
                Prefs.COLOR_YELLOW -> R.id.chColYellow
                Prefs.COLOR_GREEN -> R.id.chColGreen
                Prefs.COLOR_CYAN -> R.id.chColCyan
                else -> R.id.chColWhite
            }
        ).isChecked = true

        findViewById<Chip>(
            when (prefs.subtitleBg) {
                Prefs.BG_SOLID -> R.id.chBgSolid
                Prefs.BG_NONE -> R.id.chBgNone
                else -> R.id.chBgSemi
            }
        ).isChecked = true

        findViewById<Chip>(
            when (prefs.subtitleBgColor) {
                Prefs.BGC_GRAY -> R.id.chBgcGray
                Prefs.BGC_NAVY -> R.id.chBgcNavy
                else -> R.id.chBgcBlack
            }
        ).isChecked = true

        // Ayar her değiştiğinde önizleme de değişsin.
        val onizlemeDinleyici = ChipGroup.OnCheckedStateChangeListener { _, _ ->
            onizlemeyiGuncelle()
        }
        cgSubColor.setOnCheckedStateChangeListener(onizlemeDinleyici)
        cgSubBg.setOnCheckedStateChangeListener(onizlemeDinleyici)
        cgSubBgColor.setOnCheckedStateChangeListener(onizlemeDinleyici)

        prefs.subtitlePos = Prefs.POS_BOTTOM
        findViewById<RadioButton>(R.id.rbPosBottom).isChecked = true
        listOf(R.id.rbPosCover, R.id.rbPosBelow, R.id.rbPosAbove).forEach {
            findViewById<RadioButton>(it).visibility = android.view.View.GONE
        }


        cbOutline.isChecked = prefs.subtitleOutline
        cbOutline.setOnCheckedChangeListener { _, _ -> onizlemeyiGuncelle() }
        onizlemeyiGuncelle()

        projectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_CODE, result.resultCode)
                    putExtra(CaptureService.EXTRA_DATA, data)
                }
                ContextCompat.startForegroundService(this, intent)
                showRunning(true)
                moveTaskToBack(true)
            } else {
                tvStatus.text = "Ekran yakalama izni verilmedi."
            }
        }

        tvError = findViewById(R.id.tvError)
        tvModels = findViewById(R.id.tvModels)
        boxAdvanced = findViewById(R.id.boxAdvanced)
        tvAdvancedHeader = findViewById(R.id.tvAdvancedHeader)
        btnToggle = findViewById(R.id.btnToggle)
        tvCheck = findViewById(R.id.tvCheck)
        findViewById<Button>(R.id.btnCheck).setOnClickListener { runSetupCheck() }
        findViewById<Button>(R.id.btnRegion).setOnClickListener {
            startActivity(Intent(this, RegionActivity::class.java))
        }

        tvAdvancedHeader.setOnClickListener {
            val open = boxAdvanced.visibility == View.VISIBLE
            boxAdvanced.visibility = if (open) View.GONE else View.VISIBLE
            tvAdvancedHeader.text = if (open) "Gelişmiş ▸" else "Gelişmiş ▾"
        }

        findViewById<Button>(R.id.btnFindModels).setOnClickListener { findModels() }
        findViewById<Button>(R.id.btnCopyError).setOnClickListener { copyError() }
        findViewById<Button>(R.id.btnClearError)?.setOnClickListener {
            CrashLog.clear(this)
            tvError.text = "Kayıtlı hata yok."
            Toast.makeText(this, "Son hata silindi.", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCopyLog).setOnClickListener { copyLog() }
        findViewById<Button>(R.id.btnClearLog)?.setOnClickListener {
            CaptureService.clearLog()
            tvError.text = ""
            Toast.makeText(
                this,
                "Kayıt temizlendi. Şimdi oyuna dönüp tek bir sahne oynayın, sonra kaydı kopyalayın.",
                Toast.LENGTH_LONG,
            ).show()
        }
        findViewById<Button>(R.id.btnPreview)?.setOnClickListener { onizlemeGoster() }

        tvSecimiBagla()

        btnToggle.setOnClickListener {
            save()
            if (CaptureService.running) {
                startService(
                    Intent(this, CaptureService::class.java)
                        .setAction(CaptureService.ACTION_STOP)
                )
                showRunning(false)
            } else {
                if (EngineBenchmark1035.running) {
                    Toast.makeText(this, "Model testi sürüyor. Önce testi bitirin veya iptal edin.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                start()
            }
        }

        showRunning(CaptureService.running)
    }

    /**
     * Kurulum kontrolü: üç şeyi sırayla dener ve sonucu yazar.
     * Amaç, sorunun oyun sırasında değil burada ortaya çıkması.
     */
    private fun runSetupCheck() {
        save()
        tvCheck.text = "Kontrol ediliyor…"
        val prefsLocal = prefs

        Thread {
            val report = StringBuilder()

            // 1) Ekran üstüne çizme izni
            report.append(
                if (Settings.canDrawOverlays(this)) "✓ Ekran üstüne çizme izni var\n"
                else "✗ Ekran üstüne çizme izni yok — BAŞLAT'a basınca isteyecek\n"
            )

            // 2) Telefon içi Türkçe model
            val fastEngine = FastTranslate()
            val fastOk = kotlinx.coroutines.runBlocking {
                fastEngine.prepare() && !fastEngine.translate("Hello there").isNullOrBlank()
            }
            report.append(
                if (fastOk) "✓ İnternetsiz Türkçe model hazır\n"
                else "✗ İnternetsiz model inmedi (" + (fastEngine.lastError ?: "sebep yok") + ")\n"
            )
            fastEngine.close()

            // 3) Yapay zekâ çevirisi
            if (prefsLocal.activeKey.isBlank()) {
                report.append("• API anahtarı girilmemiş — yapay zekâ modu çalışmaz\n")
            } else {
                val engine = AiTranslate(prefsLocal)
                var result = kotlinx.coroutines.runBlocking { engine.translate("He is waiting outside.") }
                if (result.isNullOrBlank()) {
                    // Model adı sorunluysa hesaptaki gerçek modeli seç ve tekrar dene.
                    val best = engine.pickBest(engine.fetchModels(prefsLocal.activeKey))
                    if (best != null) {
                        if (prefsLocal.provider == Prefs.PROVIDER_GEMINI) prefsLocal.geminiModel = best
                        else prefsLocal.model = best
                        result = kotlinx.coroutines.runBlocking { engine.translate("He is waiting outside.") }
                    }
                }
                report.append(
                    if (!result.isNullOrBlank()) "✓ Yapay zekâ çevirisi çalışıyor (" + (if (prefsLocal.provider == Prefs.PROVIDER_GEMINI) prefsLocal.geminiModel else prefsLocal.model) + ")\n"
                    else "✗ Yapay zekâ çevirisi başarısız: " + (engine.lastError ?: "bilinmeyen") + "\n"
                )
            }

            runOnUiThread {
                tvCheck.text = report.toString().trim()
                etModel.setText(if (prefsLocal.provider == Prefs.PROVIDER_GEMINI) prefsLocal.geminiModel else prefsLocal.model)
            }
        }.start()
    }

    /** Yanlışlıkla yapıştırılan metinlerin anahtar sanılmasını engeller. */
    private fun cleanKey(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        // Birden fazla kelime veya Türkçe karakter varsa bu bir anahtar değildir.
        if (t.contains(" ") || t.any { it.code < 33 || it.code > 126 }) return ""
        return t
    }

    private fun secModel(ad: String) {
        if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel = ad else prefs.model = ad
        etModel.setText(ad)
    }

    /** Bulunan modelleri listeler; istediğinizi seçebilirsiniz. */
    private fun modelListesiniGoster(models: List<String>) {
        if (models.isEmpty()) return
        val dizi = models.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Model seçin")
            .setItems(dizi) { _, hangi -> secModel(dizi[hangi]) }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showRunning(running: Boolean) {
        btnToggle.text = if (running) "DURDUR" else "ÇEVİRİYİ BAŞLAT"
        tvStatus.text = if (running) {
            "Çalışıyor · Chiaki'yi açıp oyuna girebilirsiniz"
        } else {
            "Durduruldu"
        }
    }

    override fun onResume() {
        super.onResume()
        try { firefoxDurumYaz() } catch (_: Throwable) {}
        visible = true
        if (!uiReady) return
        try {
            TvMode.autoUpdate(this, prefs)
            tvSecimiGoster()
        } catch (_: Throwable) {
        }
        // Çeviri alanı ekranında (RegionActivity) kaydedilen yeni bölgeyi çubuklara da yansıt;
        // yoksa buradan çıkarken eski çubuk değerleri yeni alanın üstüne yazılıyordu.
        try {
            sbTop.progress = prefs.topPct
            sbBottom.progress = prefs.bottomPct
            updateRegionLabel()
        } catch (_: Throwable) {
        }
        try {
            showRunning(CaptureService.running)
            modelSetup?.onResume()
            durumuGuncelle()
            tvModels.text = AiTranslate.summary()
            showError()
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        modelSetup?.onDestroy()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        visible = false
        if (!uiReady) return
        modelSetup?.onPause()
        ui.removeCallbacksAndMessages(null)
        try {
            save()
        } catch (_: Throwable) {
        }
    }

    /** Hesaptaki gerçek model adlarını bulur ve en hızlısını seçer. */
    private fun findModels() {
        save()
        val key = prefs.activeKey
        if (key.isBlank()) {
            tvModels.text = if (prefs.provider == Prefs.PROVIDER_GEMINI)
                "Google AI Studio anahtarı girilmemiş ya da hatalı. Kutuyu temizleyip " +
                    "AIza ile başlayan anahtarı yapıştırın."
            else "Anthropic API anahtarı girilmemiş ya da hatalı."
            return
        }
        tvModels.text = "Modeller aranıyor..."
        Thread {
            val engine = AiTranslate(prefs)
            val models = engine.fetchModels(key)
            val best = engine.pickBest(models)
            runOnUiThread {
                if (models.isEmpty()) {
                    tvModels.text = "Model listesi alınamadı: " + (engine.lastError ?: "bilinmeyen hata")
                    return@runOnUiThread
                }
                if (best != null) {
                    secModel(best)
                }
                modelListesiniGoster(models)
                tvModels.text = "Seçilen: " + (best ?: "-") +
                    "\n\nHesabınızdaki modeller:\n" + models.joinToString("\n")
            }
        }.start()
    }

    private fun showError() {
        val text = CrashLog.read(this)
        if (text.isNullOrBlank()) {
            tvError.text = "Kayıtlı hata yok."
        } else {
            tvError.text = text
        }
    }

    /** Çalışma kaydı: ekrandan ne okundu, ne çevrildi, hata var mı. */
    private fun copyLog() {
        val text = AiTranslate.summary() + "\n\n" + CaptureService.dumpLog()
        tvError.text = text
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Lingo kaydı", text))
        Toast.makeText(this, "Çalışma kaydı kopyalandı, sohbete yapıştırın.", Toast.LENGTH_LONG).show()
    }

    private fun copyError() {
        val text = CrashLog.read(this)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Kayıtlı hata yok.", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Lingo hata", text))
        Toast.makeText(this, "Hata kopyalandı, sohbete yapıştırabilirsiniz.", Toast.LENGTH_LONG).show()
    }

    /** "Çeviri ekranda en az: N sn" etiketini günceller. */
    private fun minShowEtiketi() {
        val sn = sbMinShow.progress.coerceIn(1, 10)
        findViewById<TextView>(R.id.tvMinShow)?.text =
            "Çeviri ekranda en az: $sn sn · uzun satırlar kendiliğinden daha uzun durur"
    }

    private fun updateRegionLabel() {
        tvRegion.text = "Altyazının bulunduğu bölge: ekranın %${sbTop.progress} – %${sbBottom.progress} arası"
        // Her çubuğun yanında da yüzdesi görünsün (TV'de kumandayla ayarlarken kolay olsun).
        findViewById<TextView>(R.id.tvTopPct)?.text = "Üst sınır: %${sbTop.progress}"
        findViewById<TextView>(R.id.tvBottomPct)?.text = "Alt sınır: %${sbBottom.progress}"
    }

    private fun updateSubSizeLabel() {
        tvSubSize.text = "Yazı boyutu: ${sbSubSize.progress + SUB_SIZE_MIN}"
        onizlemeyiGuncelle()
    }

    /**
     * Önizleme kutusunu seçili ayarlara göre boyar.
     * Renkleri servisle aynı yerden (SubtitleStyle) okuyor; böylece burada
     * gördüğünüz şey oyunda göreceğinizin aynısı oluyor.
     */
    private fun onizlemeyiGuncelle() {
        if (!::tvPreview.isInitialized) return

        val renkSecim = when (cgSubColor.checkedChipId) {
            R.id.chColYellow -> Prefs.COLOR_YELLOW
            R.id.chColGreen -> Prefs.COLOR_GREEN
            R.id.chColCyan -> Prefs.COLOR_CYAN
            else -> Prefs.COLOR_WHITE
        }
        val arkaSecim = when (cgSubBg.checkedChipId) {
            R.id.chBgSolid -> Prefs.BG_SOLID
            R.id.chBgNone -> Prefs.BG_NONE
            else -> Prefs.BG_SEMI
        }
        val arkaRenkSecim = when (cgSubBgColor.checkedChipId) {
            R.id.chBgcGray -> Prefs.BGC_GRAY
            R.id.chBgcNavy -> Prefs.BGC_NAVY
            else -> Prefs.BGC_BLACK
        }

        val alfa = SubtitleStyle.arkaAlfa(arkaSecim)
        val arka = SubtitleStyle.arkaRenk(arkaRenkSecim)

        tvPreview.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            (sbSubSize.progress + SUB_SIZE_MIN).toFloat()
        )
        tvPreview.setTextColor(SubtitleStyle.yaziRengi(renkSecim))

        if (cbOutline.isChecked || alfa == 0) {
            tvPreview.setShadowLayer(7f, 0f, 2f, android.graphics.Color.BLACK)
        } else {
            tvPreview.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
        }

        val yogunluk = resources.displayMetrics.density
        tvPreview.setPadding(
            (10 * yogunluk).toInt(), (5 * yogunluk).toInt(),
            (10 * yogunluk).toInt(), (5 * yogunluk).toInt()
        )
        tvPreview.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12 * yogunluk
            setColor(
                android.graphics.Color.argb(
                    alfa,
                    android.graphics.Color.red(arka),
                    android.graphics.Color.green(arka),
                    android.graphics.Color.blue(arka),
                )
            )
        }
    }

    private fun updateSpeedLabel() {
        tvSpeed.text = "Her ${sbSpeed.progress + 100} ms'de bir tara (küçük = daha hızlı tepki, daha çok pil)"
    }

    /** Delete one downloaded model only after explicit confirmation and with no active engines. */
    private fun confirmModelCleanup() {
        if (CaptureService.running || EngineBenchmark1035.running || LlmTranslate.isLoading() || OpusModelPack.isImporting) {
            Toast.makeText(this, "Önce canlı çeviriyi ve model testini durdurun.", Toast.LENGTH_LONG).show()
            return
        }
        val models = mutableListOf<Pair<String, () -> Boolean>>()
        if (OpusModelPack.directory(this).exists()) {
            models.add("OPUS-MT EN-TR (ONNX)" to {
                val deleted = OpusModelPack.deleteInstalled(this)
                if (deleted) { prefs.opusMtEnabled = false; cbOpus.isChecked = false }
                deleted
            })
        }
        for (spec in listOf(LlmTranslate.E2B, LlmTranslate.E4B)) {
            val file = LlmTranslate.fileOf(this, spec)
            if (file.exists()) models.add("${spec.label} (${file.length() / (1024 * 1024)} MB)" to {
                file.delete()
            })
        }
        if (models.isEmpty()) {
            Toast.makeText(this, "Temizlenecek yüklü model yok.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = models.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Hangi modeli kaldırmak istiyorsun?")
            .setItems(names) { _, chosen ->
                AlertDialog.Builder(this)
                    .setTitle("Modeli kalıcı olarak sil")
                    .setMessage("${names[chosen]} silinecek. Yeniden kullanmak için model dosyasını tekrar yüklemen gerekir. Çeviri hafızası korunur.")
                    .setNegativeButton("İPTAL", null)
                    .setPositiveButton("MODELİ SİL") { _, _ ->
                        if (CaptureService.running || EngineBenchmark1035.running || LlmTranslate.isLoading() || OpusModelPack.isImporting) {
                            Toast.makeText(this, "Çeviri/test çalışıyor; model silinmedi.", Toast.LENGTH_LONG).show()
                        } else {
                            val ok = models[chosen].second.invoke()
                            findViewById<TextView>(R.id.tvOpusStatus).text = OpusModelPack.status(this)
                            modelSetup?.onResume()
                            Toast.makeText(this, if (ok) "Seçilen model silindi" else "Model silinemedi", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }.setNegativeButton("KAPAT", null).show()
    }

    private fun save() {
        prefs.gameName = etGame.text.toString()
        prefs.contentType = TURLER.getOrElse(spType.selectedItemPosition) { "Oyun" }
        prefs.apiKey = cleanKey(etKey.text.toString())
        prefs.geminiKey = cleanKey(etGeminiKey.text.toString())
        prefs.provider = if (rgProvider.checkedRadioButtonId == R.id.rbAnthropic)
            Prefs.PROVIDER_ANTHROPIC else Prefs.PROVIDER_GEMINI
        // Model kutusu hangi sağlayıcı seçiliyse ONUN alanına yazılmalı.
        // Önceden hep prefs.model'e (Anthropic alanı) yazıyordu; bu yüzden
        // Gemini seçiliyken model elle hiç değiştirilemiyordu ve MODELLERİ
        // BUL ile doğru ad bulunsa bile uygulamaya geçmiyordu.
        val secilenModel = etModel.text.toString().trim()
        if (secilenModel.isNotEmpty()) {
            if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel = secilenModel
            else prefs.model = secilenModel
        }
        AiTranslate.devreyiAc()

        // Altyazı görünümü
        prefs.subtitleSize = sbSubSize.progress + SUB_SIZE_MIN
        prefs.subtitleColor = when (cgSubColor.checkedChipId) {
            R.id.chColYellow -> Prefs.COLOR_YELLOW
            R.id.chColGreen -> Prefs.COLOR_GREEN
            R.id.chColCyan -> Prefs.COLOR_CYAN
            else -> Prefs.COLOR_WHITE
        }
        prefs.subtitleBg = when (cgSubBg.checkedChipId) {
            R.id.chBgSolid -> Prefs.BG_SOLID
            R.id.chBgNone -> Prefs.BG_NONE
            else -> Prefs.BG_SEMI
        }
        prefs.subtitleBgColor = when (cgSubBgColor.checkedChipId) {
            R.id.chBgcGray -> Prefs.BGC_GRAY
            R.id.chBgcNavy -> Prefs.BGC_NAVY
            else -> Prefs.BGC_BLACK
        }
        prefs.subtitlePos = when (rgSubPos.checkedRadioButtonId) {
            R.id.rbPosCover -> Prefs.POS_COVER
            R.id.rbPosBelow -> Prefs.POS_BELOW
            R.id.rbPosBottom -> Prefs.POS_BOTTOM
            else -> Prefs.POS_ABOVE
        }
        prefs.subtitleOutline = cbOutline.isChecked
        prefs.mode = when (rgMode.checkedRadioButtonId) {
            R.id.rbAi -> Prefs.MODE_AI
            R.id.rbHybrid -> Prefs.MODE_HYBRID
            else -> Prefs.MODE_INSTANT
        }
        prefs.showSpeaker = cbSpeaker.isChecked
        prefs.firefoxMotoru = cbFirefox.isChecked
        prefs.googleCeviri = cbGoogle.isChecked
        prefs.opusMtEnabled = cbOpus.isChecked && OpusModelPack.isInstalled(this)
        // Never store the API key in a plain preference or ZIP. Refuse activation if encryption fails.
        val deeplKey = etDeepLKey.text.toString().trim()
        if (deeplKey != DeepLCredentials.read(this) && !DeepLCredentials.save(this, deeplKey)) {
            cbDeepL.isChecked = false
            Toast.makeText(this, "DeepL anahtarı güvenli kaydedilemedi; DeepL kapatıldı", Toast.LENGTH_LONG).show()
        }
        prefs.deepLEnabled = cbDeepL.isChecked && DeepLCredentials.read(this).isNotBlank()
        // 9.91: gizli beş kutunun değerini ÇEVİRİ BİÇİMİ belirler. Kutular
        // ekranda yok; aşağıdaki satırlar onları eskisi gibi kaydeder.
        val bicim = when (rgBicim.checkedRadioButtonId) {
            R.id.rbBicimHizli -> Prefs.BICIM_HIZLI
            R.id.rbBicimDogru -> Prefs.BICIM_DOGRU
            else -> Prefs.BICIM_DENGELI
        }
        prefs.ceviriBicimi = bicim
        cbAccuracy.isChecked = true
        cbDogruyuBekle.isChecked = false
        cbYalnizDogru.isChecked = bicim == Prefs.BICIM_DOGRU
        cbYigin.isChecked = false
        cbAlwaysAi.isChecked = false
        prefs.accuracyFirst = cbAccuracy.isChecked
        prefs.diagnostics = cbDiag.isChecked
        prefs.minShowSec = sbMinShow.progress
        prefs.dogruyuBekle = cbDogruyuBekle.isChecked
        prefs.yalnizDogru = cbYalnizDogru.isChecked
        prefs.yigin = cbYigin.isChecked
        prefs.alwaysAi = cbAlwaysAi.isChecked
        prefs.menuTranslate = cbMenu.isChecked
        prefs.intervalMs = sbSpeed.progress + 100

        var top = sbTop.progress
        var bottom = sbBottom.progress
        if (bottom <= top) {
            bottom = (top + 10).coerceAtMost(100)
            sbBottom.progress = bottom
        }
        if (top >= bottom) {
            top = (bottom - 10).coerceAtLeast(0)
            sbTop.progress = top
        }
        prefs.topPct = top
        prefs.bottomPct = bottom
    }

    private fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
            tvStatus.text = "Bildirim iznini verin, sonra tekrar BAŞLAT deyin."
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Önce 'diğer uygulamaların üzerinde göster' iznini verin.", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                tvStatus.text = "İzni verdikten sonra geri gelip tekrar BAŞLAT deyin."
            } catch (e: Throwable) {
                // Çoğu Android TV'de bu ayar ekranı yok: izni telefondan komutla açma yolunu göster.
                tvStatus.text = "Bu cihazda izin ekranı yok. TV kılavuzu'ndaki 'İlk kurulum' adımlarını uygula."
                tvKilavuzu()
            }
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Altta altyazı · tek uygulama yakalama")
                .setMessage("Sonraki Android ekranında Tek uygulama seçip oyunu veya video uygulamasını seçin. Tüm ekran seçilirse Türkçe kutu da yakalanabilir ve İngilizceyi örter. GameLingo uygulamasını seçmeyin.")
                .setPositiveButton("Uygulamayı seç") { _, _ ->
                    projectionLauncher.launch(mpm.createScreenCaptureIntent())
                }
                .setNegativeButton("Vazgeç", null).show()
        } catch (e: Throwable) {
            CrashLog.note(this, "ekran kaydi izni", e)
            tvStatus.text = "Ekran kaydı bu cihazda yok; üretici bu özelliği kapatmış."
        }
    }

    // ------------------------------------------------ GameLingo: gezinti ve pencereler

    private val ui = Handler(Looper.getMainLooper())

    private fun dpx(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Kısayol kutuları ve alt menü. */
    private fun baglaGezinti() {
        val scroll = findViewById<ScrollView>(R.id.scroll)
        fun git(id: Int) {
            val v = findViewById<View>(id) ?: return
            scroll.post { scroll.smoothScrollTo(0, (v.top - dpx(8)).coerceAtLeast(0)) }
        }
        findViewById<View>(R.id.tileRegion).setOnClickListener { git(R.id.secRegion) }
        findViewById<View>(R.id.tileAi).setOnClickListener { git(R.id.secAi) }
        findViewById<View>(R.id.tileLook).setOnClickListener { git(R.id.secLook) }
        findViewById<View>(R.id.tileDict).setOnClickListener { sozlukPenceresi(null, null) }
        findViewById<View>(R.id.navHome).setOnClickListener { scroll.post { scroll.smoothScrollTo(0, 0) } }
        findViewById<View>(R.id.navSettings).setOnClickListener { git(R.id.secSettings) }
        findViewById<View>(R.id.navHistory).setOnClickListener { gecmisPenceresi() }
        findViewById<View>(R.id.navHelp).setOnClickListener { yardimPenceresi() }
        UserDict.apply(this)
    }

    /** Durum satırındaki göstergeler ve "Son çevrilen" kutusu; ekran açıkken saniyede bir yenilenir. */
    private fun durumuGuncelle() {
        ui.removeCallbacksAndMessages(null)
        try {
            val ok = ContextCompat.getColor(this, R.color.lingo_ok)
            val warn = ContextCompat.getColor(this, R.color.lingo_warn)
            val bad = ContextCompat.getColor(this, R.color.lingo_bad)
            val sb = SpannableStringBuilder()
            fun nokta(renk: Int, yazi: String) {
                val st = sb.length
                sb.append("●")
                sb.setSpan(ForegroundColorSpan(renk), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(" ").append(yazi).append("    ")
            }
            nokta(if (CaptureService.running) ok else warn, if (CaptureService.running) "Okuma açık" else "Okuma kapalı")
            nokta(ok, "Çeviri hazır")
            when {
                LlmTranslate.isReady(this) -> nokta(ok, "Yapay zekâ hazır")
                LlmTranslate.isLoading() -> nokta(warn, "Yapay zekâ yükleniyor")
                LlmTranslate.hasModel(this) -> nokta(warn, "Yapay zekâ bekliyor")
                else -> nokta(bad, "Yapay zekâ modeli yok")
            }
            findViewById<TextView>(R.id.tvIndicators).text = sb

            CaptureService.lastPair()?.let { (en, tr) ->
                findViewById<TextView>(R.id.tvLastSrc).text = en
                findViewById<TextView>(R.id.tvLastTr).text = tr
            }
            // 9.93 — canlı süreler: GERÇEK ölçüm, örnek veri değil.
            val ocrMs = CaptureService.sonOcrMs
            val ceviriMs = CaptureService.sonCeviriMs
            if (ocrMs > 0L) findViewById<TextView>(R.id.tvStatOcr).text = "$ocrMs ms"
            if (ceviriMs > 0L) findViewById<TextView>(R.id.tvStatCeviri).text = "$ceviriMs ms"
            if (ocrMs > 0L && ceviriMs > 0L) {
                findViewById<TextView>(R.id.tvStatGecikme).text = "${ocrMs + ceviriMs} ms"
            }
            kopruGoster()
        } catch (_: Throwable) {
        }
        ui.postDelayed({ durumuGuncelle() }, 1000)
    }

    /** "Sözlüğüm": kendi düzeltmelerini yazdığın pencere. İngilizce/Türkçe verilirse o satırı ekler. */
    private fun sozlukPenceresi(en: String?, tr: String?) {
        if (en != null && tr != null) {
            val pad = dpx(20)
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dpx(8), pad, 0) }
            val etEn = EditText(this).apply { setText(en); hint = "İngilizce" }
            val etTr = EditText(this).apply { setText(tr); hint = "Doğru Türkçesi" }
            box.addView(etEn); box.addView(etTr)
            AlertDialog.Builder(this)
                .setTitle("Sözlüğe ekle")
                .setMessage("Türkçeyi düzeltip kaydet. Bu satır bir daha hep böyle çevrilir.")
                .setView(box)
                .setPositiveButton("Kaydet") { _, _ ->
                    val e = etEn.text.toString().trim()
                    val t = etTr.text.toString().trim()
                    if (e.isNotEmpty() && t.isNotEmpty()) {
                        UserDict.add(this, e, t)
                        Toast.makeText(this, "Sözlüğe eklendi", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Vazgeç", null)
                .show()
            return
        }
        val et = EditText(this).apply {
            val mevcut = UserDict.load(this@MainActivity)
            setText(if (mevcut.isBlank()) UserDict.ORNEK else mevcut)
            minLines = 8
            gravity = android.view.Gravity.TOP
            textSize = 14f
        }
        val wrap = LinearLayout(this).apply {
            setPadding(dpx(20), dpx(8), dpx(20), 0)
            addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Sözlüğüm")
            .setMessage("Her satıra bir düzeltme: ingilizce = türkçe\nÇevrilmemesi gereken isimler: @İsim")
            .setView(wrap)
            .setPositiveButton("Kaydet") { _, _ ->
                UserDict.save(this, et.text.toString())
                Toast.makeText(this, "Sözlük kaydedildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /** Bu oturumda çevrilen satırlar; birine dokununca sözlüğe eklenebilir. */
    private fun gecmisPenceresi() {
        val list = CaptureService.history().asReversed()
        if (list.isEmpty()) {
            Toast.makeText(this, "Henüz çeviri yok. Çeviriyi başlatıp oyuna geçin.", Toast.LENGTH_LONG).show()
            return
        }
        val items = list.map { (en, tr) -> "$en\n→ $tr" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Çeviri geçmişi · yanlış olana dokun")
            .setItems(items) { _, i -> sozlukPenceresi(list[i].first, list[i].second) }
            .setNegativeButton("Kapat", null)
            .show()
    }

    // ------------------------------------------------ Telefon / TV seçimi

    private fun tvSecimiBagla() {
        try {
            findViewById<View>(R.id.btnModePhone).setOnClickListener {
                TvMode.setManual(this, prefs, false); tvSecimiGoster()
            }
            findViewById<View>(R.id.btnModeTv).setOnClickListener {
                TvMode.setManual(this, prefs, true); tvSecimiGoster()
                if (!TvMode.externalDisplayConnected(this)) {
                    Toast.makeText(this, "TV modu açık. Telefonu kabloyla TV'ye bağlayınca altyazı büyük görünecek.", Toast.LENGTH_LONG).show()
                }
            }
            findViewById<View>(R.id.btnTvGuide).setOnClickListener { tvKilavuzu() }
            kopruBagla()
            if (TvMode.isTvDevice(this)) {
                // TV'nin kendisinde seçim yok: her zaman TV görünümü.
                findViewById<View>(R.id.segMode)?.visibility = View.GONE
                kumandaOdagi(window.decorView)
            }
            TvMode.autoUpdate(this, prefs)
            tvSecimiGoster()
        } catch (e: Throwable) {
            CrashLog.note(this, "tv secimi", e)
        }
    }

    // ------------------------------------------------ Telefon ↔ TV köprüsü

    private fun kopruBagla() {
        val tv = TvMode.isTvDevice(this)
        val btnBridge = findViewById<View>(R.id.btnTvBridge)
        val tvBridge = findViewById<View>(R.id.tvBridgeState)
        val btnLink = findViewById<View>(R.id.btnPhoneLink)
        if (tv) {
            // TV: köprüyü SAĞLAYAN telefon tarafı gizli, telefonla eşleşme düğmesi görünür.
            btnBridge?.visibility = View.GONE
            tvBridge?.visibility = View.GONE
            btnLink?.visibility = View.VISIBLE
            btnLink?.setOnClickListener { eslesmePenceresi() }
        } else {
            btnLink?.visibility = View.GONE
            btnBridge?.setOnClickListener {
                if (PhoneBridgeService.running) {
                    startService(Intent(this, PhoneBridgeService::class.java).setAction(PhoneBridgeService.ACTION_STOP))
                } else {
                    if (!LlmTranslate.hasModel(this)) {
                        Toast.makeText(this, "Önce yapay zekâ modelini indir; çeviriyi o yapacak.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    ContextCompat.startForegroundService(this,
                        Intent(this, PhoneBridgeService::class.java).setAction(PhoneBridgeService.ACTION_START))
                    AlertDialog.Builder(this)
                        .setTitle("TV ve bilgisayara çeviri sağlanıyor")
                        .setMessage("GameLingo TV'de \"Telefonun yapay zekâsını kullan\"a, bilgisayarda GameLingo PC → Çeviri → Ayarlar'a gir ve şu kodu yaz:\n\n" +
                            "      ${PhoneBridgeService.pinFor(this)}\n\n" +
                            "Telefon ile TV/bilgisayar aynı ağda olmalı. Telefonu şarja takıp kenara bırakabilirsin; ekran kapalıyken de çalışır.")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
                ui.postDelayed({ kopruGoster() }, 600)
            }
        }
        kopruGoster()
    }

    private fun kopruGoster() {
        try {
            if (TvMode.isTvDevice(this)) {
                findViewById<Button>(R.id.btnPhoneLink)?.text =
                    if (prefs.bridgeOn && prefs.bridgeHost.isNotBlank()) "Telefonun yapay zekâsı: açık · ${prefs.bridgeHost}"
                    else "Telefonun yapay zekâsını kullan (doğru çeviri)"
            } else {
                findViewById<Button>(R.id.btnTvBridge)?.text =
                    if (PhoneBridgeService.running) "Çeviri sağlamayı durdur" else "TV ve bilgisayara çeviri sağla"
                findViewById<TextView>(R.id.tvBridgeState)?.text = "Çeviri köprüsü: " + PhoneBridgeService.statusText()
            }
        } catch (_: Throwable) {
        }
    }

    /** TV: telefonla eşleşme penceresi (kod + istenirse adres). */
    private fun eslesmePenceresi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val info = TextView(this).apply {
            text = "Telefondaki GameLingo'da \"TV ve bilgisayara çeviri sağla\" düğmesine bas. Ekranda çıkan 4 haneli kodu buraya yaz. " +
                "Telefon ile TV/bilgisayar aynı ağda olmalı."
        }
        val code = android.widget.EditText(this).apply {
            hint = "Kod (ör. 4821)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.bridgePin)
        }
        val host = android.widget.EditText(this).apply {
            hint = "Telefonun adresi (boş bırak: kendisi bulur)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.bridgeHost)
        }
        box.addView(info); box.addView(code); box.addView(host)
        AlertDialog.Builder(this)
            .setTitle("Telefonun yapay zekâsını kullan")
            .setView(box)
            .setPositiveButton("Eşleş") { _, _ -> eslestir(code.text.toString().trim(), host.text.toString().trim()) }
            .setNeutralButton("Kullanma") { _, _ ->
                prefs.bridgeOn = false
                kopruGoster()
                Toast.makeText(this, "TV kendi hızlı çevirisini kullanacak.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun eslestir(pin: String, manualHost: String) {
        if (pin.length != 4 || pin.any { !it.isDigit() }) {
            Toast.makeText(this, "Kod 4 haneli olmalı.", Toast.LENGTH_LONG).show()
            return
        }
        tvStatus.text = "Telefon aranıyor…"
        Thread {
            val host = manualHost.ifBlank { PhoneBridge.discover(pin) ?: PhoneBridge.discover(pin) ?: "" }
            val sonuc = if (host.isBlank()) null else PhoneBridge.ping(host, pin)
            runOnUiThread {
                when {
                    host.isBlank() -> {
                        tvStatus.text = "Telefon bulunamadı."
                        AlertDialog.Builder(this)
                            .setTitle("Telefon bulunamadı")
                            .setMessage("Telefonda \"TV ve bilgisayara çeviri sağla\" açık mı ve ikisi aynı Wi-Fi'da mı? " +
                                "Olmazsa telefondaki TV köprüsü satırında yazan adresi (ör. 192.168.1.23) eşleşme penceresindeki adres kutusuna yaz.")
                            .setPositiveButton("Tamam", null).show()
                    }
                    sonuc == null -> {
                        tvStatus.text = "Telefona ulaşılamadı ($host)."
                        Toast.makeText(this, "Telefona ulaşılamadı: kod doğru mu, telefonda köprü açık mı?", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        prefs.bridgePin = pin
                        prefs.bridgeHost = host
                        prefs.bridgeOn = true
                        tvStatus.text = if (sonuc) "Telefona bağlandı · yapay zekâ hazır" else "Telefona bağlandı · yapay zekâ yükleniyor"
                        Toast.makeText(this, "Eşleşti: $host. Çeviriler artık telefondaki yapay zekâdan gelecek.", Toast.LENGTH_LONG).show()
                        kopruGoster()
                    }
                }
            }
        }.start()
    }

    private fun tvSecimiGoster() {
        val tv = prefs.tvMode
        val phone = findViewById<TextView>(R.id.btnModePhone) ?: return
        val tvBtn = findViewById<TextView>(R.id.btnModeTv) ?: return
        val on = ContextCompat.getColor(this, R.color.lingo_on_surface)
        val off = ContextCompat.getColor(this, R.color.lingo_on_surface_variant)
        val accent = ContextCompat.getColor(this, R.color.lingo_primary)
        phone.setBackgroundResource(if (tv) R.drawable.bg_seg_off else R.drawable.bg_seg_on)
        tvBtn.setBackgroundResource(if (tv) R.drawable.bg_seg_on else R.drawable.bg_seg_off)
        phone.setTextColor(if (tv) off else on)
        tvBtn.setTextColor(if (tv) on else off)
        phone.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (tv) off else accent)
        tvBtn.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (tv) accent else off)
        val bagli = TvMode.externalDisplayConnected(this)
        findViewById<TextView>(R.id.tvTvState)?.text = if (TvMode.isTvDevice(this)) "Android TV · çevrim dışı hızlı çeviri" else when {
            tv && bagli -> "TV bağlı · yazı büyük, kutu geniş"
            tv -> "TV modu · kablo bağlanınca büyük görünür"
            bagli -> "TV bağlı ama Telefon seçili · normal boyut"
            else -> "Telefon · normal boyut"
        }
    }

    /**
     * Kumanda (D-pad) ile gezinme: tıklanabilen her öğe odaklanabilsin ve
     * odaktayken belirgin olsun (hafif büyür, turkuaz çerçeve).
     */
    private fun kumandaOdagi(v: View?) {
        if (v == null) return
        if (v.isClickable || v is android.widget.CompoundButton || v is android.widget.SeekBar || v is android.widget.EditText) {
            v.isFocusable = true
            val onceki = v.onFocusChangeListener
            v.setOnFocusChangeListener { view, odak ->
                onceki?.onFocusChange(view, odak)
                view.animate().scaleX(if (odak) 1.04f else 1f).scaleY(if (odak) 1.04f else 1f).setDuration(120).start()
                view.foreground = if (odak) android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 14f * resources.displayMetrics.density
                    setStroke((2 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this@MainActivity, R.color.lingo_primary))
                } else null
            }
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) kumandaOdagi(v.getChildAt(i))
    }

    /** "Ne görüyor?": uygulamanın yakaladığı son görüntü; altyazı bölgesi çerçeveli. */
    private fun onizlemeGoster() {
        val bmp = CaptureService.lastPreview
        if (bmp == null || bmp.isRecycled) {
            AlertDialog.Builder(this)
                .setTitle("Ne görüyor?")
                .setMessage("Henüz görüntü yok. Önce Çeviriyi başlat'a bas, izleyeceğin videoyu birkaç saniye aç, sonra buraya dön.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        val iv = android.widget.ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            setPadding(16, 16, 16, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Ne görüyor? (son yakalanan görüntü)")
            .setView(iv)
            .setMessage("Turkuaz çerçeve okunan altyazı bölgesi. Videonun yerinde siyah görünüyorsa izlediğin uygulama ekran kaydını engelliyor; altyazı çerçevenin dışındaysa Altyazı bölgesi'ni ayarla.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun tvKilavuzu() {
        if (TvMode.isTvDevice(this)) {
            AlertDialog.Builder(this)
                .setTitle(TvMode.TV_GUIDE_TITLE)
                .setMessage(TvMode.tvGuide(packageName))
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(TvMode.GUIDE_TITLE)
            .setMessage(TvMode.GUIDE)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun yardimPenceresi() {
        AlertDialog.Builder(this)
            .setTitle("Nasıl kullanılır")
            .setMessage(
                "1. Yapay zekâ modelini bir kez indir (Wi-Fi).\n" +
                    "2. Ne izlediğini yaz: tür ve adı.\n" +
                    "3. Çeviriyi başlat, ekran kaydına izin ver, oyuna geç.\n" +
                    "4. Altyazı okunmuyorsa Altyazı alanı'ndan bölgeyi seç.\n\n" +
                    "Hızlı/Dengeli modda DeepL etkinse önce DeepL, ardından OPUS-MT ve Google/Firefox denenir. " +
                    "E4B yalnız deneysel Doğru önce modunda çalışır (ölçülen gecikme yaklaşık 3 sn).\n\n" +
                    "TV'de oynamak için üstten TV'yi seç; ayrıntılar TV kılavuzu'nda.\n\n" +
                    "Yanlış çevrilen bir satırı Geçmiş'ten seçip düzeltebilirsin; bir daha hep öyle çevrilir."
            )
            .setPositiveButton("Tamam", null)
            .setNeutralButton("TV kılavuzu") { _, _ -> tvKilavuzu() }
            .show()
    }

    companion object {
        /** GameLingo'nun ana ekranı açık mı (açıkken çeviri durur, kendi yazılarımızı okumayalım). */
        @Volatile var visible = false
    }
}
