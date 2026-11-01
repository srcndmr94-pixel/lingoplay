package com.example.subtitles

/**
 * OCR sonucunda diyaloğa karışan HUD / rastgele karakter gürültüsünü temizler.
 * Güvenli yaklaşım: yalnız yüksek güvenli örüntülerde siler; gerçek diyaloğu tahmin ederek yeniden yazmaz.
 */
object OcrSourceCleaner {

    private val hudWholeLinePatterns = listOf(
        Regex("^\\d*\\s*MORE\\s+LAND\\s+\\d+\\s+SHOTGUN$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+SHOTGUN$", RegexOption.IGNORE_CASE),
        Regex("^MORE\\s+LAND\\s+\\d+\\s+SHOTGUN$", RegexOption.IGNORE_CASE)
    )

    private val knownDialogueStarts = listOf(
        "Come on,",
        "I got ",
        "Fuck Boobie!",
        "Yep.",
        "Lu,",
        "What the fuck",
        "Fuck,",
        "Can't you",
        "Hey man,",
        "Aww,",
        "Clean this",
        "It's fucking",
        "Police!",
        "Stay down!",
        "I'm hit!"
    )

    fun clean(raw: String): String {
        var text = OcrNormalizer.normalize(raw)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isBlank()) return ""
        if (hudWholeLinePatterns.any { it.matches(text) }) return ""
        if (text.equals("Kurulumu kontrol et", ignoreCase = true)) return ""

        // 10 SHOTGUN + gerçek diyalog gibi bilinen ön ekleri kaldır.
        text = text.replace(
            Regex("^\\d+\\s+SHOTGUN\\s+(?=[A-Za-z])", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // OCR gürültüsü gerçek bir bilinen repliğin önüne yapışmışsa yalnız ön kısmı at.
        val start = knownDialogueStarts
            .map { marker -> text.indexOf(marker, ignoreCase = true) }
            .filter { it > 0 }
            .minOrNull()
        if (start != null) text = text.substring(start).trim()

        return text
    }
}
