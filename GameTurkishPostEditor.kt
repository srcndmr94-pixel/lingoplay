package com.example.subtitles

/**
 * ML Kit çıktısındaki sık görülen oyun-komutu/deyim hatalarını, kaynak İngilizceyi de
 * dikkate alarak lokal ve deterministik biçimde düzeltir. Çevrimiçi çeviriye uygulanmaz.
 */
object GameTurkishPostEditor {
    private data class Rule(val source: Regex, val output: String)

    private val exactRules = listOf(
        Rule(Regex("(?i)^i['’]?m hit[.!]?$"), "Vuruldum!"),
        Rule(Regex("(?i)^stay down[.!]?$"), "Yerde kal!"),
        Rule(Regex("(?i)^move[,! ]+move[.!]?$"), "Hadi, hareket et!"),
        Rule(Regex("(?i)^come on[,! ]+come on[.!]?$"), "Hadi, hadi!"),
        Rule(Regex("(?i)^wrong way[,!. ]+this way[.!]?$"), "Yanlış taraf. Buradan!"),
        Rule(Regex("(?i)^fire at the powder keg[.!]?$"), "Barut fıçısına ateş et!"),
        Rule(Regex("(?i)^damn you[.!]?$"), "Kahretsin!"),
        Rule(Regex("(?i)^holy shit[.!]?$"), "Hassiktir!"),
        Rule(Regex("(?i)^ah[,! ]+shit[.!]?$"), "Ah, kahretsin!"),
        Rule(Regex("(?i)^what can i do for you[?!.]*$"), "Sana nasıl yardımcı olabilirim?"),
        Rule(Regex("(?i)^so[, ]+what['’]?ll you be needing[?!.]*$"), "Peki, ne lazım?"),
        Rule(Regex("(?i)^a shame[, ]+['’]?til next time then[.!]?$"), "Ne yazık. Artık bir dahaki sefere."),
        Rule(Regex("(?i)^have you business (?:to contract )?with me[?!.]*$"), "Benimle bir işin mi var?"),
        Rule(Regex("(?i)^looks like a real page[- ]turner[.!]?$"), "Sürükleyici bir şeye benziyor."),
        Rule(Regex("(?i)^i always been there for you[.!]?$"), "Ben hep senin yanındaydım."),
        Rule(Regex("(?i)^but this what i get[?!.]*$"), "Karşılığında bunu mu görüyorum?"),
        Rule(Regex("(?i)^pardon my delay[, ]+i was catching up on some reading[.!]?$"), "Geciktiğim için kusura bakma, biraz okuma yapıyordum."),
        Rule(Regex("(?i)^(?:yeah[.! ]+)?looks like a real page[- ]turner[.!]?$"), "Evet... Sürükleyici bir şeye benziyor.")
    )

    fun polish(sourceEnglish: String, mlKitTurkish: String): String {
        val source = sourceEnglish.replace(Regex("\\s+"), " ").trim()
        exactRules.firstOrNull { it.source.matches(source) }?.let { return it.output }

        var out = mlKitTurkish.trim()
        if (out.isBlank()) return out

        // Source-aware command corrections. Only apply when source intent is unambiguous.
        if (Regex("(?i)^police[!,. ]+").containsMatchIn(source) &&
            Regex("(?i)don't\\s+(?:fucking\\s+)?move").containsMatchIn(source)) {
            return "Polis! Sakın kıpırdama!"
        }
        if (Regex("(?i)^let['’]?s get these workers out of here").containsMatchIn(source)) {
            return "Hadi şu işçileri buradan çıkaralım!"
        }
        if (Regex("(?i)^a little help would be nice").containsMatchIn(source) ||
            Regex("(?i)^ah[, ]+a little help would be nice").containsMatchIn(source)) {
            return "Ah, biraz yardım etseniz fena olmaz!"
        }

        // Conservative Turkish cleanup; never rewrites names or whole sentences blindly.
        out = out.replace(Regex("\\s+([,.!?;:])"), "$1")
            .replace(Regex("([,.!?;:])(?=\\S)"), "$1 ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return out
    }
}
