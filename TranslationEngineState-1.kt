package com.example.subtitles

import java.util.concurrent.atomic.AtomicReference

enum class TranslationEngineMode { GEMINI, OFFLINE, CIRCUIT_OPEN, NO_API_KEY }

data class TranslationEngineSnapshot(
    val mode: TranslationEngineMode,
    val reason: String = ""
)

object TranslationEngineState : TranslationTelemetry {
    private val current = AtomicReference(TranslationEngineSnapshot(TranslationEngineMode.OFFLINE, "not_started"))

    fun snapshot(): TranslationEngineSnapshot = current.get()

    override fun onOnlineSuccess(channel: String, durationMs: Long) {
        current.set(TranslationEngineSnapshot(TranslationEngineMode.GEMINI, "${channel}:${durationMs}ms"))
    }

    override fun onOfflineFallback(channel: String, reason: String) {
        val mode = when (reason) {
            "no_api_key" -> TranslationEngineMode.NO_API_KEY
            "circuit_open", "circuit_open_during_retry" -> TranslationEngineMode.CIRCUIT_OPEN
            else -> TranslationEngineMode.OFFLINE
        }
        current.set(TranslationEngineSnapshot(mode, "$channel:$reason"))
    }

    override fun onCircuitOpened(channel: String, durationMs: Long) {
        current.set(TranslationEngineSnapshot(TranslationEngineMode.CIRCUIT_OPEN, "$channel:${durationMs}ms"))
    }
}
