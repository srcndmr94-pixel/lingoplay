package com.example.altyazi

/**
 * A translation memory keyed only by English text cannot distinguish contexts.
 * Do not reuse/cache short, elliptical or unfinished utterances globally.
 * Stable full sentences and explicit glossary hits keep their fast paths.
 */
object TranslationCachePolicy10427 {
    private val speaker = Regex("""^\[[^]]{1,50}]\s*""")
    private val clauseOpening = Regex("""^(?:and|but|or|because|if|when|while|like|as|then)\b""", RegexOption.IGNORE_CASE)

    fun reusable(source: String): Boolean {
        val body = source.trim().replace(speaker, "").trim()
        if (body.isBlank() || body.length > 200) return false
        if (body.contains("...") || body.contains('…')) return false
        if (body.last() !in ".!?") return false
        val words = Regex("""[\p{L}]+(?:['’][\p{L}]+)?""").findAll(body).count()
        if (words <= 3 || clauseOpening.containsMatchIn(body)) return false
        return true
    }
}
