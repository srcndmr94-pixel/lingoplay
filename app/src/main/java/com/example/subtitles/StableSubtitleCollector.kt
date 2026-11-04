package com.example.subtitles

class StableSubtitleCollector(
    private val shortStableFrames: Int = 2,
    private val longStableFrames: Int = 3,
    private val missingTolerance: Int = 1,
    private val longTextThreshold: Int = 54
) {
    init {
        require(shortStableFrames >= 1)
        require(longStableFrames >= 1)
        require(missingTolerance >= 0)
    }

    private var candidate = ""
    private var stableFrames = 0
    private var missingFrames = 0
    private var emitted = ""

    fun offer(rawText: String): String? {
        val text = normalize(rawText)
        if (text.isBlank()) {
            missingFrames++
            if (missingFrames > missingTolerance) resetCandidate()
            return null
        }
        missingFrames = 0

        when {
            candidate.isBlank() -> {
                candidate = text
                stableFrames = 1
            }
            sameText(candidate, text) -> {
                candidate = preferRicher(candidate, text)
                stableFrames++
            }
            isGrowth(candidate, text) -> {
                candidate = text
                stableFrames = 1
            }
            isGrowth(text, candidate) -> {
                // A shorter OCR frame must not immediately replace a richer current subtitle.
                stableFrames = 0
                return null
            }
            else -> {
                candidate = text
                stableFrames = 1
            }
        }

        val needed = if (candidate.length >= longTextThreshold || candidate.count { it == ' ' } >= 9) longStableFrames else shortStableFrames
        if (stableFrames < needed) return null
        if (sameText(candidate, emitted)) return null

        emitted = candidate
        return candidate
    }

    fun clear() {
        candidate = ""
        stableFrames = 0
        missingFrames = 0
        emitted = ""
    }

    private fun resetCandidate() {
        candidate = ""
        stableFrames = 0
    }

    private fun normalize(value: String) = value
        .lineSequence().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        .replace(Regex("\\s+"), " ").trim()

    private fun sameText(a: String, b: String) = a.equals(b, ignoreCase = true)

    private fun isGrowth(shorter: String, longer: String): Boolean {
        if (longer.length <= shorter.length) return false
        val a = shorter.lowercase()
        val b = longer.lowercase()
        return b.startsWith(a) || b.contains(a)
    }

    private fun preferRicher(a: String, b: String) = if (b.length >= a.length) b else a
}
