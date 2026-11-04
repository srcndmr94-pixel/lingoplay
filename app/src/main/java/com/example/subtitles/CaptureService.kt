package com.example.subtitles

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OCR-to-subtitle pipeline coordinator.
 * Existing MediaProjection/ImageReader plumbing should feed ROI bitmaps to submitFrame().
 */
class CaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stableCollector = StableSubtitleCollector(
        shortStableFrames = 2,
        longStableFrames = 3,
        missingTolerance = 1
    )

    // Wire these from the existing app runtime during integration.
    private lateinit var recognizer: ScreenTextRecognizer
    private lateinit var processor: SubtitleProcessor

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Feed the already-cropped subtitle ROI bitmap here from the existing ImageReader loop.
     */
    fun submitFrame(subtitleRoiBitmap: Bitmap) {
        val prepared = SubtitleImagePreprocessor.prepare(subtitleRoiBitmap)
        scope.launch {
            try {
                val result = recognizer.recognizeTextResult(prepared)
                val joined = OcrSubtitleLineJoiner.join(result)
                val stable = stableCollector.offer(joined) ?: return@launch
                if (SubtitleUiFilter.shouldIgnore(stable)) return@launch

                val normalizedEnglish = stable
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (normalizedEnglish.isNotBlank()) {
                    processor.process(
                        rawOcrText = stable,
                        normalizedEnglish = normalizedEnglish
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A bad OCR frame must not stop the capture loop.
            } finally {
                if (prepared !== subtitleRoiBitmap && !prepared.isRecycled) prepared.recycle()
            }
        }
    }

    suspend fun resetSubtitleSession() {
        stableCollector.clear()
        processor.clearContext()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
