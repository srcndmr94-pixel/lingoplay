package com.example.subtitles

import android.util.Log

/**
 * Son savunma hattı. SubtitleProcessor veya başka bir çağrı yolu kullanılsa bile
 * normalizasyon ve UI filtresi atlanamaz.
 */
class GuardedSubtitleTranslator(
    private val delegate: SubtitleTranslator,
    private val logger: SubtitlePipelineLogger = LogcatSubtitlePipelineLogger
) : SubtitleTranslator {

    override suspend fun translate(source: String, context: String): String {
        val normalized = OcrNormalizer.normalize(source)
        if (normalized.isBlank()) {
            logger.ignored(source, "blank_after_normalization")
            return ""
        }

        if (SubtitleUiFilter.shouldIgnore(normalized)) {
            logger.ignored(normalized, "controller_or_menu_prompt")
            return ""
        }

        logger.read(normalized)
        return try {
            delegate.translate(normalized, context).trim().also { translated ->
                if (translated.isNotBlank()) logger.written(translated)
            }
        } catch (error: Throwable) {
            logger.failure(error.message ?: error.javaClass.simpleName)
            throw error
        }
    }
}

interface SubtitlePipelineLogger {
    fun read(source: String)
    fun written(translated: String)
    fun ignored(source: String, reason: String)
    fun failure(reason: String)
}

object LogcatSubtitlePipelineLogger : SubtitlePipelineLogger {
    private const val TAG = "SubtitlePipeline"
    override fun read(source: String) = Log.i(TAG, "OKUNAN: $source")
    override fun written(translated: String) = Log.i(TAG, "YAZILAN: $translated")
    override fun ignored(source: String, reason: String) =
        Log.i(TAG, "ATLANDI [$reason]: $source")
    override fun failure(reason: String) = Log.w(TAG, "ÇEVİRİ BAŞARISIZ: $reason")
}
