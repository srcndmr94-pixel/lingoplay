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
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tvSubtitle: TextView? = null

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var lastExtractedText = ""
    private var isProcessing = false
    private var screenWidth = 1080
    private var screenHeight = 1920

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initOverlay()
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
            try {
                val bitmap = imageToBitmap(image)
                image.close()

                if (bitmap != null) {
                    // Ekranın alt %40'lık alanını daha hassas tara
                    val cropY = (screenHeight * 0.60).toInt()
                    val cropHeight = (screenHeight * 0.38).toInt()
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, cropY, screenWidth, cropHeight)

                    val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val cleanedText = cleanOcrText(visionText.text)
                            handleDetectedText(cleanedText)
                        }
                        .addOnFailureListener {
                            isProcessing = false
                        }
                        .addOnCompleteListener {
                            isProcessing = false
                        }
                } else {
                    isProcessing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isProcessing = false
            }
        }
    }

    private fun cleanOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        
        var cleaned = rawText
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace(Regex("^IFL\\b", RegexOption.IGNORE_CASE), "If I")
            .replace(Regex("^Ifl\\b"), "If I")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned
    }

    private fun handleDetectedText(text: String) {
        if (text.length < 3) {
            if (lastExtractedText.isNotEmpty()) {
                lastExtractedText = ""
                updateOverlayText("")
            }
            return
        }

        if (text == lastExtractedText) return
        lastExtractedText = text

        serviceScope.launch {
            val fullTranslation = AiTranslate.translateWithClaude(text, "Wuxia / Action RPG Game")
            if (lastExtractedText == text && fullTranslation.isNotEmpty()) {
                updateOverlayText(fullTranslation)
            }
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

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection?.stop()
        overlayView?.let { windowManager?.removeView(it) }
    }
}