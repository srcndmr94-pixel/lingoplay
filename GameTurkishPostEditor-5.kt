package com.example.subtitles

import java.util.Locale

/**
 * Oyun altyazıları için hafif Türkçe son düzenleme katmanı.
 * Küfür/argo sansürlemez; yalnızca sık görülen makine çevirisi hatalarını düzeltir.
 */
object GameTurkishPostEditor {

    private val exactFixes = linkedMapOf(
        "Taşı, hareket et!" to "Yürü, yürü!",
        "Bırak!" to "Yerde kal!",
        "Lu, şınav." to "Lu, ilerle.",
        "Lu, kapak!" to "Lu, koru beni!",
        "Fuck, o o!" to "Siktir, o!",
        "BÜYÜM!" to "Hassiktir!"
    )

    private val phraseFixes = linkedMapOf(
        Regex("\\btaşı,\\s*hareket et\\b", RegexOption.IGNORE_CASE) to "yürü, yürü",
        Regex("\\bşınav\\b", RegexOption.IGNORE_CASE) to "ilerle",
        Regex("\\bkapak!", RegexOption.IGNORE_CASE) to "koru beni!",
        Regex("\\bsokak itici(?:ler)?\\b", RegexOption.IGNORE_CASE) to "sokak satıcıları",
        Regex("\\bav tüfeğigun\\b", RegexOption.IGNORE_CASE) to "av tüfeği"
    )

    fun polish(source: String, translated: String): String {
        if (translated.isBlank()) return translated

        var result = translated
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,;:.!?])"), "$1")
            .trim()

        exactFixes[result]?.let { result = it }
        phraseFixes.forEach { (pattern, replacement) ->
            result = pattern.replace(result, replacement)
        }

        result = fixContextSensitiveLines(source, result)
        result = removeAccidentalDuplicateTail(result)
        return preserveTerminalPunctuation(source, result)
    }

    private fun fixContextSensitiveLines(source: String, translation: String): String {
        val normalized = source.trim().lowercase(Locale.US)
        return when {
            normalized == "move, move!" -> "Yürü, yürü!"
            normalized == "stay down!" -> "Yerde kal!"
            normalized == "lu, push up. stay with me." -> "Lu, ilerle. Yanımdan ayrılma."
            normalized == "lu, cover!" -> "Lu, koru beni!"
            normalized == "holy shit!" -> "Hassiktir!"
            normalized == "fuck, it's him!" -> "Siktir, bu o!"
            else -> translation
        }
    }

    private fun removeAccidentalDuplicateTail(value: String): String {
        val parts = value.split(Regex("(?<=[.!?])\\s+"))
        if (parts.size < 2) return value
        val result = ArrayList<String>(parts.size)
        for (part in parts) {
            if (result.lastOrNull()?.equals(part, ignoreCase = true) != true) result += part
        }
        return result.joinToString(" ")
    }

    private fun preserveTerminalPunctuation(source: String, value: String): String {
        if (value.isBlank()) return value
        val terminal = source.trim().lastOrNull()?.takeIf { it == '!' || it == '?' || it == '.' }
            ?: return value
        return if (value.lastOrNull() in charArrayOf('!', '?', '.')) value else value + terminal
    }
}
