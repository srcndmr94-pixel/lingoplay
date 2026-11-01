package com.example.subtitles

interface OfflineTranslationEngine : SubtitleTranslator, AutoCloseable {
    suspend fun prepareModel()
}

interface ApiKeyProvider {
    fun hasKey(): Boolean
}

interface TranslationTelemetry {
    fun onOnlineSuccess(channel: String, durationMs: Long)
    fun onOfflineFallback(channel: String, reason: String)
    fun onCircuitOpened(channel: String, durationMs: Long)
}

object NoOpTranslationTelemetry : TranslationTelemetry {
    override fun onOnlineSuccess(channel: String, durationMs: Long) = Unit
    override fun onOfflineFallback(channel: String, reason: String) = Unit
    override fun onCircuitOpened(channel: String, durationMs: Long) = Unit
}

fun interface MonotonicClock {
    fun nowMs(): Long
}
