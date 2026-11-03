package com.example.subtitles

class SubtitleRuntime : AutoCloseable {
    private val baseTranslator = OfflineSubtitleTranslator(requireWifiForModelDownload = true)
    val translator: SubtitleTranslator = OfflinePolishingTranslator(baseTranslator)

    // 220 ms'lik eski debounce, hizli oyun altyazilarinda gereksiz bekleme yaratiyordu.
    val processor = SubtitleProcessor(
        translator = translator,
        debounceMillis = 40L,
        contextSize = 3
    )

    suspend fun prepare() {
        baseTranslator.prepareModel()
    }

    override fun close() {
        (translator as? AutoCloseable)?.close()
    }
}
