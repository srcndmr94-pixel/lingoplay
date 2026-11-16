package com.example.altyazi

/** English conversational grammar for translation engines, not a list of scene subtitles. */
object SpokenEnglish204 {
    private val dialect = listOf(
        Regex("""(?i)(?<![\p{L}])(?:y['’]?all|ya['’]?ll)(?![\p{L}])""") to "you",
        Regex("""(?i)(?<![\p{L}])whatchu(?![\p{L}])""") to "what do you",
        Regex("""(?i)(?<![\p{L}])whatcha(?![\p{L}])""") to "what are you",
        Regex("""(?i)(?<![\p{L}])tryna(?![\p{L}])""") to "trying to",
        Regex("""(?i)(?<![\p{L}])aight(?![\p{L}])""") to "alright",
        Regex("""(?i)(?<![\p{L}])bruh(?![\p{L}])""") to "bro",
        Regex("""(?i)(?<![\p{L}])fo\s+sho(?![\p{L}])""") to "for sure",
        Regex("""(?i)(?<![\p{L}])yo(?![\p{L}])""") to "hey",
    )

    // English exclamations and pronouns are not names just because they are
    // capitalized at the beginning of an OCR subtitle.
    private val notNames = setOf(
        "yall", "whatchu", "whatcha", "tryna", "aight", "bruh", "yo", "hey",
        "gonna", "wanna", "lemme", "gimme", "nah", "yeah", "huh", "oops"
    )

    private val leftover = Regex("""(?i)(?<![\p{L}])(?:y['’]?all|ya['’]?ll|whatchu|whatcha|tryna|aight|bruh|fo\s+sho)(?![\p{L}])""")

    fun isSpokenGrammar(word: String): Boolean = word.lowercase() in notNames
    fun hasUntranslatedSlang(turkish: String): Boolean = leftover.containsMatchIn(turkish)

    /** Normalize only the engine input; never overwrite source OCR or logged context. */
    fun forEngine(source: String): String {
        var cleaned = source
        for ((pattern, replacement) in dialect) cleaned = pattern.replace(cleaned, replacement)
        return cleaned
    }
}
