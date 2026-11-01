package com.example.subtitles

import android.util.Log
import java.net.SocketTimeoutException

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
            // 400/401/403/404 are request/configuration errors: do not retry unchanged.
            Log.w("SubtitleAI", "Online çeviri HTTP ${error.statusCode}; çevrimdışı çeviriye geçildi.")
            offline.translate(source, context)
        } catch (error: SocketTimeoutException) {
            Log.w("SubtitleAI", "Online çeviri zaman aşımı; çevrimdışı çeviriye geçildi.")
            offline.translate(source, context)
        } catch (error: Exception) {
            Log.w("SubtitleAI", "Online çeviri başarısız; çevrimdışı çeviriye geçildi.", error)
            offline.translate(source, context)
        }
    }

    suspend fun prepareOfflineModel() = offline.prepareModel()
    override fun close() = offline.close()
}
