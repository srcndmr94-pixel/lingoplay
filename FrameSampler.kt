package com.example.subtitles

import kotlin.math.roundToInt

data class FrameRateSnapshot(
    val inputFrames: Long,
    val acceptedFrames: Long,
    val inputFps: Float,
    val acceptedFps: Float,
    val acceptancePercent: Int,
    val windowDurationMs: Long
)

class FrameSampler(
    private val minimumIntervalMs: Long = 180L,
    private val statsWindowMs: Long = 1_000L,
    private val onStats: ((FrameRateSnapshot) -> Unit)? = null
) {
    private var lastAcceptedMs: Long? = null
    private var windowStartedMs: Long? = null
    private var inputCount = 0L
    private var acceptedCount = 0L

    @Synchronized
    fun shouldAccept(timestampMs: Long): Boolean {
        if (windowStartedMs == null) windowStartedMs = timestampMs
        inputCount++

        val previous = lastAcceptedMs
        val accepted = previous == null || timestampMs - previous >= minimumIntervalMs
        if (accepted) {
            lastAcceptedMs = timestampMs
            acceptedCount++
        }

        publishIfWindowComplete(timestampMs)
        return accepted
    }

    @Synchronized
    fun snapshot(timestampMs: Long): FrameRateSnapshot? {
        val start = windowStartedMs ?: return null
        val duration = (timestampMs - start).coerceAtLeast(1L)
        return makeSnapshot(duration)
    }

    @Synchronized
    fun reset() {
        lastAcceptedMs = null
        windowStartedMs = null
        inputCount = 0L
        acceptedCount = 0L
    }

    private fun publishIfWindowComplete(timestampMs: Long) {
        val start = windowStartedMs ?: return
        val duration = timestampMs - start
        if (duration < statsWindowMs) return

        onStats?.invoke(makeSnapshot(duration.coerceAtLeast(1L)))
        windowStartedMs = timestampMs
        inputCount = 0L
        acceptedCount = 0L
    }

    private fun makeSnapshot(durationMs: Long): FrameRateSnapshot {
        val seconds = durationMs / 1000f
        val inputFps = if (seconds > 0f) inputCount / seconds else 0f
        val acceptedFps = if (seconds > 0f) acceptedCount / seconds else 0f
        val percent = if (inputCount > 0) {
            ((acceptedCount * 100f) / inputCount).roundToInt()
        } else 0

        return FrameRateSnapshot(
            inputFrames = inputCount,
            acceptedFrames = acceptedCount,
            inputFps = inputFps,
            acceptedFps = acceptedFps,
            acceptancePercent = percent,
            windowDurationMs = durationMs
        )
    }
}
