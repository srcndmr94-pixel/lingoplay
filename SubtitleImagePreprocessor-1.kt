package com.example.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * OCR öncesi altyazı görüntüsünü büyütür ve gerektiğinde daha okunaklı bir kopya üretir.
 * Amaç küçük/ince oyun fontlarında I/l, rn/m, birleşik kelime ve düşük kontrast hatalarını azaltmaktır.
 */
object SubtitleImagePreprocessor {

    fun prepare(source: Bitmap, minWidth: Int = 1440): Bitmap {
        val safeWidth = source.width.coerceAtLeast(1)
        val scale = (minWidth.toFloat() / safeWidth).coerceIn(1f, 2.25f)
        if (scale <= 1.02f) return source

        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Gri ton + kontrollü kontrast. Sert threshold uygulamaz; harf kenarlarını korur.
     */
    fun enhancedCopy(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.55f
        val brightness = 7f
        val offset = (-0.5f * contrast + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscale.postConcat(contrastMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Eski çağrı noktaları için uyumluluk. */
    fun highContrastCopy(source: Bitmap): Bitmap = enhancedCopy(source)

    /**
     * OCR çıktısında bozulma ihtimali yüksekse ikinci görüntü geçişini tetikler.
     * Uzun ama anlamsız OCR'ın yalnız harf sayısı yüksek diye kabul edilmesini önler.
     */
    fun shouldRetryOcr(text: String): Boolean {
        val value = text.trim()
        if (value.count(Char::isLetter) < 4) return true
        if (value.length < 8) return false

        val suspiciousTokens = value
            .split(Regex("\\s+"))
            .count { token ->
                val clean = token.filter(Char::isLetter)
                clean.length >= 5 && (
                    clean.count(Char::isUpperCase) >= 3 ||
                    Regex("(?i).*(?:[bcdfghjklmnpqrstvwxyz]{5,}).*").matches(clean)
                )
            }
        return suspiciousTokens >= 2
    }

    /** İki OCR sonucundan daha okunabilir adayı seçer. */
    fun chooseBetterText(first: String, second: String): String {
        return if (ocrScore(second) > ocrScore(first)) second else first
    }

    internal fun ocrScore(text: String): Int {
        val value = text.trim()
        if (value.isBlank()) return Int.MIN_VALUE
        val letters = value.count(Char::isLetter)
        val words = value.split(Regex("\\s+")).count { it.count(Char::isLetter) >= 2 }
        val punctuation = value.count { it in ".,!?;:'’-" }
        val garbage = value.count { it in "|{}[]<>~`" }
        val oddCase = value.split(Regex("\\s+")).count { word ->
            val lettersOnly = word.filter(Char::isLetter)
            lettersOnly.length >= 5 && lettersOnly.drop(1).count(Char::isUpperCase) >= 2
        }
        return letters * 2 + words * 5 + punctuation - garbage * 8 - oddCase * 6
    }
}
