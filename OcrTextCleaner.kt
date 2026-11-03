package com.example.subtitles

import java.util.Locale

/**
 * Ham OCR sonucunu ceviriye gondermeden once temizler.
 *
 * Ilkeler:
 * - Bozuk metne tahmini anlam yuklemez.
 * - HUD/silah/mermi/sayac satirlarini ayiklar.
 * - Altyaziya benzeyen satirlari korur.
 * - Agir duzeltmeyi TextFix katmanina birakir.
 */
object OcrTextCleaner {

    private val whitespace = Regex("[\\t ]+")
    private val repeatedPunctuation = Regex("([!?.,])\\1{1,}")
    private val isolatedNoise = Regex("^[^A-Za-z0-9]+$")
    private val mostlyNumbers = Regex("^[\\s\\d:/%+_.-]+$")

    private val hudWords = setOf(
        "ammo", "armor", "armour", "health", "score", "shotgun", "pistol",
        "rifle", "smg", "weapon", "reload", "round", "objective", "checkpoint"
    )

    /** Ana kullanim noktasi. Bos sonuc, karenin ceviriye gonderilmemesi demektir. */
    fun clean(rawText: String): String {
        if (rawText.isBlank()) return ""

        val candidates = rawText
            .lineSequence()
            .map(::normalizeLine)
            .filter(String::isNotBlank)
            .filterNot(::isNoiseLine)
            .distinctBy(::comparisonKey)
            .toList()

        if (candidates.isEmpty()) return ""

        val subtitleLines = candidates
            .filter { subtitleScore(it) >= MIN_SUBTITLE_SCORE }
            .takeLast(MAX_SUBTITLE_LINES)

        if (subtitleLines.isEmpty()) return ""

        return subtitleLines
            .joinToString(" ")
            .replace(whitespace, " ")
            .trim()
            .take(MAX_OUTPUT_CHARACTERS)
    }

    /** ScreenTextRecognizer veya testler icin satir bazli sonuc. */
    fun cleanLines(rawText: String): List<String> = clean(rawText)
        .takeIf(String::isNotBlank)
        ?.let(::listOf)
        ?: emptyList()

    /** Metnin ceviri katmanina hic gonderilmemesi gerekip gerekmedigi. */
    fun shouldReject(rawText: String): Boolean = clean(rawText).isBlank()

    private fun normalizeLine(input: String): String {
        var line = input
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2026'.toString(), "...")
            .replace('|', 'I')
            .replace(whitespace, " ")
            .replace(repeatedPunctuation, "$1")
            .trim()

        line = line
            .removePrefix("-")
            .removePrefix("•")
            .trim()

        // OCR'nin tek basina biraktigi parantez/kose parantez kirintilarini temizle.
        if (line.count { it == '[' } != line.count { it == ']' }) {
            line = line.replace("[", "").replace("]", "").trim()
        }
        if (line.count { it == '(' } != line.count { it == ')' }) {
            line = line.replace("(", "").replace(")", "").trim()
        }

        return line
    }

    private fun isNoiseLine(line: String): Boolean {
        if (line.length < 2) return true
        if (isolatedNoise.matches(line)) return true
        if (mostlyNumbers.matches(line)) return true

        val letters = line.count(Char::isLetter)
        val digits = line.count(Char::isDigit)
        val visible = line.count { !it.isWhitespace() }.coerceAtLeast(1)

        if (letters < 2) return true
        if (digits >= 3 && digits > letters) return true
        if (letters.toDouble() / visible < MIN_LETTER_RATIO) return true

        val words = words(line)
        if (words.isEmpty()) return true

        val hudHits = words.count { it in hudWords }
        if (hudHits >= 2) return true
        if (hudHits >= 1 && digits >= 2) return true

        // Tamami buyuk harfli kisa HUD etiketlerini reddet; diyalog cumlelerini koru.
        val uppercaseLetters = line.count { it.isLetter() && it.isUpperCase() }
        if (letters >= 4 && uppercaseLetters == letters && words.size <= 3 && !hasSentencePunctuation(line)) {
            return true
        }

        return false
    }

    private fun subtitleScore(line: String): Int {
        val words = words(line)
        val letters = line.count(Char::isLetter)
        val digits = line.count(Char::isDigit)

        var score = 0
        score += words.size.coerceAtMost(10) * 2
        score += (letters / 4).coerceAtMost(8)
        if (hasSentencePunctuation(line)) score += 4
        if (line.firstOrNull()?.isUpperCase() == true) score += 1
        if (line.contains('\'')) score += 1
        if (line.startsWith("[") || line.startsWith("(")) score += 2
        score -= digits.coerceAtMost(8)
        score -= words.count { it in hudWords } * 5

        return score
    }

    private fun words(line: String): List<String> = line
        .lowercase(Locale.US)
        .split(Regex("[^a-z]+"))
        .filter(String::isNotBlank)

    private fun comparisonKey(line: String): String = line
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun hasSentencePunctuation(line: String): Boolean =
        line.endsWith('.') || line.endsWith('!') || line.endsWith('?') || line.endsWith(']') || line.endsWith(')')

    private const val MIN_SUBTITLE_SCORE = 7
    private const val MAX_SUBTITLE_LINES = 2
    private const val MAX_OUTPUT_CHARACTERS = 240
    private const val MIN_LETTER_RATIO = 0.45
}
