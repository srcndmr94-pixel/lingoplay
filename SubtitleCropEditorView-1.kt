package com.example.subtitles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SubtitleCropEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    fun interface OnCropChangedListener {
        fun onCropChanged(rect: NormalizedCropRect)
    }

    private enum class Handle { NONE, MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private val shadePaint = Paint().apply { color = 0x88000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 210, 100)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(10f), dp(8f)), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 210, 100) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(22f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val crop = RectF()
    private var normalized = NormalizedCropRect.DEFAULT
    private var activeHandle = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var listener: OnCropChangedListener? = null

    private val handleRadius get() = dp(12f)
    private val hitRadius get() = dp(28f)
    private val minimumWidth get() = max(dp(160f), width * 0.18f)
    private val minimumHeight get() = max(dp(80f), height * 0.10f)

    fun setCrop(rect: NormalizedCropRect) {
        normalized = rect
        if (width > 0 && height > 0) crop.set(rect.toViewRect(width, height))
        invalidate()
    }

    fun getCrop(): NormalizedCropRect = normalized

    fun setOnCropChangedListener(value: OnCropChangedListener?) {
        listener = value
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        crop.set(normalized.toViewRect(w, h))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, shadePaint)
        canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), shadePaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, shadePaint)
        canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, shadePaint)
        canvas.drawRect(crop, borderPaint)

        points().forEach { (x, y) -> canvas.drawCircle(x, y, handleRadius, handlePaint) }
        canvas.drawText("ALTYAZI OKUMA ALANI", crop.centerX(), crop.top + dp(34f), labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = findHandle(event.x, event.y)
                if (activeHandle == Handle.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                moveHandle(activeHandle, dx, dy)
                lastX = event.x
                lastY = event.y
                publish()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                publish()
                activeHandle = Handle.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveHandle(handle: Handle, dx: Float, dy: Float) {
        when (handle) {
            Handle.MOVE -> {
                val safeDx = dx.coerceIn(-crop.left, width - crop.right)
                val safeDy = dy.coerceIn(-crop.top, height - crop.bottom)
                crop.offset(safeDx, safeDy)
            }
            Handle.LEFT, Handle.TOP_LEFT, Handle.BOTTOM_LEFT -> crop.left =
                (crop.left + dx).coerceIn(0f, crop.right - minimumWidth)
            Handle.RIGHT, Handle.TOP_RIGHT, Handle.BOTTOM_RIGHT -> crop.right =
                (crop.right + dx).coerceIn(crop.left + minimumWidth, width.toFloat())
            else -> Unit
        }
        when (handle) {
            Handle.TOP, Handle.TOP_LEFT, Handle.TOP_RIGHT -> crop.top =
                (crop.top + dy).coerceIn(0f, crop.bottom - minimumHeight)
            Handle.BOTTOM, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> crop.bottom =
                (crop.bottom + dy).coerceIn(crop.top + minimumHeight, height.toFloat())
            else -> Unit
        }
    }

    private fun findHandle(x: Float, y: Float): Handle {
        val nearLeft = abs(x - crop.left) <= hitRadius
        val nearRight = abs(x - crop.right) <= hitRadius
        val nearTop = abs(y - crop.top) <= hitRadius
        val nearBottom = abs(y - crop.bottom) <= hitRadius
        return when {
            nearLeft && nearTop -> Handle.TOP_LEFT
            nearRight && nearTop -> Handle.TOP_RIGHT
            nearLeft && nearBottom -> Handle.BOTTOM_LEFT
            nearRight && nearBottom -> Handle.BOTTOM_RIGHT
            nearLeft && y in crop.top..crop.bottom -> Handle.LEFT
            nearRight && y in crop.top..crop.bottom -> Handle.RIGHT
            nearTop && x in crop.left..crop.right -> Handle.TOP
            nearBottom && x in crop.left..crop.right -> Handle.BOTTOM
            crop.contains(x, y) -> Handle.MOVE
            else -> Handle.NONE
        }
    }

    private fun points() = listOf(
        crop.left to crop.top, crop.centerX() to crop.top, crop.right to crop.top,
        crop.left to crop.centerY(), crop.right to crop.centerY(),
        crop.left to crop.bottom, crop.centerX() to crop.bottom, crop.right to crop.bottom
    )

    private fun publish() {
        normalized = NormalizedCropRect.fromViewRect(crop, width, height)
        listener?.onCropChanged(normalized)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
