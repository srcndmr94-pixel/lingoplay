package com.example.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/** OCR öncesi görüntü hazırlama ve iki OCR adayı arasında güvenli seçim. */
object SubtitleImagePreprocessor {

    fun prepare(source: Bitmap, minWidth: Int = 1440): Bitmap {
        val safeWidth = source.width.coerceAtLeast(1)
        val scale = (minWidth.toFloat() / safeWidth).coerceIn(1f, 2.25f)
        if (scale <= 1.02f) return source
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun enhancedCopy(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val brightness = 5f
        val offset = (-0.5f * contrast + 0.5f) * 255f + brightness
        grayscale.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun highContrastCopy(source: Bitmap): Bitmap = enhancedCopy(source)

    fun shouldRetryOcr(text: String): Boolean {
        val value = text.trim()
        if (value.count(Char::isLetter) < 4) return true
        if (value.length < 8) return false

        val tokens = value.split(Regex("\\s+")).filter(String::isNotBlank)
        val suspicious = tokens.count { token ->
            val clean = token.filter(Char::isLetter)
            clean.length >= 5 && (
                mixedCasePenalty(clean) > 0 ||
                consonantRunPenalty(clean) > 0
            )
        }
        val hudLike = tokens.count { token ->
            token.any(Char::isDigit) || (token.length >= 4 && token.all { !it.isLetter() || it.isUpperCase() })
        }
        return suspicious >= 2 || hudLike >= 3
    }

    fun chooseBetterText(first: String, second: String): String {
        val firstScore = ocrScore(first)
        val secondScore = ocrScore(second)
        // İkinci geçiş yalnız anlamlı biçimde daha iyiyse seçilsin.
        return if (secondScore >= firstScore + 8) second else first
    }

    internal fun ocrScore(text: String): Int {
        val value = text.trim()
        if (value.isBlank()) return Int.MIN_VALUE
        val tokens = value.split(Regex("\\s+")).filter(String::isNotBlank)
        val letters = value.count(Char::isLetter)
        val normalWords = tokens.count { token ->
            val w = token.filter(Char::isLetter)
            w.length >= 2 && mixedCasePenalty(w) == 0 && consonantRunPenalty(w) == 0
        }
        val punctuation = value.count { it in ".,!?;:'’-" }
        val garbageChars = value.count { it in "|{}[]<>~`_^=" }
        val digits = value.count(Char::isDigit)
        val suspicious = tokens.sumOf { token ->
            val w = token.filter(Char::isLetter)
            mixedCasePenalty(w) + consonantRunPenalty(w)
        }
        val allCapsLong = tokens.count { token ->
            val w = token.filter(Char::isLetter)
            w.length >= 5 && w.all(Char::isUpperCase)
        }
        return letters * 2 + normalWords * 6 + punctuation - garbageChars * 10 - suspicious * 8 - digits * 2 - allCapsLong * 5
    }

    private fun mixedCasePenalty(word: String): Int {
        if (word.length < 5) return 0
        val innerUpper = word.drop(1).count(Char::isUpperCase)
        val transitions = word.zipWithNext().count { (a, b) -> a.isLowerCase() != b.isLowerCase() }
        return if (innerUpper >= 2 || transitions >= 3) 1 else 0
    }

    private fun consonantRunPenalty(word: String): Int =
        if (Regex("(?i).*[bcdfghjklmnpqrstvwxyz]{5,}.*").matches(word)) 1 else 0
}
