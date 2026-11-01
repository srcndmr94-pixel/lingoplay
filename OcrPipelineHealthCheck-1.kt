package com.example.subtitles

import android.graphics.Bitmap
import android.os.SystemClock

data class OcrPipelineHealth(
    val rawText: String,
    val normalizedText: String,
    val acceptedBySourceFilter: Boolean,
    val rejectedByUiFilter: Boolean,
    val stableText: String?,
    val preprocessMs: Long,
    val primaryOcrMs: Long,
    val retryOcrMs: Long,
    val totalMs: Long,
    val retried: Boolean,
    val status: Status
) {
    enum class Status {
        NO_TEXT,
        SOURCE_FILTERED,
        UI_FILTERED,
        WAITING_STABILITY,
        READY_FOR_TRANSLATION
    }
}

/**
 * Çeviri yapmadan OCR hattını uçtan uca denetler.
 * Crop edilmiş Bitmap verilmelidir. Böylece sorun görüntü yakalama mı, OCR mı,
 * normalizasyon mu, filtre mi, stabilizasyon mu net biçimde görülebilir.
 */
class OcrPipelineHealthCheck(
    private val recognizer: ScreenTextRecognizer,
    private val collector: StableSubtitleCollector = StableSubtitleCollector()
) {
    suspend fun inspect(croppedFrame: Bitmap): OcrPipelineHealth {
        val totalStart = SystemClock.elapsedRealtimeNanos()
        val prepStart = SystemClock.elapsedRealtimeNanos()
        val prepared = SubtitleImagePreprocessor.prepare(croppedFrame)
        val preprocessMs = elapsedMs(prepStart)

        try {
            val primaryStart = SystemClock.elapsedRealtimeNanos()
            var raw = recognizer.recognize(prepared)
            val primaryMs = elapsedMs(primaryStart)

            var retryMs = 0L
            var retried = false
            if (SubtitleImagePreprocessor.shouldRetryOcr(raw)) {
                retried = true
                val enhanced = SubtitleImagePreprocessor.enhancedCopy(prepared)
                try {
                    val retryStart = SystemClock.elapsedRealtimeNanos()
                    val retry = recognizer.recognize(enhanced)
                    retryMs = elapsedMs(retryStart)
                    raw = SubtitleImagePreprocessor.chooseBetterText(raw, retry)
                } finally {
                    enhanced.recycle()
                }
            }

            val normalized = OcrNormalizer.normalize(raw)
            if (normalized.isBlank()) {
                collector.offer("")
                return report(raw, normalized, false, false, null, preprocessMs, primaryMs, retryMs, retried, OcrPipelineHealth.Status.NO_TEXT, totalStart)
            }

            val sourceAccepted = SubtitleSourceFilter.shouldTranslate(normalized)
            if (!sourceAccepted) {
                collector.offer("")
                return report(raw, normalized, false, false, null, preprocessMs, primaryMs, retryMs, retried, OcrPipelineHealth.Status.SOURCE_FILTERED, totalStart)
            }

            val uiRejected = SubtitleUiFilter.shouldIgnore(normalized)
            if (uiRejected) {
                collector.offer("")
                return report(raw, normalized, true, true, null, preprocessMs, primaryMs, retryMs, retried, OcrPipelineHealth.Status.UI_FILTERED, totalStart)
            }

            val stable = collector.offer(normalized)
            val status = if (stable == null) {
                OcrPipelineHealth.Status.WAITING_STABILITY
            } else {
                OcrPipelineHealth.Status.READY_FOR_TRANSLATION
            }
            return report(raw, normalized, true, false, stable, preprocessMs, primaryMs, retryMs, retried, status, totalStart)
        } finally {
            if (prepared !== croppedFrame) prepared.recycle()
        }
    }

    fun reset() = collector.reset()

    private fun report(
        raw: String,
        normalized: String,
        sourceAccepted: Boolean,
        uiRejected: Boolean,
        stable: String?,
        preprocessMs: Long,
        primaryMs: Long,
        retryMs: Long,
        retried: Boolean,
        status: OcrPipelineHealth.Status,
        totalStart: Long
    ) = OcrPipelineHealth(
        rawText = raw,
        normalizedText = normalized,
        acceptedBySourceFilter = sourceAccepted,
        rejectedByUiFilter = uiRejected,
        stableText = stable,
        preprocessMs = preprocessMs,
        primaryOcrMs = primaryMs,
        retryOcrMs = retryMs,
        totalMs = elapsedMs(totalStart),
        retried = retried,
        status = status
    )

    private fun elapsedMs(startNs: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L
}

object OcrPipelineHealthFormatter {
    fun format(value: OcrPipelineHealth): String = buildString {
        appendLine("OCR_STATUS: ${value.status}")
        appendLine("RAW: ${value.rawText}")
        appendLine("NORMALIZED: ${value.normalizedText}")
        appendLine("SOURCE_FILTER: ${if (value.acceptedBySourceFilter) "PASS" else "DROP"}")
        appendLine("UI_FILTER: ${if (value.rejectedByUiFilter) "DROP" else "PASS"}")
        appendLine("STABLE: ${value.stableText ?: "-"}")
        appendLine("PREPROCESS_MS: ${value.preprocessMs}")
        appendLine("PRIMARY_OCR_MS: ${value.primaryOcrMs}")
        appendLine("RETRY_OCR_MS: ${value.retryOcrMs}")
        appendLine("RETRIED: ${value.retried}")
        append("TOTAL_MS: ${value.totalMs}")
    }
}
