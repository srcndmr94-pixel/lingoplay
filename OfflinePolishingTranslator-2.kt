package com.example.subtitles

/** Wraps only the offline engine, leaving Gemini output untouched. */
class OfflinePolishingTranslator(
    private val delegate: OfflineTranslationEngine
) : OfflineTranslationEngine {
    override suspend fun translate(source: String, context: String): String {
        val raw = delegate.translate(source, context)
        return GameTurkishPostEditor.polish(source, raw)
    }

    override suspend fun prepareModel() = delegate.prepareModel()
    override fun close() = delegate.close()
}
