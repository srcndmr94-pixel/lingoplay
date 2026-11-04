package com.example.subtitles

interface TranslationEngine { suspend fun translate(request: SubtitleTranslationRequest): TranslationResult }
interface GeminiTranslationEngine { suspend fun translate(request: SubtitleTranslationRequest): String }
interface OfflineTranslationEngine { suspend fun translate(normalizedEnglish: String): String }
fun interface OnlineTranslationPolicy { fun isOnlineEnabled(): Boolean }
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
fun interface MonotonicClock { fun nowMs(): Long }
