package com.example.subtitles

class ApiKeyProviderAdapter(
    private val delegate: ApiKeyStore
) : ApiKeyProvider {
    override fun hasKey(): Boolean = delegate.hasKey()
}

class OfflineTranslationEngineAdapter(
    private val delegate: OfflineSubtitleTranslator
) : OfflineTranslationEngine {
    override suspend fun translate(source: String, context: String): String =
        delegate.translate(source, context)

    override suspend fun prepareModel() = delegate.prepareModel()

    override fun close() = delegate.close()
}
