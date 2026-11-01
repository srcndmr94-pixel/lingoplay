package com.example.subtitles

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.subtitles.START_CAPTURE"
        const val ACTION_STOP = "com.example.subtitles.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "subtitle_capture"
        private const val NOTIFICATION_ID = 7101
        private const val OCR_INTERVAL_MS = 260L
        private const val TAG = "SubtitleCapture"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocrBusy = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    private lateinit var runtime: SubtitleRuntime
    private lateinit var recognizer: ScreenTextRecognizer
    private lateinit var subtitleRoi: SubtitleRoi
    private val stableCollector = StableSubtitleCollector(
        shortStableFrames = 2,
        longStableFrames = 2,
        missingTolerance = 1
    )

    private var overlay: SubtitleOverlay? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastOcrAt = 0L

    override fun onCreate() {
        super.onCreate()
        runtime = SubtitleRuntime(this, TranslationEngineState)
        recognizer = ScreenTextRecognizer()
        subtitleRoi = SubtitleRoi(this)
        if (Settings.canDrawOverlays(this)) overlay = SubtitleOverlay(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundSafely()
        if (projection == null && intent?.action == ACTION_START) startProjection(intent)
        return START_NOT_STICKY
    }

    private fun startProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "MediaProjection izni alınamadı")
            stopSelf()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopSelf()
            }, Handler(Looper.getMainLooper()))
        }

        val windowManager = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) windowManager.maximumWindowMetrics.bounds else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener(
                { available -> onImageAvailable(available) },
                Handler(Looper.getMainLooper())
            )
        }

        virtualDisplay = projection?.createVirtualDisplay(
            "SubtitleCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )

        scope.launch {
            runCatching { runtime.prepare() }
                .onFailure { Log.e(TAG, "Çevrimdışı model hazırlanamadı", it) }
        }
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()

        if (now - lastOcrAt < OCR_INTERVAL_MS || !ocrBusy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastOcrAt = now

        val screen = try {
            ScreenImageConverter.toBitmap(image)
        } finally {
            image.close()
        }
        val currentGeneration = generation.incrementAndGet()

        scope.launch {
            try {
                processFrame(screen, currentGeneration)
            } catch (error: Throwable) {
                Log.e(TAG, "OCR/çeviri başarısız", error)
            } finally {
                screen.recycle()
                ocrBusy.set(false)
            }
        }
    }

    private suspend fun processFrame(screen: Bitmap, currentGeneration: Long) {
        val cropped = subtitleRoi.crop(screen)
        try {
            val raw = recognizer.recognize(cropped)
            val normalized = OcrNormalizer.normalize(raw)

            if (normalized.isBlank() || SubtitleUiFilter.shouldIgnore(normalized)) {
                stableCollector.offer("")
                return
            }

            val stableText = stableCollector.offer(normalized) ?: return

            // Mevcut motorun ürettiği ham çeviriyi al.
            val rawTranslation = runtime.processor.process(stableText, 42)
            if (rawTranslation.isNullOrBlank()) return

            // Zorunlu son kapı: doğal oyun Türkçesi sözlüğü overlay öncesinde
            // her zaman çalışır. SubtitleRuntime bağlantısından bağımsızdır.
            val translated = NaturalGameTurkishLexicon.polish(
                sourceEnglish = stableText,
                fallbackTurkish = rawTranslation
            )
            if (translated.isBlank()) return

            Log.d(TAG, "OKUNAN: $stableText")
            Log.d(TAG, "HAM_CEVIRI: $rawTranslation")
            Log.d(TAG, "YAZILAN: $translated")

            // Daha yeni kare başladıysa eski sonucu ekrana basma.
            if (currentGeneration != generation.get()) return
            withContext(Dispatchers.Main) {
                if (currentGeneration == generation.get()) overlay?.show(translated)
            }
        } finally {
            if (cropped !== screen && !cropped.isRecycled) cropped.recycle()
        }
    }

    private fun startForegroundSafely() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pendingIntent = PendingIntent.getService(
            this,
            9,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Canlı oyun altyazısı")
            .setContentText("Seçilen alandaki oyun altyazıları çevriliyor")
            .setOngoing(true)
            .addAction(0, "Durdur", pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canlı oyun altyazısı",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        stableCollector.reset()
        overlay?.close()
        recognizer.close()
        runtime.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
