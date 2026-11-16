package com.example.altyazi

/**
 * Cihazdaki tek cümlelik DeepL/OPUS/Google sonucunun bariz eksikleri.
 * Serbest kalite puanlaması değildir: sadece kayıtlarla doğrulanan kaybolan
 * içerik ve çevrilmeden kalan İngilizce kısaltmaları saptar.
 * Ham benchmark sonuçlarına uygulanmaz; canlı çeviri kapısında çalışır.
 */
object LiveSubtitleQuality10425 {
    enum class Defect { ENGLISH_RESIDUE, MISSING_DETAIL }

    // İngilizce yardımcı fiil kısaltmaları Türkçe altyazı içinde kalmamalı.
    // Tek başına "Mark", "Eve", "Cecil" gibi adları bu denetim etkilemez.
    private val contractions = Regex("""(?<![\p{L}])(?:isn['’]t|aren['’]t|wasn['’]t|weren['’]t|doesn['’]t|didn['’]t|couldn['’]t|wouldn['’]t|shouldn['’]t|what['’]s|who['’]s|you['’]re|they['’]re)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private data class RequiredMeaning(val source: Regex, val turkish: Regex)

    // Kaynakta kesin bulunan anlam bağı ile Türkçedeki açık izini karşılaştır.
    // Bu diziler mümkünse YALNIZ eksik ikinci repliğe uygulanmalı.
    private val required = listOf(
        RequiredMeaning(
            Regex("""\bi['’]ll be back for dessert\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:tatlı|dessert)""", RegexOption.IGNORE_CASE)
        ),
        RequiredMeaning(
            Regex("""\bhe['’]s just cautious\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:temkin|tedbir|ihtiyat|dikkatli|sakın|içine kapanık)""", RegexOption.IGNORE_CASE)
        ),
        RequiredMeaning(
            Regex("""\bwhere['’]s our payment\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:ödeme|ücret|paramız|para(?:mız)?|alacağımız)""", RegexOption.IGNORE_CASE)
        )
    )

    fun defect(source: String, translated: String): Defect? {
        if (contractions.containsMatchIn(translated)) return Defect.ENGLISH_RESIDUE
        for (rule in required) {
            if (rule.source.containsMatchIn(source) && !rule.turkish.containsMatchIn(translated))
                return Defect.MISSING_DETAIL
        }
        return null
    }
}
