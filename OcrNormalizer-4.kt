package com.example.subtitles

object OcrNormalizer {
    private val whitespace = Regex("[\\t ]+")
    private val rules = listOf(
        Regex("(?i)^l(?=['’])") to "I",
        Regex("(?i)\\bl['’]m\\b") to "I'm",
        Regex("(?i)\\bthe\\s+re['’]?s\\b") to "there's",
        Regex("(?i)\\bha['’]?nay\\b") to "haven't",
        Regex("(?i)\\bqot\\b") to "got",
        Regex("(?i)\\bldo\\b") to "I do",
        Regex("(?i)\\bwearsia\\b") to "wears a",
        Regex("(?i)\\bhustband\\b") to "husband",
        Regex("(?i)\\bstorim\\s*up\\b") to "storm up",
        Regex("(?i)\\btound\\b") to "round",
        Regex("(?i)\\bsent\\s+imental\\b") to "sentimental",
        Regex("(?i)\\btobe\\b") to "to be",
        Regex("(?i)\\bbabyabout\\b") to "baby about",
        Regex("(?i)\\beomfortin\\b") to "comfort in",
        Regex("(?i)\\btemplarsthere\\b") to "Templars there",
        Regex("(?i)\\bimust\\b") to "I must",
        Regex("(?i)^ou\\s+walked\\b") to "You walked",
        Regex("(?i)\\bcertai\\b") to "certain"
    )

    fun normalize(input: String): String = input.lineSequence()
        .map(::normalizeLine)
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()

    fun normalizeLine(raw: String): String {
        var text = raw
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('|', 'I')
            .trim()
        text = whitespace.replace(text, " ")
        text = Regex("\\s+([,.;:!?])").replace(text, "$1")
        text = Regex("(?<=\\s)1(?=\\s*[?.!,]|$)").replace(text, "I")
        text = Regex("(?<=\\s)l(?=[' ]|$)").replace(text, "I")
        for ((pattern, replacement) in rules) text = pattern.replace(text, replacement)
        return whitespace.replace(text, " ").trim()
    }
}
