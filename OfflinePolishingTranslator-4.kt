package com.example.subtitles

/**
 * Mevcut SubtitleTranslator sonucunu oyun altyazısına uygun biçimde düzenler.
 * Ek ağ isteği oluşturmaz; context parametresini olduğu gibi asıl çevirmene iletir.
 */
class OfflinePolishingTranslator(
    private val delegate: SubtitleTranslator
) : SubtitleTranslator, AutoCloseable {

    override suspend fun translate(source: String, context: String): String {
        if (source.isBlank()) return ""
        val translated = delegate.translate(source, context)
        return GameTurkishPostEditor.polish(source, translated)
    }

    override fun close() {
        (delegate as? AutoCloseable)?.close()
    }
}
