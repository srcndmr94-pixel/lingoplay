package com.example.subtitles

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
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
        private const val SUBTITLE_INTERVAL_MS = 450L
        private const val PANEL_INTERVAL_MS = 1_200L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val subtitleBusy = AtomicBoolean(false)
    private val panelBusy = AtomicBoolean(false)
    private val subtitleGeneration = AtomicLong(0L)
    @Volatile private var pendingSubtitleFrame: Bitmap? = null
    private val subtitleRoi = SubtitleRoi()

    private lateinit var runtime: SubtitleRuntime
    private lateinit var subtitleRecognizer: ScreenTextRecognizer
    private var dynamicRecognizer: DynamicTextRecognizer? = null
    private var subtitleOverlay: SubtitleOverlay? = null
    private var dynamicOverlay: DynamicPanelOverlay? = null
    private var panelCoordinator: DynamicPanelCoordinator? = null

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastSubtitleAt = 0L
    private var lastPanelAt = 0L

    override fun onCreate() {
        super.onCreate()
        runtime = SubtitleRuntime(this)
        subtitleRecognizer = ScreenTextRecognizer()

        if (Settings.canDrawOverlays(this)) {
            subtitleOverlay = SubtitleOverlay(this)
            dynamicOverlay = DynamicPanelOverlay(this)
            dynamicRecognizer = DynamicTextRecognizer()
            panelCoordinator = DynamicPanelCoordinator(
                recognizer = dynamicRecognizer!!,
                panelTranslator = runtime.panelRouter,
                overlay = dynamicOverlay!!
            )
        }
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
            stopSelf()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopSelf()
            }, Handler(Looper.getMainLooper()))
        }

        val wm = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ onImage(reader) }, Handler(Looper.getMainLooper()))
        }

        virtualDisplay = projection?.createVirtualDisplay(
            "SubtitleCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        scope.launch {
            runCatching { runtime.prepare() }
                .onFailure { Log.e("SubtitleCapture", "Offline model hazırlama başarısız", it) }
        }
    }

    private fun onImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()
        val needSubtitle = now - lastSubtitleAt >= SUBTITLE_INTERVAL_MS &&
            subtitleBusy.compareAndSet(false, true)
        val needPanels = panelCoordinator != null &&
            now - lastPanelAt >= PANEL_INTERVAL_MS &&
            panelBusy.compareAndSet(false, true)

        if (!needSubtitle && !needPanels) {
            image.close()
            return
        }

        val screen = try {
            ScreenImageConverter.toBitmap(image)
        } finally {
            image.close()
        }

        if (needSubtitle) {
            lastSubtitleAt = now
            val subtitleFrame = if (needPanels) screen.copy(Bitmap.Config.ARGB_8888, false) else screen
            val generation = subtitleGeneration.incrementAndGet()
            scope.launch {
                try {
                    processSubtitle(subtitleFrame, generation)
                } catch (t: Throwable) {
                    Log.e("SubtitleCapture", "Altyazı OCR/çeviri başarısız", t)
                } finally {
                    subtitleFrame.recycle()
                    subtitleBusy.set(false)
                    drainLatestSubtitleFrame()
                }
            }
        } else if (!subtitleBusy.compareAndSet(false, true)) {
            // A translation is already running. Keep exactly one pending frame: the newest one.
            val pending = screen.copy(Bitmap.Config.ARGB_8888, false)
            pendingSubtitleFrame?.recycle()
            pendingSubtitleFrame = pending
        } else {
            subtitleBusy.set(false)
        }

        if (needPanels) {
            lastPanelAt = now
            val panelFrame = if (needSubtitle) screen else screen.copy(Bitmap.Config.ARGB_8888, false)
            scope.launch {
                try {
                    panelCoordinator?.process(panelFrame)
                } catch (t: Throwable) {
                    Log.e("DynamicPanel", "Dinamik panel OCR/çeviri başarısız", t)
                } finally {
                    panelFrame.recycle()
                    panelBusy.set(false)
                }
            }
        }
    }

    private fun drainLatestSubtitleFrame() {
        val next = pendingSubtitleFrame ?: return
        pendingSubtitleFrame = null
        if (!subtitleBusy.compareAndSet(false, true)) {
            next.recycle()
            return
        }
        val generation = subtitleGeneration.incrementAndGet()
        scope.launch {
            try {
                processSubtitle(next, generation)
            } catch (t: Throwable) {
                Log.e("SubtitleCapture", "Bekleyen altyazı OCR/çeviri başarısız", t)
            } finally {
                next.recycle()
                subtitleBusy.set(false)
                drainLatestSubtitleFrame()
            }
        }
    }

    private suspend fun processSubtitle(screen: Bitmap, generation: Long) {
        val cropped = subtitleRoi.crop(screen)
        try {
            val raw = subtitleRecognizer.recognize(cropped)
            if (raw.isBlank()) return
            val normalized = OcrNormalizer.normalize(raw)
            if (normalized.isBlank()) return
            if (SubtitleUiFilter.shouldIgnore(normalized)) {
                withContext(Dispatchers.Main) {
                    if (generation == subtitleGeneration.get()) subtitleOverlay?.clear()
                }
                return
            }
            val translated = runtime.processor.process(normalized, 42)
            // Never display a translation that finished after a newer subtitle arrived.
            if (generation != subtitleGeneration.get()) return
            withContext(Dispatchers.Main) {
                if (generation != subtitleGeneration.get()) return@withContext
                if (translated.isNullOrBlank()) subtitleOverlay?.clear()
                else subtitleOverlay?.show(translated)
            }
        } finally {
            if (cropped !== screen) cropped.recycle()
        }
    }

    private fun startForegroundSafely() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 9, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Altyazı çevirisi çalışıyor")
            .setContentText("Konuşma altyazıları ve dinamik ekran panelleri taranıyor")
            .setOngoing(true)
            .addAction(0, "Durdur", pending)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Altyazı yakalama", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        subtitleOverlay?.close()
        dynamicOverlay?.close()
        dynamicRecognizer?.close()
        pendingSubtitleFrame?.recycle()
        pendingSubtitleFrame = null
        subtitleRecognizer.close()
        runtime.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
