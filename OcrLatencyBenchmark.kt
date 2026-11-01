package com.example.subtitles

import android.graphics.Bitmap
import android.os.SystemClock

data class OcrLatencySample(
    val totalMs: Long,
    val preprocessMs: Long,
    val primaryOcrMs: Long,
    val retryOcrMs: Long,
    val retried: Boolean,
    val textLength: Int
)

data class OcrLatencyReport(
    val samples: Int,
    val averageMs: Double,
    val p50Ms: Long,
    val p95Ms: Long,
    val maxMs: Long,
    val retryPercent: Int,
    val effectiveFps: Double
)

/** Gerçek ScreenTextRecognizer ile cihaz üzerinde OCR gecikmesini ölçer. */
class OcrLatencyBenchmark(
    private val recognizer: ScreenTextRecognizer
) {
    suspend fun measure(
        source: Bitmap,
        warmupRuns: Int = 2,
        measuredRuns: Int = 20
    ): Pair<OcrLatencyReport, List<OcrLatencySample>> {
        require(warmupRuns >= 0)
        require(measuredRuns > 0)

        repeat(warmupRuns) { runOnce(source) }
        val samples = List(measuredRuns) { runOnce(source) }
        return summarize(samples) to samples
    }

    private suspend fun runOnce(source: Bitmap): OcrLatencySample {
        val totalStarted = SystemClock.elapsedRealtimeNanos()

        val prepStarted = SystemClock.elapsedRealtimeNanos()
        val prepared = SubtitleImagePreprocessor.prepare(source)
        val preprocessMs = elapsedMs(prepStarted)

        try {
            val primaryStarted = SystemClock.elapsedRealtimeNanos()
            var raw = recognizer.recognize(prepared)
            val primaryMs = elapsedMs(primaryStarted)

            var retryMs = 0L
            var retried = false
            if (SubtitleImagePreprocessor.shouldRetryOcr(raw)) {
                retried = true
                val enhanced = SubtitleImagePreprocessor.enhancedCopy(prepared)
                try {
                    val retryStarted = SystemClock.elapsedRealtimeNanos()
                    val retry = recognizer.recognize(enhanced)
                    retryMs = elapsedMs(retryStarted)
                    raw = SubtitleImagePreprocessor.chooseBetterText(raw, retry)
                } finally {
                    enhanced.recycle()
                }
            }

            return OcrLatencySample(
                totalMs = elapsedMs(totalStarted),
                preprocessMs = preprocessMs,
                primaryOcrMs = primaryMs,
                retryOcrMs = retryMs,
                retried = retried,
                textLength = raw.length
            )
        } finally {
            if (prepared !== source) prepared.recycle()
        }
    }

    companion object {
        fun summarize(samples: List<OcrLatencySample>): OcrLatencyReport {
            require(samples.isNotEmpty())
            val sorted = samples.map { it.totalMs }.sorted()
            val average = sorted.average()
            val retries = samples.count { it.retried }
            return OcrLatencyReport(
                samples = samples.size,
                averageMs = average,
                p50Ms = percentile(sorted, 0.50),
                p95Ms = percentile(sorted, 0.95),
                maxMs = sorted.last(),
                retryPercent = ((retries * 100.0) / samples.size).toInt(),
                effectiveFps = if (average > 0.0) 1000.0 / average else 0.0
            )
        }

        private fun percentile(sorted: List<Long>, ratio: Double): Long {
            val index = ((sorted.size - 1) * ratio).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        private fun elapsedMs(startedNs: Long): Long =
            (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L
    }
}
