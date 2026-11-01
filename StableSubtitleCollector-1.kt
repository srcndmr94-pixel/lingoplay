package com.example.subtitles

import kotlin.math.max

/**
 * OCR parçalarını birkaç kare boyunca karşılaştırır; uzun metni tamamlanmadan göndermez.
 * Kullanım: collector.offer(normalizedText) null döndürürse bekle, metin döndürürse çevir.
 */
class StableSubtitleCollector(
    private val shortStableFrames: Int = 2,
    private val longStableFrames: Int = 2,
    private val missingTolerance: Int = 1
) {
    private var best = ""
    private var last = ""
    private var stableCount = 0
    private var missingCount = 0
    private var lastEmitted = ""

    fun offer(value: String): String? {
        val text = normalize(value)
        if (text.isBlank()) {
            missingCount++
            if (missingCount > missingTolerance) resetCandidate()
            return null
        }
        missingCount = 0

        val similarity = similarity(last, text)
        stableCount = if (similarity >= 0.84f) stableCount + 1 else 1
        last = text
        if (quality(text) >= quality(best) || text.contains(best, ignoreCase = true)) best = text

        val wordCount = best.split(Regex("\\s+")).count { it.isNotBlank() }
        val required = if (best.length >= 72 || wordCount >= 10 || best.count { it == '\n' } >= 2) {
            longStableFrames
        } else {
            shortStableFrames
        }
        if (stableCount < required) return null

        val result = best.trim()
        if (result == lastEmitted || nearDuplicate(result, lastEmitted)) return null
        lastEmitted = result
        resetCandidate()
        return result
    }

    fun reset() {
        lastEmitted = ""
        resetCandidate()
    }

    private fun resetCandidate() {
        best = ""
        last = ""
        stableCount = 0
        missingCount = 0
    }

    private fun normalize(value: String) = value
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun quality(value: String): Int =
        value.count(Char::isLetter) * 2 + value.length - value.count { it in "|{}[]<>" }

    private fun nearDuplicate(a: String, b: String): Boolean =
        b.isNotBlank() && similarity(a, b) >= 0.94f

    private fun similarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        val left = bigrams(a.lowercase())
        val right = bigrams(b.lowercase())
        if (left.isEmpty() || right.isEmpty()) return if (a.equals(b, true)) 1f else 0f
        val shared = left.intersect(right).size
        return (2f * shared) / max(1, left.size + right.size)
    }

    private fun bigrams(value: String): Set<String> =
        value.windowed(2, 1).toSet()
}
