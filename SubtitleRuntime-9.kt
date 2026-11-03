package com.example.subtitles

/**
 * Çevrimdışı ML Kit çevirmenini hazırlar ve son düzenleme katmanını
 * SubtitleProcessor üzerinden kullanıma açar.
 */
class SubtitleRuntime : AutoCloseable {
    private val baseTranslator = OfflineSubtitleTranslator(
        requireWifiForModelDownload = true
    )

    val translator: SubtitleTranslator = OfflinePolishingTranslator(baseTranslator)

    val processor = SubtitleProcessor(
        translator = translator,
        debounceMillis = 80L,
        contextSize = 3
    )

    suspend fun prepare() {
        baseTranslator.prepareModel()
    }

    override fun close() {
        (translator as? AutoCloseable)?.close()
    }
}
