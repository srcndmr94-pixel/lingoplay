package com.example.altyazi

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Ekranın üzerine çizilen çeviri katmanı.
 *
 * İki tür yazı var:
 *  - Altyazı kutusu: büyük, ortalı, altyazı şeridinin hemen üstünde durur.
 *  - Küçük etiketler: menü, görev ve tuş yazılarının kendi yanında durur.
 *
 * Böylece farklı yerlerden gelen çeviriler tek kutuda toplanıp birbirine karışmaz.
 */
class Overlay(private val context: Context) {

    data class Label(val text: String, val x: Int, val y: Int)

    /** Altyazı kutusunun görünümü; ayarlardan geliyor. */
    data class Stil(
        val boyutSp: Float = 18f,
        val yaziRengi: Int = Color.WHITE,
        val arkaAlfa: Int = 190,
        val arkaRenk: Int = Color.BLACK,
        val kenarlik: Boolean = true,
        /** Kutunun ekran kenarlarına uzaklığı, ekran genişliğinin yüzdesi (TV modu: kesilme payı). */
        val kenarPayYuzde: Int = 0,
    )

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var subtitleView: TextView? = null

    /**
     * Bir önceki repliğin kutusu: hızlı konuşmada yeni satır gelince eski satır
     * silinmez, buraya küçülüp solgunlaşarak çıkar ve bir süre daha okunabilir
     * kalır. Düzeltme geç yetiştiğinde de kaybolmayıp burada görünür.
     */
    private var prevView: TextView? = null
    private val labelViews = ArrayList<TextView>()
    /** Last source strip: reused when the previous-caption text changes later. */
    @Volatile private var protectedStripTop = -1
    @Volatile private var protectedStripBottom = -1

    @Volatile
    private var stil = Stil()

    private data class SubtitleRequest(
        val value: String?, val topPx: Int, val refined: Boolean, val draft: Boolean,
        val bottomPx: Int, val previous: String?, val protectedTopPx: Int,
        val protectedBottomPx: Int, val preferBelow: Boolean
    )
    @Volatile private var lastSubtitleRequest: SubtitleRequest? = null

    /** Ayarlar değişince çağrılır; kutu anında yeni görünüme geçer. */
    fun setStil(yeni: Stil) {
        stil = yeni
        subtitleView?.post {
            stiliUygula()
            // Font-size changes alter measured height; reposition rather than
            // leaving a suddenly taller box on top of the English captions.
            lastSubtitleRequest?.let { req ->
                setSubtitle(req.value, req.topPx, req.refined, req.draft,
                    req.bottomPx, req.previous, req.protectedTopPx,
                    req.protectedBottomPx, req.preferBelow)
            }
        }
    }

    private fun stiliUygula() {
        subtitleView?.let { stiliUygula(it, stil.boyutSp) }
        prevView?.let { stiliUygula(it, stil.boyutSp * ONCEKI_ORAN) }
    }

    private fun stiliUygula(t: TextView, boyutSp: Float) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, boyutSp)
        t.setTextColor(stil.yaziRengi)
        if (stil.kenarlik) t.setShadowLayer(7f, 0f, 2f, Color.BLACK)
        else t.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        t.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(
                Color.argb(
                    stil.arkaAlfa,
                    Color.red(stil.arkaRenk),
                    Color.green(stil.arkaRenk),
                    Color.blue(stil.arkaRenk),
                )
            )
        }
    }

    fun show() {
        if (root != null) return

        val container = FrameLayout(context)

        // Önceki satır ÖNCE eklenir: alttaki (güncel) kutu onun üstüne çizilsin,
        // ikisi bir an çakışırsa güncel satır okunaklı kalsın.
        val prev = makeText(stil.boyutSp * ONCEKI_ORAN, stil.arkaAlfa).apply {
            visibility = View.GONE
            alpha = ONCEKI_SOLUK
        }
        container.addView(
            prev,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        )

        val subtitle = makeText(stil.boyutSp, stil.arkaAlfa).apply { visibility = View.GONE }
        container.addView(
            subtitle,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            wm.addView(container, lp)
            root = container
            subtitleView = subtitle
            prevView = prev
            stiliUygula()
        } catch (_: Throwable) {
        }
    }

    private fun makeText(sizeSp: Float, alpha: Int): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(alpha, 0, 0, 0))
        }
    }

    /**
     * Altyazı çevirisi. topPx, kutunun ekranın üstünden uzaklığı.
     * [refined]: telefondaki yapay zekânın düzgün çevirisi; aynı repliğin kaba çevirisinin
     * yerine geçer. Kutu daralmaz (zıplamasın), yazı yumuşakça değişir, köşede küçük nokta çıkar.
     */
    /**
     * [bottomPx] >= 0 ise kutunun ALT kenarı bu çizginin üstünde kalır (kutu ne kadar
     * büyük olursa olsun İngilizce altyazının üstüne binmez, okumayı engellemez).
     */
    fun setSubtitle(
        value: String?,
        topPx: Int,
        refined: Boolean = false,
        draft: Boolean = false,
        bottomPx: Int = -1,
        previous: String? = null,
        protectedTopPx: Int = -1,
        protectedBottomPx: Int = -1,
        preferBelow: Boolean = false,
    ) {
        lastSubtitleRequest = SubtitleRequest(value, topPx, refined, draft, bottomPx,
            previous, protectedTopPx, protectedBottomPx, preferBelow)
        val t = subtitleView ?: return
        t.post {
            if (value.isNullOrBlank()) {
                t.animate().cancel()
                t.alpha = 1f
                t.minWidth = 0
                t.visibility = View.GONE
                t.text = ""
                oncekiGizle()
                return@post
            }
            val lp = t.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = topPx.coerceAtLeast(dp(8))
            val kenar = maxOf(dp(12), context.resources.displayMetrics.widthPixels * stil.kenarPayYuzde / 100)
            lp.leftMargin = kenar
            lp.rightMargin = kenar
            t.layoutParams = lp

            val wasVisible = t.visibility == View.VISIBLE
            // Aynı repliğin düzeltilmiş hali: kutu en az eski genişliğinde kalsın.
            t.minWidth = if (refined && wasVisible) t.width else 0

            val shown: CharSequence = if (refined) {
                SpannableStringBuilder(value).apply {
                    val start = length
                    append("  ●")
                    setSpan(ForegroundColorSpan(Color.rgb(46, 230, 214)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(RelativeSizeSpan(0.55f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else value
            // NOT: taslak artık GRİYE boyanmıyor. Kullanıcı altyazı rengini kendi
            // seçiyor (ör. sarı); gri taslak o seçimi eziyor ve replik yapay zekâ
            // onaylayınca griden sarıya dönüyordu. Yazı artık hep seçilen renkte;
            // yapay zekânın onayladığı satırı sondaki turkuaz nokta gösteriyor.
            t.text = shown
            val screenBounds = wm.currentWindowMetrics.bounds
            val ekranGen = screenBounds.width()
            val ekranYuk = screenBounds.height()
            val genislik = (ekranGen - lp.leftMargin - lp.rightMargin).coerceAtLeast(dp(80))
            val widthSpec = View.MeasureSpec.makeMeasureSpec(genislik, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            t.measure(widthSpec, heightSpec)

            // One measurement for current AND previous text. Otherwise large
            // translation text could still overlap the newly added English line.
            var showPrevious = !previous.isNullOrBlank()
            val bandIsValid = protectedTopPx >= 0 &&
                protectedBottomPx > protectedTopPx && protectedBottomPx <= ekranYuk
            protectedStripTop = if (bandIsValid) protectedTopPx else -1
            protectedStripBottom = if (bandIsValid) protectedBottomPx else -1
            if (bandIsValid) {
                val oldHeight = if (showPrevious) {
                    prevView?.let { prev ->
                        prev.text = previous
                        prev.measure(widthSpec, heightSpec)
                        prev.measuredHeight
                    } ?: 0
                } else 0
                val placement = SubtitleOverlayPlacement299.choose(
                    screenHeight = ekranYuk,
                    bandTop = protectedTopPx,
                    bandBottom = protectedBottomPx,
                    preferredTop = topPx,
                    currentHeight = t.measuredHeight,
                    previousHeight = oldHeight,
                    preferBelow = preferBelow,
                    margin = dp(8),
                    gap = dp(6)
                )
                if (placement == null) {
                    // There is no safe place at the current font size. Never
                    // hide English OCR pixels just to keep the Turkish box up.
                    t.visibility = View.GONE
                    oncekiGizle()
                    return@post
                }
                lp.topMargin = placement.topPx
                showPrevious = placement.showPrevious
                t.layoutParams = lp
            } else {
                // No capture band yet: original safe top/bottom sizing behavior.
                if (bottomPx >= 0) {
                    lp.topMargin = (bottomPx - t.measuredHeight).coerceAtLeast(dp(8))
                } else if (lp.topMargin > ekranYuk / 2) {
                    val maxTop = ekranYuk - t.measuredHeight - dp(2)
                    lp.topMargin = lp.topMargin.coerceAtMost(maxTop.coerceAtLeast(dp(8)))
                }
                t.layoutParams = lp
            }
            t.visibility = View.VISIBLE
            if (wasVisible) {
                // Yumuşak geçiş: yazı aniden değişmesin.
                t.animate().cancel()
                t.alpha = 0.35f
                t.animate().alpha(1f).setDuration(180).start()
            } else {
                t.alpha = 1f
            }
            // Önceki satır, güncel kutunun TAM ÜSTÜNE. Güncel kutunun yeri bu
            // noktada kesinleştiği için aynı geçişte yerleştiriliyor; ayrı bir
            // post ile yapılsaydı iki kutu bir kare üst üste binebilirdi.
            oncekiYerlestir(if (showPrevious) previous else null, lp.topMargin, lp.leftMargin, lp.rightMargin)
        }
    }

    /**
     * Önceki repliği güncel kutunun üstüne koyar. [altPx] güncel kutunun üst
     * kenarı: önceki satırın ALT kenarı bunun hemen üstünde durur.
     */
    private fun oncekiYerlestir(value: String?, altPx: Int, sol: Int, sag: Int) {
        val p = prevView ?: return
        if (value.isNullOrBlank()) {
            oncekiGizle()
            return
        }
        p.animate().cancel()
        p.text = value
        val lp = p.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = sol
        lp.rightMargin = sag
        val genislik = (context.resources.displayMetrics.widthPixels - sol - sag).coerceAtLeast(dp(80))
        p.measure(
            View.MeasureSpec.makeMeasureSpec(genislik, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val ust = altPx - p.measuredHeight - dp(3)
        // Yukarıda yer yoksa önceki satırı hiç gösterme: ekranın tepesine
        // yapışmış, oyunun üstünü kapatan bir kutu okumayı kolaylaştırmaz.
        if (ust < dp(8) || (
            protectedStripTop >= 0 && protectedStripBottom > protectedStripTop &&
                ust < protectedStripBottom && ust + p.measuredHeight > protectedStripTop
        )) {
            oncekiGizle()
            return
        }
        lp.topMargin = ust
        p.layoutParams = lp
        p.alpha = ONCEKI_SOLUK
        p.visibility = View.VISIBLE
    }

    /**
     * Yalnız üst sıranın yazısını değiştirir; güncel satıra dokunmaz.
     * Geç gelen düzeltme için: alttaki yazı yerinde kalır, üstteki tazelenir.
     * Güncel satır ekranda değilse tek başına üst sıra gösterilmez.
     */
    fun oncekiYaz(value: String?) {
        val t = subtitleView ?: return
        t.post {
            if (t.visibility != View.VISIBLE) {
                oncekiGizle()
                return@post
            }
            val lp = t.layoutParams as FrameLayout.LayoutParams
            oncekiYerlestir(value, lp.topMargin, lp.leftMargin, lp.rightMargin)
        }
    }

    /** Önceki satırı solarak kaldırır (çağıran süreyi kendisi tutar). */
    fun oncekiSoldur(sureMs: Long) {
        val p = prevView ?: return
        p.post {
            if (p.visibility != View.VISIBLE) return@post
            p.animate().cancel()
            p.animate().alpha(0f).setDuration(sureMs).withEndAction {
                p.visibility = View.GONE
                p.text = ""
                p.alpha = ONCEKI_SOLUK
            }.start()
        }
    }

    private fun oncekiGizle() {
        val p = prevView ?: return
        p.animate().cancel()
        p.visibility = View.GONE
        p.text = ""
        p.alpha = ONCEKI_SOLUK
    }

    /** Menü, görev ve tuş yazılarının çevirileri; her biri kendi yerinde. */
    fun setLabels(labels: List<Label>) {
        val container = root ?: return
        container.post {
            while (labelViews.size < labels.size) {
                val tv = makeText(13f, 205)
                container.addView(
                    tv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.TOP or Gravity.START }
                )
                labelViews.add(tv)
            }

            for (i in labelViews.indices) {
                val tv = labelViews[i]
                val label = labels.getOrNull(i)
                if (label == null) {
                    tv.visibility = View.GONE
                    continue
                }
                val lp = tv.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = label.x.coerceAtLeast(0)
                lp.topMargin = label.y.coerceAtLeast(0)
                tv.layoutParams = lp
                tv.text = label.text
                tv.visibility = View.VISIBLE
            }
        }
    }

    fun clearLabels() = setLabels(emptyList())

    fun hide() {
        val r = root ?: return
        try {
            wm.removeView(r)
        } catch (_: Throwable) {
        }
        root = null
        subtitleView = null
        prevView = null
        labelViews.clear()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        /** Önceki satırın yazı boyutu, güncel satıra oran. */
        const val ONCEKI_ORAN = 0.78f

        /** Önceki satırın saydamlığı: okunur ama öne çıkmaz. */
        const val ONCEKI_SOLUK = 0.62f
    }
}
