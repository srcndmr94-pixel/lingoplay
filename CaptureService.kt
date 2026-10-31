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

    private var blankFrameCount = 0

    private val cloudTranslationCache = object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 300
        }
    }

    private var cooldownUntil: Long = 0
    private var currentBackoffDelayMs: Long = 15000
    private var isApiKeyFatalError = false
    private var lastToastMessage = ""

    private var cachedCropTopRatio = 0.65f
    private var cachedCropHeightRatio = 0.30f

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
                            
                            if (isValidSubtitleText(cleanedText)) {
                                blankFrameCount = 0
                                handleDetectedText(cleanedText)
                            } else {
                                blankFrameCount++
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

    // Unicode diacritic cleaning to unify accented OCR artifacts like Leavé -> Leave
    private fun aksaniDuzle(s: String): String {
        val ayristirilmis = Normalizer.normalize(s, Normalizer.Form.NFD)
        return ayristirilmis.replace(Regex("\\p{Mn}+"), "")
    }

    // Splits punctuation-glued words while protecting ellipses and decimals
    private fun noktalamaAyir(s: String): String {
        var t = s
        t = Regex("\\.{2,}").replace(t) { "\u0001".repeat(it.value.length) }
        t = Regex("(?<=\\d)\\.(?=\\d)").replace(t, "\u0002")
        t = Regex("(?<=[a-z])[.,](?=[a-z])").replace(t, " ")
        t = Regex("(?<=[a-z])([.,])(?=[A-Z])").replace(t, "$1 ")
        return t.replace("\u0001", ".").replace("\u0002", ".")
    }

    // Cleans OCR outputs, handling punctuation gluing, typos, and pronoun contractions securely
    private fun cleanOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        val unaccented = aksaniDuzle(rawText)
        val punctuated = noktalamaAyir(unaccented)
        return punctuated
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\u2019", "'")
            .replace("\u2018", "'")
            .replace("\u201C", "")
            .replace("\u201D", "")
            .replace("\"", "")
            .replace("`", "'")
            .replace(Regex("(?<![A-Za-z0-9])0(?=[a-z]{2,})"), "O")   // Corrects 0ur -> Our
            .replace(Regex("^[A-Z](?=[A-Z][a-z]{2,})"), "")          // Cleans POur -> Our
            // Possessive attachment splitter (Sect'sfistart -> Sect's fistart) before pronouns
            .replace(Regex("\\b([A-Z][a-z]{2,})'s(?=[a-z]{3,})"), "$1's ")
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
            .replace(Regex("\\bl(?=')"), "I")
            .replace(Regex("\\bl\\b"), "I")
            .replace(Regex("(?<=\\bIf\\s)[l1]\\b"), "I")
            .replace("vw", "w")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // Detects notification popups/banners in OCR text
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

    // Filters out interactive game button prompts and HUD instructions
    private fun isInputPrompt(text: String): Boolean {
        if (Regex("\\[[^\\]]{1,12}\\]").containsMatchIn(text)) return true
        if (Regex("(?i)\\b(press|hold|tap)\\b.{0,30}\\bto\\b").containsMatchIn(text)) return true
        if (Regex("(?i)[+□○△✕]\\s*(to|and)\\b").containsMatchIn(text)) return true
        return false
    }

    private fun isValidSubtitleText(text: String): Boolean {
        if (text.length < 2) return false

        val visibleCount = text.count { !it.isWhitespace() }
        if (visibleCount == 0) return false

        if (isNotificationBanner(text) || isInputPrompt(text)) {
            return false
        }

        val letterCount = text.count { it.isLetter() }
        if (letterCount < 2) return false

        val nonLetterCount = visibleCount - letterCount
        if (nonLetterCount.toDouble() / visibleCount > 0.50) return false

        return true
    }

    private fun shouldSendToCloud(text: String): Boolean {
        val meaningfulWords = text.split(" ").filter { word -> word.count { it.isLetter() } >= 3 }
        return meaningfulWords.size >= 2
    }

    private fun normalizeForMatching(text: String): String {
        return aksaniDuzle(text).lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    // Adaptive Levenshtein fuzzy text deduplication threshold
    private fun ayniMetinMi(yeni: String, onceki: String): Boolean {
        if (onceki.isEmpty()) return false
        if (yeni == onceki) return true
        val enUzun = maxOf(yeni.length, onceki.length)
        val sinir = if (enUzun >= 20) (enUzun * 0.34).toInt() else 1
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

        val cachedCloudTranslation = cloudTranslationCache[normalized]
        if (cachedCloudTranslation != null) {
            hasCloudTranslated = true
            updateOverlayText(cachedCloudTranslation)
            return
        }

        if (isLocalTranslatorReady) {
            localTranslator?.translate(text)
                ?.addOnSuccessListener { localTranslation ->
                    if (lastNormalizedText == normalized && localTranslation.isNotBlank() && !hasCloudTranslated) {
                        updateOverlayText(localTranslation)
                    }
                }
        }

        if (!shouldSendToCloud(text)) {
            return
        }

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