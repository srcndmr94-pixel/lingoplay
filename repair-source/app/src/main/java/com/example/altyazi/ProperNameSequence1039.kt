package com.example.altyazi

/**
 * Protects likely two-token personal names without maintaining a list of
 * individual characters or hardcoding any title-specific translations.
 *
 * This is intentionally conservative: all-caps words, titles and common
 * sentence starters are not evidence of a person's name. The calling shield
 * still handles one-token names using its dictionary and session memory.
 */
object ProperNameSequence1039 {
    private val titleCaseWord = Regex("\\p{Lu}\\p{Ll}{2,}")
    private val words = Regex("\\b\\p{Lu}\\p{Ll}{2,}\\b")
    private val leadingFunctionWords = setOf(
        "the", "this", "that", "those", "these", "there", "here", "what", "when",
        "where", "why", "how", "who", "your", "their", "our", "his", "her",
        "you", "they", "she", "we", "all", "any", "some", "every", "none",
        "but", "and", "for", "from", "with", "after", "before", "then",
        "well", "now", "look", "listen", "wait", "good", "great", "nice",
        "old", "new", "young", "one", "two", "many", "more", "most",
        "emperor", "general", "regent", "professor", "master", "captain",
        "commander", "president", "doctor", "mister", "miss", "king", "queen"
    )
    private val trailingFunctionWords = setOf(
        "the", "this", "that", "those", "these", "your", "their", "our",
        "was", "were", "are", "has", "have", "had", "can", "could", "will",
        "would", "should", "did", "does", "not", "now", "then", "here",
        "there", "right", "wrong", "what", "when", "where", "just"
    )

    /** Character positions of words that belong to a likely two-word name. */
    fun protectedStarts(text: String): Set<Int> {
        val found = words.findAll(text).toList()
        val positions = HashSet<Int>()
        for (i in 0 until found.lastIndex) {
            val first = found[i]
            val second = found[i + 1]
            val gap = text.substring(first.range.last + 1, second.range.first)
            if (gap.isEmpty() || !gap.all(Char::isWhitespace)) continue
            if (!titleCaseWord.matches(first.value) || !titleCaseWord.matches(second.value)) continue
            if (first.value.lowercase() in leadingFunctionWords ||
                second.value.lowercase() in trailingFunctionWords) continue
            positions += first.range.first
            positions += second.range.first
        }
        return positions
    }
}
