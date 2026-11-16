package com.example.altyazi

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.min

/** Oyun önizlemesinin üstünde OCR alanını sürükleyerek veya dört kenarı seçerek ayarlar. */
class RegionActivity : AppCompatActivity() {

    companion object {
        // OCR servisi bu ekranı "oyun" diye önizlemeye kaydetmesin.
        @Volatile var visible = false
    }

    private lateinit var prefs: Prefs
    private lateinit var region: RegionView
    private lateinit var slider: SeekBar
    private lateinit var percentLabel: TextView
    private lateinit var summary: TextView
    private var selectedEdge = RegionEdge.TOP
    private var updatingSlider = false
    private lateinit var edgeButtons: Map<RegionEdge, TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visible = true
        setContentView(R.layout.activity_region)
        prefs = Prefs(this)
        region = findViewById(R.id.regionView)
        slider = findViewById(R.id.sbRegionEdge)
        percentLabel = findViewById(R.id.tvRegionPercent)
        summary = findViewById(R.id.tvOlcu)

        region.setBounds(RegionBounds(
            prefs.leftPct / 100f, prefs.topPct / 100f,
            prefs.rightPct / 100f, prefs.bottomPct / 100f
        ).normalized())

        edgeButtons = mapOf(
            RegionEdge.TOP to findViewById(R.id.btnEdgeTop),
            RegionEdge.BOTTOM to findViewById(R.id.btnEdgeBottom),
            RegionEdge.LEFT to findViewById(R.id.btnEdgeLeft),
            RegionEdge.RIGHT to findViewById(R.id.btnEdgeRight)
        )
        edgeButtons.forEach { (edge, button) -> button.setOnClickListener { selectEdge(edge) } }

        slider.max = 100
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingSlider) return
                region.setBounds(region.bounds().change(selectedEdge, progress))
                refreshControls()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        region.onRegionChanged = { refreshControls() }
        findViewById<View>(R.id.btnRegionDefault).setOnClickListener {
            region.setBounds(RegionBounds.DEFAULT)
            refreshControls()
        }
        findViewById<Button>(R.id.btnRegionSave).setOnClickListener { saveAndClose() }
        findViewById<Button>(R.id.btnRegionCancel).setOnClickListener { finish() }

        showGamePreview()
        selectEdge(RegionEdge.TOP)
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    private fun showGamePreview() {
        // Yalnızca servisin oyunda yakaladığı son kare; yeni ekran yakalama izni istenmez.
        val screenshot = if (CaptureService.running) CaptureService.lastPreviewRaw else null
        if (screenshot != null && !screenshot.isRecycled) {
            region.previewBitmap = screenshot
            findViewById<TextView>(R.id.tvRegionPreviewHint).visibility = View.GONE
        }

        val previewArea = findViewById<FrameLayout>(R.id.regionPreviewArea)
        val previewFrame = findViewById<FrameLayout>(R.id.regionPreviewFrame)
        previewArea.post {
            val maxW = (previewArea.width - dp(24)).coerceAtLeast(dp(100))
            val maxH = (previewArea.height - dp(12)).coerceAtLeast(dp(110))
            val aspect = if (screenshot != null && !screenshot.isRecycled && screenshot.height > 0) {
                screenshot.width.toFloat() / screenshot.height
            } else 16f / 9f
            val w = min(maxW, (maxH * aspect).toInt()).coerceAtLeast(1)
            val h = (w / aspect).toInt().coerceAtLeast(1)
            previewFrame.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
        }
    }

    private fun selectEdge(edge: RegionEdge) {
        selectedEdge = edge
        edgeButtons.forEach { (e, button) ->
            button.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (e == edge) Color.rgb(94, 98, 111) else Color.TRANSPARENT)
            }
            button.setTextColor(if (e == edge) Color.WHITE else Color.rgb(205, 207, 222))
        }
        refreshControls()
    }

    private fun refreshControls() {
        val b = region.bounds()
        val percentage = b.percent(selectedEdge)
        updatingSlider = true
        slider.progress = percentage
        updatingSlider = false
        percentLabel.text = "%$percentage"
        summary.text = "Yatay %${b.percent(RegionEdge.LEFT)}–%${b.percent(RegionEdge.RIGHT)}" +
            "  ·  Dikey %${b.percent(RegionEdge.TOP)}–%${b.percent(RegionEdge.BOTTOM)}"
    }

    private fun saveAndClose() {
        val b = region.bounds().normalized()
        if (b.right - b.left < RegionBounds.MIN_SIZE - 0.001f ||
            b.bottom - b.top < RegionBounds.MIN_SIZE - 0.001f) {
            Toast.makeText(this, "Altyazı alanı çok küçük.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.leftPct = b.percent(RegionEdge.LEFT)
        prefs.topPct = b.percent(RegionEdge.TOP)
        prefs.rightPct = b.percent(RegionEdge.RIGHT)
        prefs.bottomPct = b.percent(RegionEdge.BOTTOM)
        Toast.makeText(this, "Altyazı bölgesi kaydedildi.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
