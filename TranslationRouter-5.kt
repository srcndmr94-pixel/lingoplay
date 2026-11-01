package com.example.subtitles

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TranslationRouter(
    private val channel: String,
    private val online: SubtitleTranslator,
    private val offline: OfflineTranslationEngine,
    private val apiKeyProvider: ApiKeyProvider,
    private val telemetry: TranslationTelemetry = NoOpTranslationTelemetry,
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 8_000L,
    private val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() },
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val jitterMs: () -> Long = { Random.nextLong(0L, 251L) }
) : SubtitleTranslator {

    companion object {
        private const val TAG = "SubtitleAI"
        private const val BASE_RETRY_DELAY_MS = 700L
        private const val MAX_RETRY_DELAY_MS = 1_100L
    }

    init {
        require(failureThreshold > 0) { "failureThreshold must be positive" }
        require(cooldownMs >= 0L) { "cooldownMs cannot be negative" }
    }

    private val consecutiveTransientFailures = AtomicInteger(0)
    private val circuitOpenUntil = AtomicLong(0L)
    private val retryEpoch = AtomicLong(0L)
    private val retryMutex = Mutex()

    override suspend fun translate(source: String, context: String): String {
        if (!apiKeyProvider.hasKey()) return fallback(source, context, "no_api_key")
        if (isCircuitOpen()) return fallback(source, context, "circuit_open")

        val startedAt = clock.nowMs()
        val observedEpoch = retryEpoch.get()
        return try {
            online.translate(source, context).also {
                markOnlineSuccess(clock.nowMs() - startedAt)
            }
        } catch (error: GeminiHttpException) {
            if (isTransientHttp(error.statusCode)) {
                retryUnderLock(source, context, error, observedEpoch)
            } else {
                Log.w(TAG, "[$channel] Kalıcı HTTP ${error.statusCode}; offline.")
                fallback(source, context, "http_${error.statusCode}")
            }
        } catch (error: SocketTimeoutException) {
            registerTransientFailure("timeout")
            offline.translate(source, context)
        } catch (error: Exception) {
            Log.w(TAG, "[$channel] Online çeviri başarısız; offline.", error)
            fallback(source, context, "exception_${error.javaClass.simpleName}")
        }
    }

    private suspend fun retryUnderLock(
        source: String,
        context: String,
        firstError: Exception,
        observedEpoch: Long
    ): String = retryMutex.withLock {
        if (isCircuitOpen()) return@withLock fallback(source, context, "circuit_open_during_retry")

        // Another coroutine already completed the single shared retry attempt.
        if (retryEpoch.get() != observedEpoch) {
            return@withLock fallback(source, context, "retry_already_handled")
        }

        val bounded = BASE_RETRY_DELAY_MS.coerceAtMost(MAX_RETRY_DELAY_MS)
        retryDelay(bounded + jitterMs().coerceIn(0L, 250L))

        val result = try {
            val startedAt = clock.nowMs()
            online.translate(source, context).also {
                markOnlineSuccess(clock.nowMs() - startedAt)
            }
        } catch (error: GeminiHttpException) {
            if (!isTransientHttp(error.statusCode)) {
                fallback(source, context, "retry_http_${error.statusCode}")
            } else {
                registerTransientFailure(reasonOf(error))
                offline.translate(source, context)
            }
        } catch (error: SocketTimeoutException) {
            registerTransientFailure("timeout")
            offline.translate(source, context)
        } catch (error: Exception) {
            registerTransientFailure(reasonOf(error))
            offline.translate(source, context)
        }

        retryEpoch.incrementAndGet()
        result
    }

    private fun isTransientHttp(status: Int): Boolean =
        status == 408 || status == 429 || status in 500..599

    private fun isCircuitOpen(): Boolean = clock.nowMs() < circuitOpenUntil.get()

    private fun markOnlineSuccess(durationMs: Long) {
        consecutiveTransientFailures.set(0)
        circuitOpenUntil.set(0L)
        telemetry.onOnlineSuccess(channel, durationMs.coerceAtLeast(0L))
    }

    private fun registerTransientFailure(reason: String) {
        val failures = consecutiveTransientFailures.incrementAndGet()
        telemetry.onOfflineFallback(channel, reason)
        if (failures >= failureThreshold) {
            circuitOpenUntil.set(clock.nowMs() + cooldownMs)
            consecutiveTransientFailures.set(0)
            telemetry.onCircuitOpened(channel, cooldownMs)
            Log.w(TAG, "[$channel] $reason tekrarlandı; devre $cooldownMs ms açıldı.")
        } else {
            Log.w(TAG, "[$channel] $reason; bu istek offline çevrildi.")
        }
    }

    private suspend fun fallback(source: String, context: String, reason: String): String {
        telemetry.onOfflineFallback(channel, reason)
        return offline.translate(source, context)
    }

    private fun reasonOf(error: Exception): String = when (error) {
        is GeminiHttpException -> "http_${error.statusCode}"
        is SocketTimeoutException -> "timeout"
        else -> "exception_${error.javaClass.simpleName}"
    }
}
