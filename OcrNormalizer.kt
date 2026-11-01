package com.example.subtitles

/**
 * Conservative OCR cleanup for English game subtitles.
 * It fixes OCR-shaped corruption before translation without hard-coding dialogue sentences.
 */
object OcrNormalizer {
    private val whitespace = Regex("[\\t ]+")
    private val spacesBeforePunctuation = Regex("\\s+([,.;:!?])")
    private val spacesAfterOpening = Regex("([([{])\\s+")
    private val spacesBeforeClosing = Regex("\\s+([)\\]}])")

    private val replacements = listOf(
        Regex("(?i)\\bhas\\s+n['’]?treturned\\b") to "hasn't returned",
        Regex("(?i)\\bhave\\s+n['’]?treturned\\b") to "haven't returned",
        Regex("(?i)\\bdoes\\s+n['’]?t\\b") to "doesn't",
        Regex("(?i)\\bdid\\s+n['’]?t\\b") to "didn't",
        Regex("(?i)\\bwould\\s+n['’]?t\\b") to "wouldn't",
        Regex("(?i)\\bcould\\s+n['’]?t\\b") to "couldn't",
        Regex("(?i)\\bshould\\s+n['’]?t\\b") to "shouldn't",
        Regex("(?i)\\bcan\\s+n['’]?t\\b") to "can't",
        Regex("(?i)\\bwearsia\\b") to "wears a",
        Regex("(?i)\\bhustband\\b") to "husband",
        Regex("(?i)\\bstorim\\s*up\\b") to "storm up",
        Regex("(?i)\\bstorimup\\b") to "storm up",
        Regex("(?i)\\btound\\b") to "round",
        Regex("(?i)\\bsecurity\\b") to "security",
        Regex("(?i)\\bsecurty\\b") to "security",
        Regex("(?i)\\bhand\\s+ling\\b") to "handling",
        Regex("(?i)\\bgo?sts\\b") to "costs",
        Regex("(?i)\\baandling\\b") to "handling",
        Regex("(?i)\\bwhata\\b") to "what a",
        Regex("(?i)\\bsent\\s+imental\\b") to "sentimental",
        Regex("(?i)\\bdeviish\\s*synmetry\\b") to "devilish symmetry",
        Regex("(?i)\\btobe\\b") to "to be",
        Regex("(?i)\\bbabyabout\\b") to "baby about",
        Regex("(?i)\\bigot\\b") to "I got",
        Regex("(?i)\\bnota\\s+word\\b") to "not a word"
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        return input.lineSequence()
            .map(::normalizeLine)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
    }

    fun normalizeLine(raw: String): String {
        var text = raw
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('|', 'I')
            .trim()

        text = whitespace.replace(text, " ")
        text = spacesBeforePunctuation.replace(text, "$1")
        text = spacesAfterOpening.replace(text, "$1")
        text = spacesBeforeClosing.replace(text, "$1")

        // OCR frequently reads a leading capital-I as lowercase l before an apostrophe/space.
        text = text.replace(Regex("^l(?=[' ]|$)"), "I")
        text = text.replace(Regex("(?<=\\s)l(?=[' ]|$)"), "I")
        // OCR can swap the two glyphs in contractions/word joins.
        text = text.replace(Regex("^'l(?=\\s)", RegexOption.IGNORE_CASE), "I'll")
        text = text.replace(Regex("(?i)\\bmightl\\b"), "might I")

        for ((pattern, replacement) in replacements) {
            text = pattern.replace(text, replacement)
        }

        // Repair a few high-frequency English contractions split by OCR.
        text = text.replace(Regex("(?i)\\b(I|you|we|they|he|she|it)\\s+'\\s*(m|re|ve|ll|d|s)\\b")) {
            "${it.groupValues[1]}'${it.groupValues[2]}"
        }
        return whitespace.replace(text, " ").trim()
    }
}
