package com.example.subtitles

import android.os.SystemClock
import kotlinx.coroutines.CancellationException

class TranslationRouter(
    private val channel: String = "subtitle",
    private val online: GeminiTranslationEngine,
    private val offline: OfflineTranslationEngine,
    private val onlinePolicy: OnlineTranslationPolicy,
    private val telemetry: TranslationTelemetry = NoOpTranslationTelemetry,
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 8_000L,
    private val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
) : TranslationEngine {
    init { require(failureThreshold > 0); require(cooldownMs >= 0L) }
    private var consecutiveFailures = 0
    private var circuitOpenUntil = Long.MIN_VALUE

    override suspend fun translate(request: SubtitleTranslationRequest): TranslationResult {
        val source = request.normalizedEnglish.trim()
        if (source.isBlank()) return TranslationResult.Failure(request.subtitleId, "empty_source")
        if (!onlinePolicy.isOnlineEnabled()) return translateOffline(request, "online_disabled")
        if (clock.nowMs() < circuitOpenUntil) return translateOffline(request, "circuit_open")
        return try {
            val started = clock.nowMs()
            val raw = online.translate(request)
            when (val validation = GeminiTranslationValidator.validate(source, raw, request.previousContext)) {
                is GeminiTranslationValidator.Result.Valid -> {
                    consecutiveFailures = 0
                    telemetry.onOnlineSuccess(channel, clock.nowMs() - started)
                    TranslationResult.Success(request.subtitleId, validation.text, TranslationSource.GEMINI)
                }
                is GeminiTranslationValidator.Result.Invalid -> {
                    onOnlineFailure()
                    translateOffline(request, "gemini_invalid:${validation.reason.name.lowercase()}")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onOnlineFailure()
            translateOffline(request, "gemini_error:${error::class.simpleName ?: "unknown"}")
        }
    }

    private fun onOnlineFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            consecutiveFailures = 0
            circuitOpenUntil = clock.nowMs() + cooldownMs
            telemetry.onCircuitOpened(channel, cooldownMs)
        }
    }

    private suspend fun translateOffline(request: SubtitleTranslationRequest, reason: String): TranslationResult {
        telemetry.onOfflineFallback(channel, reason)
        return try {
            val translated = offline.translate(request.normalizedEnglish.trim()).trim()
            if (translated.isBlank()) TranslationResult.Failure(request.subtitleId, "$reason:offline_empty")
            else TranslationResult.Success(request.subtitleId, translated, TranslationSource.OFFLINE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            TranslationResult.Failure(request.subtitleId, "$reason:offline_error:${error::class.simpleName ?: "unknown"}")
        }
    }
}
