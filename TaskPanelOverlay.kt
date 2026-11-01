package com.example.subtitles

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class TaskPanelOverlay(private val context: Context) : AutoCloseable {
    data class Item(
        val side: TaskPanelDetector.Side,
        val text: String
    )

    private data class Entry(
        val view: TextView,
        val params: WindowManager.LayoutParams
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val entries = mutableMapOf<TaskPanelDetector.Side, Entry>()

    fun show(item: Item) {
        if (item.text.isBlank()) {
            clear(item.side)
            return
        }
        val entry = entries[item.side] ?: create(item.side).also { entries[item.side] = it }
        entry.view.text = item.text
        entry.view.visibility = View.VISIBLE
        windowManager.updateViewLayout(entry.view, entry.params)
    }

    fun clear(side: TaskPanelDetector.Side) {
        entries[side]?.view?.apply {
            text = ""
            visibility = View.GONE
        }
    }

    private fun create(side: TaskPanelDetector.Side): Entry {
        val view = TextView(context).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xE6000000.toInt())
            textSize = 16f
            gravity = Gravity.START
            setPadding(18, 14, 18, 14)
        }

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (side == TaskPanelDetector.Side.LEFT) {
                Gravity.START or Gravity.TOP
            } else {
                Gravity.END or Gravity.TOP
            }
            x = dp(18)
            y = dp(120)
        }

        windowManager.addView(view, params)
        return Entry(view, params)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun close() {
        entries.values.forEach { runCatching { windowManager.removeView(it.view) } }
        entries.clear()
    }
}
