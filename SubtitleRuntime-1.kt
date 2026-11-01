package com.example.subtitles

import android.content.Context

class SubtitleRuntime(context: Context) : AutoCloseable {
    private val apiKeyStore = ApiKeyStore(context)
    private val offline = OfflineSubtitleTranslator(requireWifiForModelDownload = true)
    private val online = GeminiSubtitleTranslator(apiKeyStore)
    val translator = TranslationRouter(online, offline, apiKeyStore)
    val processor = SubtitleProcessor(
        translator = translator,
        debounceMillis = 220L,
        contextSize = 3
    )

    suspend fun prepare() {
        translator.prepareOfflineModel()
    }

    override fun close() {
        translator.close()
    }
}
