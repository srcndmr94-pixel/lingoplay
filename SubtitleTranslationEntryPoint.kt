package com.example.subtitles

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** The single authoritative entry point for every spoken-subtitle translation. */
class SubtitleTranslationEntryPoint(
    private val processor: SubtitleProcessor,
    private val logger: SubtitleSessionLogger = SubtitleSessionLog
) {
    suspend fun process(rawOcr: String, sequence: Int = 42): String? {
        if (rawOcr.isBlank()) return null

        val normalized = OcrNormalizer.normalize(rawOcr)
        if (normalized.isBlank()) return null

        if (SubtitleUiFilter.shouldIgnore(normalized)) {
            logger.onIgnored(normalized, "controller_or_menu_prompt")
            return null
        }

        logger.onRead(normalized)
        return try {
            processor.process(normalized, sequence)?.also { translated ->
                if (translated.isNotBlank()) logger.onWritten(translated)
            }
        } catch (error: Throwable) {
            logger.onFailure(error.message ?: error.javaClass.simpleName)
            throw error
        }
    }
}

interface SubtitleSessionLogger {
    fun onRead(source: String)
    fun onWritten(translated: String)
    fun onIgnored(source: String, reason: String)
    fun onFailure(reason: String)
}

data class SubtitleSessionRecord(
    val id: Long,
    val type: Type,
    val text: String
) {
    enum class Type { READ, WRITTEN, IGNORED, FAILURE }
}

/**
 * Process-wide session store. The history screen can read snapshot() or renderText().
 * Android Log output keeps the exact OKUNAN/YAZILAN markers searchable in Logcat.
 */
object SubtitleSessionLog : SubtitleSessionLogger {
    private const val TAG = "SubtitleSession"
    private const val MAX_RECORDS = 300
    private val nextId = AtomicLong(1L)
    private val records = CopyOnWriteArrayList<SubtitleSessionRecord>()

    override fun onRead(source: String) = add(SubtitleSessionRecord.Type.READ, source, "OKUNAN")
    override fun onWritten(translated: String) = add(SubtitleSessionRecord.Type.WRITTEN, translated, "YAZILAN")
    override fun onIgnored(source: String, reason: String) {
        Log.d(TAG, "ATLANDI [$reason]: $source")
        add(SubtitleSessionRecord.Type.IGNORED, source, "ATLANDI")
    }
    override fun onFailure(reason: String) = add(SubtitleSessionRecord.Type.FAILURE, reason, "YAPAY ZEKÂ BAŞARISIZ")

    fun snapshot(): List<SubtitleSessionRecord> = records.toList()

    fun renderText(): String = records.joinToString("\n") { record ->
        when (record.type) {
            SubtitleSessionRecord.Type.READ -> "OKUNAN: ${record.text}"
            SubtitleSessionRecord.Type.WRITTEN -> "YAZILAN: ${record.text}"
            SubtitleSessionRecord.Type.IGNORED -> "ATLANDI: ${record.text}"
            SubtitleSessionRecord.Type.FAILURE -> "YAPAY ZEKÂ BAŞARISIZ: ${record.text}"
        }
    }

    fun clear() = records.clear()

    private fun add(type: SubtitleSessionRecord.Type, text: String, label: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val record = SubtitleSessionRecord(nextId.getAndIncrement(), type, clean)
        records += record
        while (records.size > MAX_RECORDS) records.removeAt(0)
        Log.i(TAG, "$label: $clean")
    }
}
