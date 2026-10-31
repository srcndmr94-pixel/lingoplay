package com.example.subtitles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.LinkedHashMap

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tvSubtitle: TextView? = null

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var localTranslator: Translator? = null
    private var isLocalTranslatorReady = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var lastExtractedText = ""
    private var lastNormalizedText = ""
    private var hasCloudTranslated = false
    private var isProcessing = false
    private var screenWidth = 1080
    private var screenHeight = 1920

    // Ekran titremesini önlemek için boş kare sayacı (debounce)
    private var blankFrameCount = 0

    // Yalnızca Gemini çevirilerini saklayan, en çok 300 elemanlı LRU önbellek
    private val cloudTranslationCache = object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 300
        }
    }

    // Soğuma ve hata yönetimi: 15s -> 30s -> 60s ... (en çok 5 dk)
    private var cooldownUntil: Long = 0
    private var currentBackoffDelayMs: Long = 15000
    private var isApiKeyFatalError = false
    private var lastToastMessage = ""

    // SharedPreferences ön önbelleği (kare başına I/O engeli)
    private var cachedCropTopRatio = 0.65f
    private var cachedCropHeightRatio = 0.30f

    // Bildirim şeridi tespitinde aranan uygulama adları
    private val notificationAppNames = listOf(
        "messenger", "facebook", "whatsapp", "geoguessr",
        "instagram", "telegram", "youtube", "discord", "gmail", "spotify",
        "primevideo", "netflix", "pydroid", "twitch", "tiktok"
    )

    // Oyun menü/arayüz etiketlerinde geçen kelimeler (harita, dükkân, envanter)
    private val uiLabelWords = setOf(
        "marker", "markers", "legend", "exit", "shop", "map", "inventory", "equip",
        "unequip", "settings", "options", "menu", "remove", "place", "close", "back",
        "save", "load", "quit", "resume", "retry", "respawn", "waypoint", "objective",
        "quest", "craft", "upgrade", "purchase", "sell", "buy", "confirm", "cancel",
        "apply", "reset", "filter", "sort",
        "unassign", "credits", "patch", "notes", "release", "wheel",
        "defaults", "remap", "rebind", "fleet"
    )

    // Tuş şeridinde geçen eylem adları (L3 Lunge / Swim Up / Dive)
    private val buttonActionWords = setOf(
        "swim", "dive", "lunge", "jump", "dodge", "sprint", "crouch", "interact",
        "attack", "block", "parry", "aim", "reload", "climb", "vault", "grapple",
        "mount", "dismount", "loot", "sheathe", "whistle", "revive", "stealth",
        "counter", "grab", "throw", "drink"
    )

    // Türkçeye özgü harfler: zaten Türkçe olan metni yeniden çevirmemek için
    private val turkishChars = "ışğüöçİŞĞÜÖÇ".toSet()
    private val turkishWords = setOf(
        "ve", "bir", "bu", "için", "ile", "çok", "değil", "var", "yok",
        "ama", "gibi", "daha", "kadar", "sen", "ben", "biz", "siz",
        "ne", "nasıl", "neden", "et", "de", "da", "şey", "olarak",
        "sonra", "önce", "her", "şu", "onu", "beni", "seni", "bize"
    )

    // Türkçeye özgü ek kalıpları. "Kurulumu kontrol et" gibi satırlarda ne özel
    // harf ne de liste kelimesi bulunuyordu. Üç harfli -ler / -lar KASTEN yok:
    // "ruler", "butler" gibi İngilizce kelimeler yanlış pozitif üretiyor.
    private val turkishSuffix = Regex(
        "\\w+(mak|mek|yor|dır|dir|dur|dür|acak|ecek|umu|ümü|ını|ini|unu|ünü|" +
            "lık|lik|luk|lük|leri|ları|lara|lere|ların|lerin|larda|lerde)$"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initOverlay()
        initLocalTranslator()
        loadPreferences()
    }

    // Not: yalnızca servis açılışında okunur; kırpma ayarı değişince servis yeniden başlatılmalı
    private fun loadPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        cachedCropTopRatio = prefs.getFloat("crop_top_ratio", 0.65f)
        cachedCropHeightRatio = prefs.getFloat("crop_height_ratio", 0.30f)
    }

    private fun initLocalTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
        localTranslator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder().build()
        localTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                isLocalTranslatorReady = true
            }
            ?.addOnFailureListener {
                isLocalTranslatorReady = false
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode != -1 && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            setupVirtualDisplay()
            startCaptureLoop()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "subtitle_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Altyazı Çeviri Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Canlı Altyazı Çevirici")
            .setContentText("Ekran taranıyor (Oyun & Video)...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_subtitle, null)
        tvSubtitle = overlayView?.findViewById(R.id.tvSubtitle)

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        windowManager?.addView(overlayView, params)
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "UniversalSubtitleCapture",
            screenWidth,
            screenHeight,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun startCaptureLoop() {
        serviceScope.launch {
            while (true) {
                if (!isProcessing) {
                    captureAndProcess()
                }
                delay(300)
            }
        }
    }

    private fun captureAndProcess() {
        val image = imageReader?.acquireLatestImage() ?: return
        isProcessing = true

        serviceScope.launch(Dispatchers.Default) {
            var fullBitmap: Bitmap? = null
            var croppedBitmap: Bitmap? = null

            try {
                fullBitmap = imageToBitmap(image)
                if (fullBitmap != null) {
                    val cropY = (screenHeight * cachedCropTopRatio).toInt().coerceIn(0, screenHeight - 1)
                    // coerceAtLeast(1): oran 0 olduğunda createBitmap sıfır yükseklikte istisna atıyordu
                    val cropHeight = (screenHeight * cachedCropHeightRatio).toInt()
                        .coerceAtLeast(1)
                        .coerceAtMost(screenHeight - cropY)

                    croppedBitmap = Bitmap.createBitmap(fullBitmap, 0, cropY, screenWidth, cropHeight)

                    val f = fullBitmap
                    val c = croppedBitmap ?: run {
                        f.recycle()
                        isProcessing = false
                        return@launch
                    }

                    val inputImage = InputImage.fromBitmap(c, 0)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val cleanedText = cleanOcrText(visionText.text)

                            // SÜZGEÇ 1: ekran çöpü / HUD kontrolü (görünürlük)
                            if (isValidSubtitleText(cleanedText)) {
                                blankFrameCount = 0
                                handleDetectedText(cleanedText)
                            } else {
                                blankFrameCount++
                                // Titremeyi önlemek için 2 ardışık boş kareden sonra temizle
                                if (blankFrameCount >= 2 && lastExtractedText.isNotEmpty()) {
                                    lastExtractedText = ""
                                    lastNormalizedText = ""
                                    hasCloudTranslated = false
                                    updateOverlayText("")
                                }
                            }
                        }
                        .addOnCompleteListener {
                            if (c != f) {
                                c.recycle()
                            }
                            f.recycle()
                            isProcessing = false
                        }
                } else {
                    isProcessing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fullBitmap?.recycle()
                if (croppedBitmap != null && croppedBitmap != fullBitmap) {
                    croppedBitmap.recycle()
                }
                isProcessing = false
            } finally {
                image.close()
            }
        }
    }

    /** OCR'ın her karede farklı şapka taktığı okumaları düzler: Leavé / Leaveè -> Leave */
    private fun aksaniDuzle(s: String): String {
        val ayristirilmis = Normalizer.normalize(s, Normalizer.Form.NFD)
        return ayristirilmis.replace(Regex("\\p{Mn}+"), "")
    }

    /**
     * Noktalamaya yapışmış kelimeleri ayırır; üç nokta dizilerini ve ondalık sayıları korur.
     * Küçük harften sonra gelen nokta gerçek cümle sonu değil, OCR çöpüdür.
     */
    private fun noktalamaAyir(s: String): String {
        var t = s
        // Cümle noktalamasından sonra kalan tek harflik artık (him.o -> him.).
        // Nokta boşluğa çevrilmeden ÖNCE çalışmalı, yoksa "him o" kalır.
        t = Regex("(?<=[.!?])[a-z]\\b").replace(t, "")
        // Üç nokta dizilerini ve ondalıkları geçici işaretle koru
        t = Regex("\\.{2,}").replace(t) { "\u0001".repeat(it.value.length) }
        t = Regex("(?<=\\d)\\.(?=\\d)").replace(t, "\u0002")
        // küçük + nokta/virgül + küçük -> OCR çöpü, boşluğa çevir (fistart.cannot -> fistart cannot)
        t = Regex("(?<=[a-z])[.,](?=[a-z])").replace(t, " ")
        // küçük + nokta/virgül + BÜYÜK -> gerçek cümle sonu, boşluk ekle
        t = Regex("(?<=[a-z])([.,])(?=[A-Z])").replace(t, "$1 ")
        return t.replace("\u0001", ".").replace("\u0002", ".")
    }

    /**
     * Büyük/küçük harf kaymalarını onarır: belongingS -> belongings,
     * COwardice -> Cowardice, yourCowardice -> your Cowardice.
     * 0ur/POur düzeltmelerinden SONRA çağrılmalı, yoksa "POur" -> "Pour" olur.
     */
    private fun harfKaymasiDuzelt(s: String): String {
        var t = s
        // Kelime sonunda yalnız kalan büyük harf
        t = Regex("(?<=[a-z])([A-Z])\\b").replace(t) { it.groupValues[1].lowercase() }
        // BÜYÜK + BÜYÜK + küçük dizisinde ikinciyi küçült
        t = Regex("(?<=[A-Z])([A-Z])(?=[a-z]{2,})").replace(t) { it.groupValues[1].lowercase() }
        // küçük -> BÜYÜK geçişine boşluk koy
        t = Regex("(?<=[a-z])(?=[A-Z])").replace(t, " ")
        return t
    }

    /**
     * Metin zaten Türkçe mi? HAM metin üzerinde çalışmalı: aksaniDuzle şapkaları
     * düzlediği için (ü -> u, ç -> c) kendisinden sonra bu kontrol çalışmaz.
     */
    private fun zatenTurkce(ham: String): Boolean {
        if (ham.count { it in turkishChars } >= 2) return true

        val kelimeler = Regex("[^\\W\\d_]+").findAll(ham.lowercase()).map { it.value }

        return kelimeler.any { it in turkishWords || turkishSuffix.matches(it) }
    }

    /** OCR çıktısını onarır: aksan, noktalama, harf kayması, kesme işareti ve bilinen yazım hataları. */
    private fun cleanOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        // Türkçe bildirim/reklam metnini çevirmeye çalışma (Parmak uçlarında güvenlik)
        if (zatenTurkce(rawText)) return ""

        val unaccented = aksaniDuzle(rawText)
        val punctuated = noktalamaAyir(unaccented)

        val tekSatir = punctuated
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("’", "'")            // kıvrık ' -> düz '
            .replace("‘", "'")
            .replace("“", "")             // yalnızca çift tırnaklar atılır
            .replace("”", "")
            .replace("\"", "")
            .replace("`", "'")
            .replace(Regex("(?<![A-Za-z0-9])0(?=[a-z]{2,})"), "O")   // 0ur -> Our
            .replace(Regex("^[A-Z](?=[A-Z][a-z]{2,})"), "")          // POur -> Our

        return harfKaymasiDuzelt(tekSatir)
            // Yapışık iyelik eki; zamir kurallarından ÖNCE gelmeli
            .replace(Regex("\\b([A-Z][a-z]{2,})'s(?=[a-z]{3,})"), "$1's ")   // Sect'sfistart -> Sect's fistart
            // Zamir + kısaltma: boşluğu kapat (You 're -> You're)
            .replace(Regex("(?i)\\b(you|we|they)\\s*'(re|ve|ll|d)\\b"), "$1'$2")
            .replace(Regex("(?i)\\b(i)\\s*'(m|ve|ll|d)\\b"), "$1'$2")
            .replace(Regex("(?i)\\b(he|she|it|that|there|who|what)\\s*'(s|ll|d|ve)\\b"), "$1'$2")
            // Ek bir sonraki kelimeye yapışmışsa ayır ('s hariç: "that's ecret" riski)
            .replace(Regex("(?i)\\b(you|we|they)\\s*'(re|ve|ll|d)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\b(i)\\s*'(m|ve|ll|d)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\b(he|she|it|that|there|who|what)\\s*'(ll|d|ve)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\byoutre\\b"), "You're")   // kesme işareti t okunmuş
            .replace(Regex("(?i)\\byouive\\b"), "You've")
            .replace(Regex("(?i)\\bIFL\\b"), "If I")
            .replace(Regex("(?i)\\bIfl\\b"), "If I")
            .replace(Regex("\\bl(?=')"), "I")              // l'll, l'm, l've -> I'll, I'm, I've
            .replace(Regex("\\bl\\b"), "I")                // tek başına l her zaman I'dır
            .replace(Regex("(?<=\\bIf\\s)[l1]\\b"), "I")   // If l / If 1 -> If I
            .replace("vw", "w")                            // unvworthy -> unworthy
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Bildirim şeridi mi? İmzası, birden fazla uygulama adını yan yana taşımasıdır.
     * Boşluklar atılarak eşleştirilir, çünkü OCR adları bölüyor: "Face book" -> "facebook".
     */
    private fun isNotificationBanner(text: String): Boolean {
        val sikistirilmis = text.lowercase().replace(Regex("[^a-z]"), "")
        val eslesme = notificationAppNames.count { sikistirilmis.contains(it) }

        // İki veya daha fazla uygulama adı: kesin bildirim şeridi
        if (eslesme >= 2) return true

        // Tek ad: yalnızca cümle değilse ve kısaysa (diyalogda geçen adı elemeyelim)
        if (eslesme == 1) {
            val gorunur = text.count { !it.isWhitespace() }
            val cumleMi = text.any { it in ".!?" }
            return gorunur < 30 && !cumleMi
        }
        return false
    }

    /**
     * Tuş rehberi / HUD talimatı mı?
     * Yalnızca "press"/"hold" kelimesine bakmak "Hold the line!" gibi gerçek replikleri de eler.
     * Bilinen ödün: köşe parantez kuralı ses ve konuşmacı etiketlerini de eler ([Sighs], [Chen]).
     */
    private fun isInputPrompt(text: String): Boolean {
        if (Regex("\\[[^\\]]{1,12}\\]").containsMatchIn(text)) return true
        if (Regex("(?i)\\b(press|hold|tap)\\b.{0,30}\\bto\\b").containsMatchIn(text)) return true
        if (Regex("(?i)[+□○△✕]\\s*(to|and)\\b").containsMatchIn(text)) return true
        // Simge jetonları: (x) (X) (o) ve OCR'ın simgeden ürettiği )) / oo artıkları
        if (Regex("\\(\\s*[xXoO+]\\s*\\)").containsMatchIn(text)) return true
        if (Regex("(?:^|\\s)(?:\\)\\)|oo)(?=\\s)").containsMatchIn(text)) return true
        // Kumanda tuş adları: L1 L2 L3 R1 R2 R3 LB RB LT RT
        if (Regex("(?<![A-Za-z])(?:[LR][123]|LB|RB|LT|RT)(?![A-Za-z])").containsMatchIn(text)) return true
        // PlayStation / Xbox tuş simgeleri
        if (Regex("[✕✖○◯◉△▲□■⊗⊙]").containsMatchIn(text)) return true
        // Parantez içinde tek tuş harfi: (A) (B) (X) (O) (Y) — Xbox A/B/Y de dahil
        if (Regex("\\(\\s*[ABXYOabxyo]\\s*\\)").containsMatchIn(text)) return true
        // Yarım parantezli tuş harfi: "A) Reset", "O) Close", "O)) Release Wheel"
        if (Regex("(?<![A-Za-z])[ABXYOabxyo]\\)+").containsMatchIn(text)) return true
        // Satır başında yalın tuş harfi + menü kelimesi: "A Reset", "O Unassign"
        if (Regex("(?:^|\\s)[ABXYO]\\s+[A-Z][a-z]+").containsMatchIn(text)) return true
        // İki veya daha fazla tuş eylemi yan yana ve cümle noktalaması yok.
        // Noktalama şartı, "I'll swim across at dawn." gibi gerçek replikleri korur.
        if (text.none { it in ".!?…" }) {
            val eylemSayisi = Regex("[A-Za-z]+").findAll(text)
                .count { it.value.lowercase() in buttonActionWords }
            if (eylemSayisi >= 2) return true
        }
        return false
    }

    /**
     * Menü / harita etiketi mi? (Place marker, Remove all markers, Legend, See Shop Exit)
     * İmza: cümle noktalaması yok, en çok dört kelime, içinde bir arayüz kelimesi var.
     * Gerçek diyalog ya noktalama taşır ya da bu kelimeleri içermez.
     */
    private fun isMenuLabel(text: String): Boolean {
        if (text.any { it in ".!?…" }) return false
        val words = Regex("[A-Za-z']+").findAll(text).map { it.value }.toList()
        if (words.isEmpty() || words.size > 4) return false
        return words.any { it.lowercase() in uiLabelWords }
    }

    /**
     * SÜZGEÇ 1 — ekrana basılır mı? HUD, can barı, sayaç, tuş simgesi ve bildirim eler.
     * Oran YALNIZCA görünür karakterler üzerinden hesaplanır; boşluklar paydayı şişirip
     * "HP 87 / MP 42" gibi satırların süzgeci geçmesine yol açıyordu.
     */
    private fun isValidSubtitleText(text: String): Boolean {
        if (text.length < 2) return false

        val visibleCount = text.count { !it.isWhitespace() }
        if (visibleCount == 0) return false

        if (isNotificationBanner(text) || isInputPrompt(text) || isMenuLabel(text)) {
            return false
        }

        // Tek harfli artıkları ("x2", "L1") ele
        val letterCount = text.count { it.isLetter() }
        if (letterCount < 2) return false

        val nonLetterCount = visibleCount - letterCount
        if (nonLetterCount.toDouble() / visibleCount > 0.50) return false

        return true
    }

    /**
     * SÜZGEÇ 2 — Gemini kotası harcanır mı?
     * En az 2 adet 3+ harfli kelime gerekir; "Run!", "Yes." bedava yerel çeviride kalır.
     */
    private fun shouldSendToCloud(text: String): Boolean {
        val meaningfulWords = text.split(" ").filter { word -> word.count { it.isLetter() } >= 3 }
        return meaningfulWords.size >= 2
    }

    private fun normalizeForMatching(text: String): String {
        return aksaniDuzle(text).lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * OCR titremesini aynı metin sayar. Ölçüm: aynı repliğin varyantları 0.00–0.32
     * oranında, gerçekten farklı replikler 0.68–0.88 oranında ayrışıyor.
     * Kısa satırlarda sınır 1'de kalır, yoksa "End this for me" ile "End this for now" birleşir.
     */
    private fun ayniMetinMi(yeni: String, onceki: String): Boolean {
        if (onceki.isEmpty()) return false
        if (yeni == onceki) return true
        val enUzun = maxOf(yeni.length, onceki.length)
        val sinir = if (enUzun >= 20) (enUzun * 0.34).toInt() else 1
        return uzaklikEnFazla(yeni, onceki, sinir)
    }

    /** Levenshtein uzaklığı sınırı aşıyor mu? Erken çıkışlı, tam matris kurmaz. */
    private fun uzaklikEnFazla(a: String, b: String, sinir: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > sinir) return false
        var onceki = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val simdi = IntArray(b.length + 1)
            simdi[0] = i
            var enIyi = i
            for (j in 1..b.length) {
                val bedel = if (a[i - 1] == b[j - 1]) 0 else 1
                simdi[j] = minOf(onceki[j] + 1, simdi[j - 1] + 1, onceki[j - 1] + bedel)
                if (simdi[j] < enIyi) enIyi = simdi[j]
            }
            if (enIyi > sinir) return false
            onceki = simdi
        }
        return onceki[b.length] <= sinir
    }

    private fun handleDetectedText(text: String) {
        val normalized = normalizeForMatching(text)
        if (ayniMetinMi(normalized, lastNormalizedText)) return

        lastExtractedText = text
        lastNormalizedText = normalized
        hasCloudTranslated = false

        // 1. AŞAMA: geçmiş Gemini çevirisi varsa doğrudan kullan
        val cachedCloudTranslation = cloudTranslationCache[normalized]
        if (cachedCloudTranslation != null) {
            hasCloudTranslated = true
            updateOverlayText(cachedCloudTranslation)
            return
        }

        // 2. AŞAMA: yerel ML Kit çevirisini anında ekrana bas (bedava)
        if (isLocalTranslatorReady) {
            localTranslator?.translate(text)
                ?.addOnSuccessListener { localTranslation ->
                    if (lastNormalizedText == normalized && localTranslation.isNotBlank() && !hasCloudTranslated) {
                        updateOverlayText(localTranslation)
                    }
                }
        }

        // 3. AŞAMA: kısa replikler için kota harcamadan yerelde kal
        if (!shouldSendToCloud(text)) {
            return
        }

        // 4. AŞAMA: uzun replikler için Gemini'yi sorgula
        val now = System.currentTimeMillis()
        if (isApiKeyFatalError || now < cooldownUntil) {
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        val modelName = prefs.getString("gemini_model_name", "gemini-flash-latest") ?: "gemini-flash-latest"

        serviceScope.launch {
            val result = AiTranslate.translateWithGemini(text, apiKey, modelName)

            if (result.isSuccess) {
                currentBackoffDelayMs = 15000
                val fullTranslation = result.getOrDefault("")
                if (fullTranslation.isNotEmpty()) {
                    // Önbelleğe HER ZAMAN yaz: geç gelen yanıt ekrana basılmasa da
                    // kota harcanmıştır, aynı replik tekrar geldiğinde yeniden istek atılmasın
                    cloudTranslationCache[normalized] = fullTranslation
                    // Ekrana yalnızca altyazı hâlâ geçerliyse bas
                    if (lastNormalizedText == normalized) {
                        hasCloudTranslated = true
                        updateOverlayText(fullTranslation)
                    }
                }
            } else {
                val errorCode = result.exceptionOrNull()?.message ?: "UNKNOWN"
                handleTranslationError(errorCode)
            }
        }
    }

    private fun handleTranslationError(errorCode: String) {
        val now = System.currentTimeMillis()
        when {
            // Güvenlik filtresine takılan tek satır yüzünden tüm bulut çevirisi duraklatılmaz
            errorCode.contains("EMPTY_RESPONSE") -> {}
            errorCode.contains("401") -> {
                isApiKeyFatalError = true
                showSingleToast("HATA: API Anahtarı Geçersiz (401). Gemini durduruldu.")
            }
            errorCode.contains("429") -> {
                cooldownUntil = now + currentBackoffDelayMs
                showSingleToast("Kota Doldu (429). Soğuma: ${currentBackoffDelayMs / 1000}s (Yerel Çeviri Aktif)")
                currentBackoffDelayMs = (currentBackoffDelayMs * 2).coerceAtMost(300000)
            }
            errorCode.contains("503") -> {
                cooldownUntil = now + currentBackoffDelayMs
                showSingleToast("Sunucu Yoğun (503). Soğuma: ${currentBackoffDelayMs / 1000}s")
                currentBackoffDelayMs = (currentBackoffDelayMs * 2).coerceAtMost(300000)
            }
            // Ağ hatası sunucu reddi değildir: kısa soğuma uygulanır, üstel artış yoktur
            errorCode.contains("NET_ERROR") -> {
                cooldownUntil = now + 5000
                showSingleToast("Ağ Hatası. 5s sonra yeniden denenecek (Yerel Çeviri Aktif)")
            }
            else -> {
                cooldownUntil = now + 15000
                showSingleToast("Çeviri Hatası: $errorCode (Geçici Duraklatıldı)")
            }
        }
    }

    private fun showSingleToast(message: String) {
        if (message == lastToastMessage) return
        lastToastMessage = message
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateOverlayText(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            if (text.isEmpty()) {
                overlayView?.visibility = View.GONE
            } else {
                overlayView?.visibility = View.VISIBLE
                tvSubtitle?.text = text
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val rawBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)

            if (rawBitmap.width != screenWidth || rawBitmap.height != screenHeight) {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                rawBitmap.recycle()
                cropped
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        recognizer.close()
        localTranslator?.close()

        virtualDisplay?.release()
        virtualDisplay = null

        // imageReader kapatılmazsa her servis turunda ~16 MB RGBA tampon açıkta kalır
        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        // Zaten kaldırılmış görünümde removeView istisna atıp onDestroy'u yarıda keser
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
    }
}
