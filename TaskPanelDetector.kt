package com.example.subtitles

import android.graphics.Bitmap
import android.graphics.Rect

class TaskPanelDetector {
    enum class Side { LEFT, RIGHT }

    data class Region(
        val side: Side,
        val bounds: Rect,
        val bitmap: Bitmap
    )

    fun regions(screen: Bitmap): List<Region> {
        val w = screen.width
        val h = screen.height
        if (w < 2 || h < 2) return emptyList()

        // Intentionally keeps the center and bottom subtitle area out.
        val top = (h * 0.16f).toInt().coerceIn(0, h - 1)
        val bottom = (h * 0.72f).toInt().coerceIn(top + 1, h)
        val panelWidth = (w * 0.34f).toInt().coerceAtLeast(1)

        val leftBounds = Rect(0, top, panelWidth, bottom)
        val rightBounds = Rect(w - panelWidth, top, w, bottom)

        return listOf(
            Region(Side.LEFT, leftBounds, crop(screen, leftBounds)),
            Region(Side.RIGHT, rightBounds, crop(screen, rightBounds))
        )
    }

    private fun crop(source: Bitmap, rect: Rect): Bitmap = Bitmap.createBitmap(
        source,
        rect.left,
        rect.top,
        rect.width(),
        rect.height()
    )
}
