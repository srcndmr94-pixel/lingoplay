package com.example.altyazi

/** A conservative trigger for independently complete source clauses whose second
 *  part may have vanished in an otherwise fluent translation. This is not a
 *  semantic correctness proof; a flagged line gets a bounded second attempt. */
object ClauseRecovery1042 {
    private val boundary = Regex("""(?<=[.!?])\s+(?=(?:[-–]\s*)?[\p{Lu}\[])""")
    private val targetBoundary = Regex("""[.!?]\s+(?=\S)""")
    private val joiningWords = Regex("""\b(?:ve|ama|fakat|ancak|çünkü|sonra|ardından)\b""", RegexOption.IGNORE_CASE)

    fun clauses(source: String): List<String>? {
        val pieces = source.trim().split(boundary).map { it.trim().trimStart('-', '–', ' ') }
        if (pieces.size != 2 || pieces.any { it.length < 6 }) return null
        // Exclude initials, sound-only tags and other too-short fragments.
        if (pieces.any { it.split(Regex("""\s+""")).size < 2 }) return null
        return pieces
    }

    fun needsSecondAttempt(source: String, translated: String, lostProfanity: Boolean = false): Boolean {
        if (clauses(source) == null) return false
        if (lostProfanity) return true
        if (source.length < 37 || source.split(Regex("""\s+""")).size < 8) return false
        if (targetBoundary.containsMatchIn(translated)) return false
        // Do not retranslate fluent, single-sentence Turkish joined with a conjunction.
        if (joiningWords.containsMatchIn(translated)) return false
        return translated.length < source.length * 0.85
    }
}
