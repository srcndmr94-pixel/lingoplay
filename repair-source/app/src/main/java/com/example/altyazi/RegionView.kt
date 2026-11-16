package com.example.altyazi

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

/**
 * Çeviri alanını ekranda elle seçtiren çerçeve.
 *
 * Köşelerden ve kenar ortalarından tutup sürükleyerek boyutlandırılır,
 * içinden tutup sürükleyerek taşınır. Değerler ekran oranı olarak (0..1)
 * tutulur ki farklı çözünürlüklerde aynı yere denk gelsin.
 */
class RegionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Önizleme, OCR servisinin oyunda aldığı son kare. Dosyaya yazılmaz. */
    var previewBitmap: android.graphics.Bitmap? = null
        set(value) { field = value; invalidate() }

    var onRegionChanged: (() -> Unit)? = null

    internal fun bounds(): RegionBounds = RegionBounds(sol, ust, sag, alt).normalized()

    internal fun setBounds(b: RegionBounds) {
        val safe = b.normalized()
        sol = safe.left
        ust = safe.top
        sag = safe.right
        alt = safe.bottom
        invalidate()
        onRegionChanged?.invoke()
    }

    /** Seçili alan, ekran oranı cinsinden. */
    var sol = 0.08f
    var ust = 0.74f
    var sag = 0.92f
    var alt = 0.99f

    private val karartma = Paint().apply { color = Color.argb(150, 0, 0, 0) }

    private val cerceve = Paint().apply {
        color = Color.rgb(15, 153, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        isAntiAlias = true
    }

    private val tutamak = Paint().apply {
        color = Color.rgb(15, 153, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val yazi = Paint().apply {
        color = Color.argb(140, 255, 255, 255)
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val tutamakYaricap = dp(5f)
    private val yakalamaMesafesi = dp(28f)

    /** Hangi tutamak sürükleniyor; null ise alanın tamamı taşınıyor. */
    private var tutulan: Tutamak? = null
    private var tasiniyor = false
    private var sonX = 0f
    private var sonY = 0f

    private enum class Tutamak { SOL, SAG, UST, ALT, SOL_UST, SAG_UST, SOL_ALT, SAG_ALT }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun kutu() = RectF(sol * width, ust * height, sag * width, alt * height)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snapshot = previewBitmap
        if (snapshot != null && !snapshot.isRecycled) {
            canvas.drawBitmap(
                snapshot,
                null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                null
            )
        } else {
            canvas.drawColor(Color.BLACK)
        }
        val k = kutu()

        // Alan dışını karart; seçilen yer net görünsün.
        canvas.drawRect(0f, 0f, width.toFloat(), k.top, karartma)
        canvas.drawRect(0f, k.bottom, width.toFloat(), height.toFloat(), karartma)
        canvas.drawRect(0f, k.top, k.left, k.bottom, karartma)
        canvas.drawRect(k.right, k.top, width.toFloat(), k.bottom, karartma)

        canvas.drawRoundRect(k, dp(4f), dp(4f), cerceve)

        for ((x, y) in tutamakNoktalari(k)) {
            canvas.drawCircle(x, y, tutamakYaricap, tutamak)
        }

        if (k.top > dp(26f)) {
            canvas.drawText("ALTYAZI BÖLGESİ", k.centerX(), k.top - dp(12f), yazi)
        }
    }

    private fun tutamakNoktalari(k: RectF): List<Pair<Float, Float>> = listOf(
        k.left to k.top, k.right to k.top,
        k.left to k.bottom, k.right to k.bottom,
        k.centerX() to k.top, k.centerX() to k.bottom,
        k.left to k.centerY(), k.right to k.centerY(),
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val k = kutu()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tutulan = yakinTutamak(event.x, event.y, k)
                tasiniyor = tutulan == null && k.contains(event.x, event.y)
                sonX = event.x
                sonY = event.y
                return tutulan != null || tasiniyor
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - sonX) / width
                val dy = (event.y - sonY) / height
                sonX = event.x
                sonY = event.y

                if (tasiniyor) {
                    val genislik = sag - sol
                    val yukseklik = alt - ust
                    sol = (sol + dx).coerceIn(0f, 1f - genislik)
                    sag = sol + genislik
                    ust = (ust + dy).coerceIn(0f, 1f - yukseklik)
                    alt = ust + yukseklik
                } else {
                    when (tutulan) {
                        Tutamak.SOL -> sol = yeniSol(dx)
                        Tutamak.SAG -> sag = yeniSag(dx)
                        Tutamak.UST -> ust = yeniUst(dy)
                        Tutamak.ALT -> alt = yeniAlt(dy)
                        Tutamak.SOL_UST -> { sol = yeniSol(dx); ust = yeniUst(dy) }
                        Tutamak.SAG_UST -> { sag = yeniSag(dx); ust = yeniUst(dy) }
                        Tutamak.SOL_ALT -> { sol = yeniSol(dx); alt = yeniAlt(dy) }
                        Tutamak.SAG_ALT -> { sag = yeniSag(dx); alt = yeniAlt(dy) }
                        null -> return false
                    }
                }
                invalidate()
                onRegionChanged?.invoke()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tutulan = null
                tasiniyor = false
                return true
            }
        }
        return false
    }

    // En küçük alan: ekranın %5'i. Daha küçüğü hem tutulamaz hem işe yaramaz.
    private fun yeniSol(dx: Float) = (sol + dx).coerceIn(0f, sag - 0.05f)
    private fun yeniSag(dx: Float) = (sag + dx).coerceIn(sol + 0.05f, 1f)
    private fun yeniUst(dy: Float) = (ust + dy).coerceIn(0f, alt - 0.05f)
    private fun yeniAlt(dy: Float) = (alt + dy).coerceIn(ust + 0.05f, 1f)

    private fun yakinTutamak(x: Float, y: Float, k: RectF): Tutamak? {
        val adaylar = listOf(
            Tutamak.SOL_UST to (k.left to k.top),
            Tutamak.SAG_UST to (k.right to k.top),
            Tutamak.SOL_ALT to (k.left to k.bottom),
            Tutamak.SAG_ALT to (k.right to k.bottom),
            Tutamak.UST to (k.centerX() to k.top),
            Tutamak.ALT to (k.centerX() to k.bottom),
            Tutamak.SOL to (k.left to k.centerY()),
            Tutamak.SAG to (k.right to k.centerY()),
        )
        var enIyi: Tutamak? = null
        var enKisa = yakalamaMesafesi
        for ((ad, nokta) in adaylar) {
            val uzaklik = max(abs(x - nokta.first), abs(y - nokta.second))
            if (uzaklik < enKisa) {
                enKisa = uzaklik
                enIyi = ad
            }
        }
        return enIyi
    }

    /** Varsayılan alana döner: ekranın alt şeridi. */
    fun varsayilana_don() {
        setBounds(RegionBounds.DEFAULT)
    }

    fun yuzde(oran: Float): Int = (oran * 100).toInt().coerceIn(0, 100)

    fun kucukMu(): Boolean = min(sag - sol, alt - ust) < 0.04f
}
