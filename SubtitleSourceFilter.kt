package com.example.subtitles

object SubtitleSourceFilter {
    private val appUiPhrases = listOf(
        "tam dolu", "koyu gri", "lacivert", "turkuaz", "sarı", "sari",
        "nereye bakılsın", "nereye bakilsin", "kurulumu kontrol et", "durdur",
        "ingilizce de görünür", "ingilizce de gorunur"
    )

    fun shouldTranslate(text: String): Boolean {
        val value = text.lowercase().replace(Regex("\\s+"), " ").trim()
        if (value.isBlank()) return false
        if (appUiPhrases.any(value::contains)) return false

        // Turkish-specific letters are a strong signal that the app's own Turkish UI
        // leaked into the crop. Do not reject ASCII-only text because OCR-corrupted
        // English is often not dictionary-valid.
        if (value.any { it in "çğıöşü" }) return false

        return value.count(Char::isLetter) >= 2
    }
}
