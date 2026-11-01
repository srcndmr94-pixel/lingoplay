package com.example.subtitles

object SubtitleUiFilter {
    private val psSymbol = "[▲△○◯×✕□■]"
    private val namedButton = "(?:L1|L2|L3|R1|R2|R3|LT|RT|LB|RB|D-?PAD|OPTIONS?|SHARE|TOUCHPAD)"
    private val faceButton = "(?:A|B|X|Y|O)"
    private val action =
        "(?:carry|drop|wait|wat|dock|exit|interact|use|take|open|close|select|back|cancel|confirm|observe|follow|talk|climb|jump|sprint|reload|attack|block|parry|dodge|mark|place|remove|customize|continue)"

    private val controllerPrompt = Regex(
        "^\\s*[()]*\\s*(?:$psSymbol|$namedButton|$faceButton)\\s*[()]*\\s+$action\\b.*[.!]?$",
        RegexOption.IGNORE_CASE
    )

    private val explicitNamedPrompt = Regex(
        "^\\s*[()]*\\s*(?:$psSymbol|$namedButton)\\s*[()]*\\s+.+$",
        RegexOption.IGNORE_CASE
    )

    private val menuPrompt = Regex(
        "^\\s*[()]*\\s*(?:(?:see\\s+)?shop\\s+exit|(?:exit\\s+)?see\\s+shop|world\\s+map(?:\\s+observe)?|observe\\s+(?:the\\s+)?world\\s+map|use\\s+(?:$psSymbol|$namedButton|$faceButton)\\s+to\\s+.+)\\s*[.!]?$",
        RegexOption.IGNORE_CASE
    )

    fun shouldIgnore(text: String): Boolean {
        val value = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (value.isBlank()) return true
        return controllerPrompt.matches(value) ||
            explicitNamedPrompt.matches(value) ||
            menuPrompt.matches(value)
    }
}
