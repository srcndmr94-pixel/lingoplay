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

    // Ekran titremesini önlemek için boş kare sayacı (Debounce)
    private var blankFrameCount = 0

    // Sadece Gemini Çevirilerini Saklayan Max 300 Elemanlı LRU Cache
    private val cloudTranslationCache = object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 300
        }
    }

    // Soğuma ve Hata Yönetimi
    private var cooldownUntil: Long = 0
    private var currentBackoffDelayMs: Long = 15000 // 15s -> 30s -> 60s ... (max 5m)
    private var isApiKeyFatalError = false
    private var lastToastMessage = ""

    // SharedPreferences Ön Önbelleği (Kare başı I/O engeli)
    private var cachedCropTopRatio = 0.65f
    private var cachedCropHeightRatio = 0.30f

    // Bildirim Engelleme Listesi
    private val notificationAppNames = listOf(
        "messenger", "facebook", "whatsapp", "geoguessr",
        "instagram", "telegram", "youtube", "discord", "gmail", "spotify"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initOverlay()
        initLocalTranslator()
        loadPreferences()
    }

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
                    val cropHeight = (screenHeight * cachedCropHeightRatio).toInt().coerceAtLeast(1).coerceAtMost(screenHeight - cropY)

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
                            
                            // 1. SÜZGEÇ: Ekran Çöpü / HUD Kontrolü (Görünürlük Filtresi)
                            if (isValidSubtitleText(cleanedText)) {
                                blankFrameCount = 0
                                handleDetectedText(cleanedText)
                            } else {
                                blankFrameCount++
                                // Anlık titremeleri (flicker) önlemek için 2 ardışık boş kareden sonra temizle
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

    private fun aksaniDuzle(s: String): String {
        val ayristirilmis = Normalizer.normalize(s, Normalizer.Form.NFD)
        return ayristirilmis.replace(Regex("\\p{Mn}+"), "")
    }

    private fun cleanOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        val unaccented = aksaniDuzle(rawText)
        return unaccented
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\u2019", "'")            // kıvrık ' → düz '
            .replace("\u2018", "'")
            .replace("\u201C", "")             // yalnızca çift tırnaklar atılır
            .replace("\u201D", "")
            .replace("\"", "")
            .replace("`", "'")
            // Zamirler ile kısaltmaları birleştir/ayır
            .replace(Regex("(?i)\\b(you|we|they)\\s*'(re|ve|ll|d)\\b"), "$1'$2")
            .replace(Regex("(?i)\\b(i)\\s*'(m|ve|ll|d)\\b"), "$1'$2")
            .replace(Regex("(?i)\\b(he|she|it|that|there|who|what)\\s*'(s|ll|d|ve)\\b"), "$1'$2")
            .replace(Regex("(?i)\\b(you|we|they)\\s*'(re|ve|ll|d)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\b(i)\\s*'(m|ve|ll|d)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\b(he|she|it|that|there|who|what)\\s*'(ll|d|ve)(?=[a-z]{3,})"), "$1'$2 ")
            .replace(Regex("(?i)\\byoutre\\b"), "You're")
            .replace(Regex("(?i)\\byouive\\b"), "You've")
            .replace(Regex("(?i)\\bIFL\\b"), "If I")
            .replace(Regex("(?i)\\bIfl\\b"), "If I")
            .replace(Regex("\\bl(?=')"), "I")  // l'll, l'm, l've → I'll, I'm, I've
            .replace(Regex("\\bl\\b"), "I")
            .replace(Regex("(?<=\\bIf\\s)[l1]\\b"), "I")
            .replace("vw", "w")                // unvworthy -> unworthy
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isNotificationBanner(text: String): Boolean {
        val sikistirilmis = text.lowercase().replace(Regex("[^a-z]"), "")
        val eslesme = notificationAppNames.count { sikistirilmis.contains(it) }

        if (eslesme >= 2) return true

        if (eslesme == 1) {
            val gorunur = text.count { !it.isWhitespace() }
            val cumleMi = text.any { it in ".!?" }
            return gorunur < 30 && !cumleMi
        }
        return false
    }

    /**
     * SÜZGEÇ 1: Gerçekten ekranda gösterilecek metin mi? (HUD, Can Barı, Tuş Simgesi, Sayaç ve Bildirim Eleme)
     */
    private fun isValidSubtitleText(text: String): Boolean {
        if (text.length < 2) return false

        val visibleCount = text.count { !it.isWhitespace() }
        if (visibleCount == 0) return false

        // Sosyal medya / bildirim şeritlerini doğrudan engelle
        if (isNotificationBanner(text)) {
            return false
        }

        // Tek harfli artıkları ("x2", "L1") ele
        val letterCount = text.count { it.isLetter() }
        if (letterCount < 2) return false

        // Oran YALNIZCA görünür karakterler üzerinden hesaplanır
        val nonLetterCount = visibleCount - letterCount
        if (nonLetterCount.toDouble() / visibleCount > 0.50) return false

        return true
    }

    /**
     * SÜZGEÇ 2: Gemini bulut isteği atmaya değer uzunlukta mı? (Kota Koruma Filtresi)
     */
    private fun shouldSendToCloud(text: String): Boolean {
        // En az 2 adet 3+ harfli kelime içermelidir. ("Run!", "Yes." gibi kısa replikler bedava yerel çeviride kalır)
        val meaningfulWords = text.split(" ").filter { word -> word.count { it.isLetter() } >= 3 }
        return meaningfulWords.size >= 2
    }

    private fun normalizeForMatching(text: String): String {
        return aksaniDuzle(text).lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /** OCR titremesini aynı metin sayar: tek karakterlik fark yeni istek doğurmaz. */
    private fun ayniMetinMi(yeni: String, onceki: String): Boolean {
        if (onceki.isEmpty()) return false
        if (yeni == onceki) return true
        val sinir = maxOf(1, minOf(yeni.length, onceki.length) / 12)
        return uzaklikEnFazla(yeni, onceki, sinir)
    }

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

        // 1. ÖNBELLEK KONTROLÜ (Geçmiş Gemini Çevirileri)
        val cachedCloudTranslation = cloudTranslationCache[normalized]
        if (cachedCloudTranslation != null) {
            hasCloudTranslated = true
            updateOverlayText(cachedCloudTranslation)
            return
        }

        // 2. AŞAMA: Yerel ML Kit çevirisini ANINDA ekrana bas (Tüm geçerli metinler için bedava)
        if (isLocalTranslatorReady) {
            localTranslator?.translate(text)
                ?.addOnSuccessListener { localTranslation ->
                    if (lastNormalizedText == normalized && localTranslation.isNotBlank() && !hasCloudTranslated) {
                        updateOverlayText(localTranslation)
                    }
                }
        }

        // 3. AŞAMA: Kısa replikler için Gemini'ye boşuna kota harcama ("Run!", "Yes." vb. yerelde kalır)
        if (!shouldSendToCloud(text)) {
            return
        }

        // 4. AŞAMA: Uzun replikler için Kaliteli Gemini Çevirisini Sorgula
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
                if (lastNormalizedText == normalized && fullTranslation.isNotEmpty()) {
                    hasCloudTranslated = true
                    cloudTranslationCache[normalized] = fullTranslation
                    updateOverlayText(fullTranslation)
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
            // Güvenlik filtresine takılan satırlarda soğuma uygulanmaz; yerel çeviri ekranda tutulur
            errorCode.contains("EMPTY_RESPONSE") -> {
                // No cooldown triggered, subsequent lines continue normally
            }
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

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
    }
}