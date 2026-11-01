package com.example.subtitles

/**
 * Filters controller/menu prompts that are commonly picked up by subtitle OCR.
 * The filter is structural: it does not contain game-specific dialogue sentences.
 */
object SubtitleUiFilter {
    private val psSymbol = "[▲△○◯×✕□■]"
    private val namedButton = "(?:L1|L2|L3|R1|R2|R3|LT|RT|LB|RB|D-?PAD|OPTIONS?|SHARE|TOUCHPAD)"
    private val xboxButton = "(?:A|B|X|Y)"

    private val explicitControllerPrompt = Regex(
        "^\\s*[()]*\\s*(?:$psSymbol|$namedButton)\\s*[()]*\\s+.+$",
        RegexOption.IGNORE_CASE
    )

    private val xboxStylePrompt = Regex(
        "^\\s*[()]*\\s*$xboxButton\\s*[()]*\\s+(?:carry|wait|wat|dock|exit|interact|use|take|drop|open|close|select|back|cancel|confirm|observe|follow|talk|climb|jump|sprint|reload|attack|block|parry|dodge|mark|place|remove)\\b.*$",
        RegexOption.IGNORE_CASE
    )

    private val menuPrompt = Regex(
        "^\\s*[()]*\\s*(?:(?:see\\s+)?shop\\s+exit|(?:exit\\s+)?see\\s+shop|world\\s+map(?:\\s+observe)?|observe\\s+(?:the\\s+)?world\\s+map|use\\s+[A-Z○◯×✕□■▲△]\\s+to\\s+.+)\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun shouldIgnore(text: String): Boolean {
        val normalized = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return true
        if (explicitControllerPrompt.matches(normalized)) return true
        if (xboxStylePrompt.matches(normalized)) return true
        if (menuPrompt.matches(normalized)) return true
        return false
    }
}
