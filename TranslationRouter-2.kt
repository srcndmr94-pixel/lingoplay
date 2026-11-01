package com.example.subtitles

import android.util.Log
import kotlinx.coroutines.delay
import java.net.SocketTimeoutException
import kotlin.random.Random

class TranslationRouter(
    private val online: SubtitleTranslator,
    private val offline: OfflineSubtitleTranslator,
    private val apiKeyStore: ApiKeyStore
) : SubtitleTranslator, AutoCloseable {
    override suspend fun translate(source: String, context: String): String {
        if (!apiKeyStore.hasKey()) return offline.translate(source, context)
        return try {
            online.translate(source, context)
        } catch (error: GeminiHttpException) {
            when {
                error.statusCode == 429 || error.statusCode >= 500 -> retryOnceThenOffline(source, context, error)
                else -> offline.translate(source, context) // 400/401/403: unchanged retry is pointless
            }
        } catch (error: SocketTimeoutException) {
            Log.w("SubtitleAI", "Online timeout; anında çevrimdışı çeviri.")
            offline.translate(source, context)
        } catch (error: Exception) {
            Log.w("SubtitleAI", "Online çeviri başarısız; çevrimdışı çeviri.", error)
            offline.translate(source, context)
        }
    }

    private suspend fun retryOnceThenOffline(source: String, context: String, first: Exception): String {
        delay(650L + Random.nextLong(0, 250))
        return try {
            online.translate(source, context)
        } catch (second: Exception) {
            Log.w("SubtitleAI", "Geçici API hatası tekrarlandı; çevrimdışı çeviri.", second)
            offline.translate(source, context)
        }
    }

    suspend fun prepareOfflineModel() = offline.prepareModel()
    override fun close() = offline.close()
}
