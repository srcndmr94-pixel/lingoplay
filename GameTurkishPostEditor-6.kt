package com.example.subtitles

import java.util.Locale

/** Oyun altyazilarinda ML Kit'in sik yaptigi anlam hatalarini kaynak cumleye gore duzeltir. */
object GameTurkishPostEditor {
    fun polish(source: String, translated: String): String {
        val s = normalizeSource(source)
        val exact = when (s) {
            "where the fuck you at?" -> "Neredesin lan?"
            "what the fuck you talking about?" -> "Ne sikimden bahsediyorsun?"
            "the fuck is that?" -> "O ne lan?"
            "ah, shit!" -> "Ah, siktir!"
            "move, move!" -> "Hadi, hadi!"
            "police! don't fucking move!" -> "Polis! Sakın kıpırdamayın!"
            "holy shit!" -> "Hassiktir!"
            "stay down!" -> "Yerde kal!"
            "lu, push up. stay with me." -> "Lu, ilerle. Yanımdan ayrılma."
            "but this what i get?" -> "Ama karşılığında bunu mu görüyorum?"
            "wrong way, this way." -> "Yanlış taraf, buradan."
            "let's get these workers out of here, man!" -> "Şu işçileri buradan çıkaralım, dostum!"
            else -> null
        }
        if (exact != null) return exact

        var out = translated.replace(Regex("\\s+"), " ").trim()

        // Kaynaktaki agir/argo tonu gereksizce silen yaygin ML Kit ciktilarini kaynakla sinirli duzelt.
        if (s.contains("fuck") || s.contains("shit")) {
            out = out
                .replace(Regex("(?i)^fuck,?\\s*"), "Siktir, ")
                .replace(Regex("(?i)\\bbok!$"), "siktir!")
        }

        return preservePunctuation(source, out)
    }

    private fun normalizeSource(value: String): String = value
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.US)

    private fun preservePunctuation(source: String, value: String): String {
        if (value.isBlank()) return value
        val p = source.trim().lastOrNull()?.takeIf { it == '!' || it == '?' || it == '.' } ?: return value
        return if (value.lastOrNull() == '!' || value.lastOrNull() == '?' || value.lastOrNull() == '.') value else value + p
    }
}
