package com.example.altyazi

/**
 * Genel amaçlı doğal çeviri seçimi; belirli oyun, film veya replik içermez.
 * Hızlı modun gecikme beklentisi değişmez. Dengeli modda güçlü motor varsa
 * aynı kaynağı farklı bağlamlarda sabit oyun çevirisine kilitlemeyiz.
 */
object NaturalTranslationPolicy10427 {
    private val soundOnly = Regex("""^\[[^]]{1,80}]$""")

    private val contextSensitive = Regex(
        """\b(?:it|its|this|that|these|those|they|them|their|theirs|he|him|his|she|her|hers|we|us|our|here|there|one|ones)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Önceki repliği değişince anlamı değişebilecek satır geçmişten körlemesine alınmaz. */
    fun dependsOnContext(source: String): Boolean = contextSensitive.containsMatchIn(source)

    fun modelFirst(isBalanced: Boolean, strongEngineAvailable: Boolean, source: String): Boolean {
        val body = source.trim()
        return isBalanced && strongEngineAvailable &&
            body.isNotEmpty() && body.any { it.isLetter() } &&
            !soundOnly.matches(body)
    }
}
