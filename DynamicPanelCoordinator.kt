package com.example.subtitles

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DynamicPanelCoordinator(
    private val recognizer: DynamicTextRecognizer,
    private val panelTranslator: SubtitleTranslator,
    private val overlay: DynamicPanelOverlay,
    private val detector: DynamicPanelDetector = DynamicPanelDetector(),
    private val tracker: PanelTracker = PanelTracker(),
    private val cache: PanelTranslationCache = PanelTranslationCache()
) {
    suspend fun process(frame: Bitmap) {
        val blocks = recognizer.recognizeBlocks(frame)
        val update = tracker.update(detector.detect(blocks))

        withContext(Dispatchers.Main) {
            update.removedIds.forEach(overlay::remove)
        }

        for (panel in update.stable) {
            val translated = cache.get(panel.sourceText)
                ?: translatePanel(panel.sourceText).also {
                    cache.put(panel.sourceText, it)
                }

            withContext(Dispatchers.Main) {
                overlay.show(panel.id, panel.bounds, translated)
            }
        }
    }

    private suspend fun translatePanel(source: String): String {
        val parsed = source.lineSequence()
            .map(ControllerPromptParser::parse)
            .toList()

        val translatable = parsed
            .map { it.label }
            .filter(String::isNotBlank)
            .joinToString("\n")

        if (translatable.isBlank()) return source

        val translated = panelTranslator.translate(
            translatable,
            "Translate this game objective or information panel into concise natural Turkish. " +
                "Return only translated lines in the same order. Preserve numbers."
        )

        val translatedLines = translated.lines().iterator()
        return parsed.joinToString("\n") { item ->
            val line = if (translatedLines.hasNext()) translatedLines.next() else item.label
            ControllerPromptParser.restore(item.prefix, line)
        }
    }
}
