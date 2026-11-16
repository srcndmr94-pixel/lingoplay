package com.example.altyazi

fun main() {
    fun nearly(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.0001f
    val d = RegionBounds.DEFAULT
    check(nearly(d.left, 0.08f) && nearly(d.top, 0.74f))
    check(nearly(d.right, 0.92f) && nearly(d.bottom, 0.99f))
    check(d.percent(RegionEdge.TOP) == 74)
    check(d.change(RegionEdge.TOP, 84).percent(RegionEdge.TOP) == 84)
    check(d.change(RegionEdge.TOP, 100).top <= d.bottom - RegionBounds.MIN_SIZE + 0.0001f)
    check(d.change(RegionEdge.BOTTOM, 0).bottom >= d.top + RegionBounds.MIN_SIZE - 0.0001f)
    check(d.change(RegionEdge.LEFT, 100).left <= d.right - RegionBounds.MIN_SIZE + 0.0001f)
    check(d.change(RegionEdge.RIGHT, 0).right >= d.left + RegionBounds.MIN_SIZE - 0.0001f)
    check(d.change(RegionEdge.RIGHT, 100).right == 1.0f)
    check(d.change(RegionEdge.LEFT, 0).left == 0.0f)
    check(RegionBounds(0.9f, 0.98f, 0.1f, 0.1f).normalized().let {
        it.right - it.left >= 0.049f && it.bottom - it.top >= 0.049f
    })
    val moved = d.change(RegionEdge.TOP, 84)
    check(moved.bottom == d.bottom && moved.left == d.left && moved.right == d.right)
    println("OK: RegionBounds 12 sinir ve alan testi")
}
