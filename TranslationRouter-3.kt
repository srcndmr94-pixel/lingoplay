package com.example.subtitles

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import java.net.SocketTimeoutException
import kotlin.random.Random

class TranslationRouter(
    private val online: SubtitleTranslator,
    private val offline: OfflineSubtitleTranslator,
    private val apiKeyStore: ApiKeyStore
) : SubtitleTranslator, AutoCloseable {

    companion object {
        private const val TAG = "SubtitleAI"
        private const val MAX_TRANSIENT_RETRIES = 1
        private const val BASE_RETRY_DELAY_MS = 700L
        private const val MAX_RETRY_DELAY_MS = 1100L
        private const val CIRCUIT_FAILURE_THRESHOLD = 3
        private const val CIRCUIT_COOLDOWN_MS = 8_000L
    }

    private var consecutiveTransientFailures = 0
    private var circuitOpenUntil = 0L

    override suspend fun translate(source: String, context: String): String {
        if (!apiKeyStore.hasKey()) return offline.translate(source, context)

        val now = SystemClock.elapsedRealtime()
        if (now < circuitOpenUntil) {
            // Do not print an error for every subtitle while the API is cooling down.
            return offline.translate(source, context)
        }

        return try {
            online.translate(source, context).also { markOnlineSuccess() }
        } catch (error: GeminiHttpException) {
            when {
                isTransientHttp(error.statusCode) -> {
                    retryTransientOnce(source, context, error)
                }
                else -> {
                    // 400/401/403 etc. are request/auth/config errors. Never resend unchanged.
                    Log.w(TAG, "Kalıcı HTTP ${error.statusCode}; bu satır çevrimdışı çevrildi.")
                    offline.translate(source, context)
                }
            }
        } catch (error: SocketTimeoutException) {
            // For subtitles latency wins over retrying a timed-out request.
            registerTransientFailure("timeout")
            offline.translate(source, context)
        } catch (error: Exception) {
            Log.w(TAG, "Online çeviri başarısız; bu satır çevrimdışı çevrildi.", error)
            offline.translate(source, context)
        }
    }

    private suspend fun retryTransientOnce(
        source: String,
        context: String,
        firstError: Exception
    ): String {
        var lastError = firstError

        repeat(MAX_TRANSIENT_RETRIES) { retryIndex ->
            val exponential = BASE_RETRY_DELAY_MS * (1L shl retryIndex)
            val bounded = exponential.coerceAtMost(MAX_RETRY_DELAY_MS)
            val jitter = Random.nextLong(0L, 251L)
            delay(bounded + jitter)

            try {
                return online.translate(source, context).also { markOnlineSuccess() }
            } catch (error: GeminiHttpException) {
                if (!isTransientHttp(error.statusCode)) {
                    Log.w(TAG, "Retry kalıcı HTTP ${error.statusCode} döndürdü; offline fallback.")
                    return offline.translate(source, context)
                }
                lastError = error
            } catch (error: SocketTimeoutException) {
                lastError = error
            } catch (error: Exception) {
                lastError = error
                break
            }
        }

        val reason = when (lastError) {
            is GeminiHttpException -> "HTTP ${lastError.statusCode}"
            is SocketTimeoutException -> "timeout"
            else -> lastError.javaClass.simpleName
        }
        registerTransientFailure(reason)
        return offline.translate(source, context)
    }

    private fun isTransientHttp(status: Int): Boolean =
        status == 408 || status == 429 || status in 500..599

    @Synchronized
    private fun markOnlineSuccess() {
        consecutiveTransientFailures = 0
        circuitOpenUntil = 0L
    }

    @Synchronized
    private fun registerTransientFailure(reason: String) {
        consecutiveTransientFailures++
        if (consecutiveTransientFailures >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntil = SystemClock.elapsedRealtime() + CIRCUIT_COOLDOWN_MS
            consecutiveTransientFailures = 0
            Log.w(TAG, "$reason art arda tekrarlandı; AI 8 sn soğutuluyor, bu sırada offline çeviri kullanılacak.")
        } else {
            Log.w(TAG, "$reason; yalnız bu satır offline çevrildi.")
        }
    }

    suspend fun prepareOfflineModel() = offline.prepareModel()

    override fun close() {
        offline.close()
    }
}
