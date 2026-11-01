package com.example.subtitles

/**
 * Herhangi bir SubtitleTranslator'ı cache ile sarar.
 * Aynı kaynak+bağlam tekrar geldiğinde ağ/ML Kit çağrısı yapılmaz.
 */
class CachingSubtitleTranslator(
    private val delegate: SubtitleTranslator,
    private val cache: TranslationCache,
    private val engineKey: String
) : SubtitleTranslator {

    override suspend fun translate(source: String, context: String): String {
        cache.get(source, context, engineKey)?.let { return it }
        val translated = delegate.translate(source, context)
        cache.put(source, translated, context, engineKey)
        return translated
    }
}
