package com.example.altyazi

/** Ekran boyutundan bağımsız OCR alanı. Sol/üst/sağ/alt 0–1 oranındadır. */
internal enum class RegionEdge { TOP, BOTTOM, LEFT, RIGHT }

internal data class RegionBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        const val MIN_SIZE = 0.05f
        val DEFAULT get() = RegionBounds(0.08f, 0.74f, 0.92f, 0.99f)
    }

    fun normalized(): RegionBounds {
        val l = left.coerceIn(0f, 1f - MIN_SIZE)
        val t = top.coerceIn(0f, 1f - MIN_SIZE)
        return RegionBounds(
            left = l,
            top = t,
            right = right.coerceIn(l + MIN_SIZE, 1f),
            bottom = bottom.coerceIn(t + MIN_SIZE, 1f),
        )
    }

    fun change(edge: RegionEdge, percent: Int): RegionBounds {
        val r = normalized()
        val v = percent.coerceIn(0, 100) / 100f
        return when (edge) {
            RegionEdge.TOP -> r.copy(top = v.coerceIn(0f, r.bottom - MIN_SIZE))
            RegionEdge.BOTTOM -> r.copy(bottom = v.coerceIn(r.top + MIN_SIZE, 1f))
            RegionEdge.LEFT -> r.copy(left = v.coerceIn(0f, r.right - MIN_SIZE))
            RegionEdge.RIGHT -> r.copy(right = v.coerceIn(r.left + MIN_SIZE, 1f))
        }
    }

    fun percent(edge: RegionEdge): Int = ((when (edge) {
        RegionEdge.TOP -> top
        RegionEdge.BOTTOM -> bottom
        RegionEdge.LEFT -> left
        RegionEdge.RIGHT -> right
    }) * 100f + 0.5f).toInt().coerceIn(0, 100)
}
