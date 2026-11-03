package com.example.subtitles

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SubtitleProcessor(
    private val translator: SubtitleTranslator,
    private val translationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMillis: Long = 40L,
    contextSize: Int = 3
) {
    private companion object {
        // Eski 600 ms bekleme hizli diyaloglarda altyaziyi geciktiriyordu.
        const val FRAGMENT_WAIT_MILLIS = 180L
    }

    private val context = SubtitleContextBuffer(contextSize)
    private val fragments = SubtitleFragmentBuffer()
    private val phraseLexicon = PhraseLexicon()
    private val mutex = Mutex()
    private var latest = ""

    suspend fun process(rawOcrText: String, maxLineLength: Int): String? {
        // Once HUD/gurultu ayiklanir, sonra mevcut OCR yazim duzeltmeleri uygulanir.
        val filtered = OcrTextCleaner.clean(rawOcrText)
        if (filtered.isBlank()) return null

        val cleaned = TextFix.onar(filtered).trim()
        if (cleaned.isBlank()) return null

        val duplicate = mutex.withLock {
            if (cleaned == latest) true
            else {
                latest = cleaned
                false
            }
        }
        if (duplicate) return null

        SoundCaptionFix.translateOrNull(cleaned)?.let {
            return TurkishTextFix.limitSubtitleLines(
                it,
                maxLineLength.coerceIn(30, 60)
            )
        }

        val fragment = fragments.offer(cleaned)
        val source = when (fragment) {
            is SubtitleFragmentBuffer.Result.Ready -> {
                if (debounceMillis > 0L) delay(debounceMillis)
                fragment.text
            }

            SubtitleFragmentBuffer.Result.AwaitNext -> {
                delay(FRAGMENT_WAIT_MILLIS)
                if (mutex.withLock { cleaned != latest }) return null
                fragments.flush() ?: return null
            }
        }

        val previous = context.context()
        val hints = phraseLexicon.formatHints(source)
        val translationContext = buildString {
            if (previous.isNotBlank()) {
                appendLine("PREVIOUS DIALOGUE:")
                appendLine(previous)
            }
            if (hints.isNotBlank()) {
                appendLine("KNOWN EXPRESSIONS:")
                appendLine(hints)
            }
            appendLine("TASK:")
            append(
                "Translate only the current English subtitle into natural Turkish. " +
                    "Use previous dialogue only to resolve meaning and references."
            )
        }

        val translated = withContext(translationDispatcher) {
            translator.translate(source, translationContext)
        }

        context.add(source)

        // Son duzeltme burada zorunlu uygulanir. Boylece Runtime veya translator
        // sarmalayicisi degisse bile GameTurkishPostEditor atlanamaz.
        val structurallyFixed = TurkishTextFix.onar(source, translated, maxLineLength)
        return GameTurkishPostEditor.polish(source, structurallyFixed)
    }

    suspend fun reset() {
        mutex.withLock { latest = "" }
        fragments.clear()
        context.clear()
    }
}
