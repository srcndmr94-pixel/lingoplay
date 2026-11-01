package com.example.subtitles

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }

    fun toPixelRect(width: Int, height: Int): Rect = Rect(
        (left * width).roundToInt().coerceIn(0, width - 1),
        (top * height).roundToInt().coerceIn(0, height - 1),
        (right * width).roundToInt().coerceIn(1, width),
        (bottom * height).roundToInt().coerceIn(1, height)
    )

    fun toViewRect(width: Int, height: Int): RectF = RectF(
        left * width, top * height, right * width, bottom * height
    )

    companion object {
        // Güvenli başlangıç: ekranın alt-orta altyazı bölgesi.
        val DEFAULT = NormalizedCropRect(0.10f, 0.58f, 0.90f, 0.94f)

        fun fromViewRect(rect: RectF, width: Int, height: Int): NormalizedCropRect =
            NormalizedCropRect(
                left = (rect.left / width).coerceIn(0f, 1f),
                top = (rect.top / height).coerceIn(0f, 1f),
                right = (rect.right / width).coerceIn(0f, 1f),
                bottom = (rect.bottom / height).coerceIn(0f, 1f)
            )
    }
}

class SubtitleCropSettings(context: Context) {
    private val prefs = context.getSharedPreferences("subtitle_crop", Context.MODE_PRIVATE)

    fun load(): NormalizedCropRect = runCatching {
        NormalizedCropRect(
            prefs.getFloat("left", NormalizedCropRect.DEFAULT.left),
            prefs.getFloat("top", NormalizedCropRect.DEFAULT.top),
            prefs.getFloat("right", NormalizedCropRect.DEFAULT.right),
            prefs.getFloat("bottom", NormalizedCropRect.DEFAULT.bottom)
        )
    }.getOrDefault(NormalizedCropRect.DEFAULT)

    fun save(rect: NormalizedCropRect) {
        prefs.edit()
            .putFloat("left", rect.left)
            .putFloat("top", rect.top)
            .putFloat("right", rect.right)
            .putFloat("bottom", rect.bottom)
            .apply()
    }

    fun reset() = save(NormalizedCropRect.DEFAULT)
}
