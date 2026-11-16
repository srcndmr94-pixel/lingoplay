package com.example.altyazi

/**
 * Conservative checks for mechanically detectable meaning reversals.
 * This is NOT a translation model: it flags only cases whose source and
 * target grammar provide strong evidence of a dangerous mismatch.
 */
object MeaningSafety {
    enum class Risk {
        REVERSED_NEGATIVE_COMMAND,
        LOST_PROHIBITION,
        IMPERATIVE_AS_INFINITIVE
    }

    private val zeroSurvivors = Regex("""^(?:\[[^]]+]\s*)?(?:(?:leave|keep)\s+(?:no\s+one|nobody)\s+(?:alive|breathing)|spare\s+(?:no\s+one|nobody))[.!\s]*$""", RegexOption.IGNORE_CASE)
    // Not a broad search for 'kimse': that word also appears in the WRONG
    // affirmative instruction 'Kimseyi canlı bırak.'
    private val positiveMercy = Regex("""^(?:hiç\s+)?(?:kimseyi|birini|onları|hepsini)\s+(?:(?:sağ|canlı)\s+)?(?:bırak|bağışla|affet)(?:ın|ınız)?[.!\s]*$""", RegexOption.IGNORE_CASE)
    private val englishProhibition = Regex("""^(?:\[[^]]+]\s*)?(?:do\s+not|don['’]t|never)\s+(?!(?:worry|mind)\b)[a-z]""", RegexOption.IGNORE_CASE)
    private val sourceImperative = Regex("""^(?:\[[^]]+]\s*)?(?:keep|go|come|move|stop|run|follow|leave|shut|hold|stay|take|look|listen|shoot|wait)\b""", RegexOption.IGNORE_CASE)
    private val infinitiveEnding = Regex("""\b[\p{L}]+m[ae]k\s*[!.]*$""", RegexOption.IGNORE_CASE)

    // Negative verbs in Turkish: -ma/-me, -mıyor, -mam, -mayın, -mesin,
    // plus 'sakın', and common negative lexical forms. Do not mistake
    // 'kimseyi' alone for the prohibition: it isn't sufficient evidence.
    private val turkishProhibition = Regex("""(?:\b(?:sakın|asla|hiçbir|değil|yok|hayır)\b|\b[\p{L}]+(?:ma|me)(?:\b|[mydznslıiuüae][\p{L}]*)|\b[\p{L}]+m[ıiuü]yor[\p{L}]*\b)""", RegexOption.IGNORE_CASE)

    fun risk(source: String, target: String): Risk? {
        val s = source.trim()
        val t = target.trim().replace(Regex("""^\[[^]]+]\s*"""), "")
        if (s.isBlank() || t.isBlank()) return null
        if (zeroSurvivors.matches(s) && positiveMercy.matches(t))
            return Risk.REVERSED_NEGATIVE_COMMAND
        if (englishProhibition.containsMatchIn(s) && !turkishProhibition.containsMatchIn(t))
            return Risk.LOST_PROHIBITION
        // "Keep going!" must not become the infinitive "Devam etmek!".
        if (s.trimEnd().endsWith('!') && sourceImperative.containsMatchIn(s) &&
            infinitiveEnding.containsMatchIn(t))
            return Risk.IMPERATIVE_AS_INFINITIVE
        return null
    }
}
