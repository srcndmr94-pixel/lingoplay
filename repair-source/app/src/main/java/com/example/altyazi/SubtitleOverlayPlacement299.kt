package com.example.altyazi

/** Pure pixel-space placement: translations must not overlap English OCR strip. */
object SubtitleOverlayPlacement299 {
    data class Placement(val topPx: Int, val showPrevious: Boolean, val below: Boolean)

    fun choose(
        screenHeight: Int, bandTop: Int, bandBottom: Int,
        preferredTop: Int, currentHeight: Int, previousHeight: Int,
        preferBelow: Boolean, margin: Int, gap: Int
    ): Placement? {
        if (screenHeight <= 0 || currentHeight <= 0 || margin < 0) return null
        if (bandTop < 0 || bandBottom <= bandTop || bandBottom > screenHeight) return null
        val priorHeight = previousHeight.coerceAtLeast(0)
        if (priorHeight > 0) {
            trySide(screenHeight, bandTop, bandBottom, preferredTop, currentHeight,
                priorHeight, preferBelow, margin, gap, true)?.let { return it }
        }
        trySide(screenHeight, bandTop, bandBottom, preferredTop, currentHeight,
            priorHeight, preferBelow, margin, gap, false)?.let { return it }
        if (priorHeight > 0) {
            trySide(screenHeight, bandTop, bandBottom, preferredTop, currentHeight,
                priorHeight, !preferBelow, margin, gap, true)?.let { return it }
        }
        return trySide(screenHeight, bandTop, bandBottom, preferredTop, currentHeight,
            priorHeight, !preferBelow, margin, gap, false)
    }

    private fun trySide(
        screenHeight: Int, bandTop: Int, bandBottom: Int,
        preferredTop: Int, currentHeight: Int, previousHeight: Int,
        below: Boolean, margin: Int, gap: Int, showPrevious: Boolean
    ): Placement? {
        val extra = if (showPrevious) previousHeight + gap else 0
        if (below) {
            val first = bandBottom + gap + extra
            val maxTop = screenHeight - margin - currentHeight
            return if (first > maxTop) null else
                Placement(preferredTop.coerceIn(first, maxTop), showPrevious, true)
        }
        val top = bandTop - gap - currentHeight
        return if (top - extra < margin) null else Placement(top, showPrevious, false)
    }
}
