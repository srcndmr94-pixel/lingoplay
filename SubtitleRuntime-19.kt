package com.example.subtitles

/**
 * Tek ve acik ceviri zinciri:
 * OfflineSubtitleTranslator -> SubtitleProcessor -> GameTurkishPostEditor
 */
class SubtitleRuntime : AutoCloseable {
    private val baseTranslator = OfflineSubtitleTranslator(
        requireWifiForModelDownload = true
    )

    // Post-editor SubtitleProcessor'in son adiminda zorunlu calisir.
    // Boylece farkli translator sarmalayicilarinin zinciri atlamasi onlenir.
    val translator: SubtitleTranslator = baseTranslator

    val processor = SubtitleProcessor(
        translator = translator,
        debounceMillis = 40L,
        contextSize = 3
    )

    suspend fun prepare() {
        baseTranslator.prepareModel()
    }

    override fun close() {
        baseTranslator.close()
    }
}
