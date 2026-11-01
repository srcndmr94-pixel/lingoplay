package com.example.subtitles

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.subtitles.START_CAPTURE"
        const val ACTION_STOP = "com.example.subtitles.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "subtitle_capture"
        private const val NOTIFICATION_ID = 7101
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val roi = SubtitleRoi()
    private lateinit var runtime: SubtitleRuntime
    private lateinit var recognizer: ScreenTextRecognizer
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlay: SubtitleOverlay? = null
    private var lastFrameAt = 0L

    override fun onCreate() {
        super.onCreate()
        runtime = SubtitleRuntime(this)
        recognizer = ScreenTextRecognizer()
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
        } else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf(); return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
        }

        val wm = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        val density = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ onImage(reader) }, Handler(Looper.getMainLooper()))
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "SubtitleCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        scope.launch { runCatching { runtime.prepare() }.onFailure { Log.e("SubtitleCapture", "Model hazırlama başarısız", it) } }
    }

    private fun onImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 450 || !busy.compareAndSet(false, true)) {
            image.close(); return
        }
        lastFrameAt = now
        val bitmap = try { ScreenImageConverter.toBitmap(image) } finally { image.close() }
        scope.launch {
            try {
                val cropped = roi.crop(bitmap)
                val raw = recognizer.recognize(cropped)
                if (cropped !== bitmap) cropped.recycle()
                if (raw.isBlank()) return@launch
                Log.d("SubtitleCapture", "OKUNAN: $raw")
                val translated = runtime.processor.process(raw, 42)
                withContext(Dispatchers.Main) {
                    if (translated.isNullOrBlank()) overlay?.clear()
                    else {
                        Log.d("SubtitleCapture", "YAZILAN: $translated")
                        overlay?.show(translated)
                    }
                }
            } catch (t: Throwable) {
                Log.e("SubtitleCapture", "OCR/çeviri başarısız", t)
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private fun startForegroundSafely() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 9, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Altyazı yakalama çalışıyor")
            .setContentText("Ekranın alt-orta bölgesi taranıyor")
            .setOngoing(true)
            .addAction(0, "Durdur", pending)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
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
        overlay?.close()
        recognizer.close()
        runtime.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
