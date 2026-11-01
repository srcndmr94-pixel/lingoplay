package com.example.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Enlarges the cropped subtitle area before OCR. This keeps the original frame untouched,
 * but gives small game-font glyphs more pixels for recognition.
 */
object SubtitleImagePreprocessor {
    fun prepare(source: Bitmap, minWidth: Int = 1280): Bitmap {
        val scale = (minWidth.toFloat() / source.width.coerceAtLeast(1)).coerceIn(1f, 2f)
        if (scale <= 1.02f) return source

        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /** Optional high-contrast copy for games whose subtitle font has poor edge contrast. */
    fun highContrastCopy(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        // Moderate contrast, deliberately not thresholded to preserve antialiased glyph edges.
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
