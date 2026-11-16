package com.example.altyazi

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.AnimatedVectorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Uygulama açılırken ~2 saniyelik animasyonlu giriş:
 * kumanda çizilir → tuşlar yanar → "TR" balonu çıkar → "GameLingo" ve slogan belirir → kaybolur.
 * Ana ekranın ÜSTÜNE geçici bir katman olarak eklenir; arkada ekran normal kurulur.
 * Dokununca (ya da TV kumandasında herhangi bir tuşla) hemen geçilir.
 */
object GirisAnimasyonu {

    private const val LOGO_MS = 1250L     // logonun çizilme süresi (gl_giris_logo.xml)
    private const val BEKLE_MS = 650L     // yazılar göründükten sonra bekleme
    private const val KAYBOL_MS = 320L

    fun goster(a: Activity) {
        val kok = a.window?.decorView as? ViewGroup ?: return
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val turkuaz = Color.rgb(46, 230, 214)

        val katman = FrameLayout(a).apply {
            setBackgroundColor(Color.rgb(11, 18, 32))   // lingo_bg
            isClickable = true
            isFocusable = true
            elevation = 100f
        }
        val sutun = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logo = ImageView(a).apply {
            setImageResource(R.drawable.gl_giris_logo)
        }
        val ad = TextView(a).apply {
            text = SpannableStringBuilder("GameLingo").apply {
                setSpan(ForegroundColorSpan(turkuaz), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.DEFAULT_BOLD
            alpha = 0f
            translationY = dp(14).toFloat()
        }
        val slogan = TextView(a).apply {
            text = "Oyunları kendi dilinde yaşa"
            setTextColor(Color.rgb(143, 163, 191))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            alpha = 0f
            translationY = dp(10).toFloat()
        }
        sutun.addView(logo, LinearLayout.LayoutParams(dp(170), dp(170)))
        sutun.addView(ad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        sutun.addView(slogan, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        katman.addView(sutun, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        var bitti = false
        fun kaldir() {
            if (bitti) return
            bitti = true
            katman.animate().cancel()
            katman.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(KAYBOL_MS)
                .withEndAction { try { kok.removeView(katman) } catch (_: Throwable) {} }
                .start()
        }
        katman.setOnClickListener { kaldir() }
        katman.setOnKeyListener { _, _, _ -> kaldir(); true }

        try {
            kok.addView(katman, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            katman.requestFocus()
        } catch (_: Throwable) {
            return
        }

        (logo.drawable as? AnimatedVectorDrawable)?.start()
        val yavas = DecelerateInterpolator()
        ad.animate().alpha(1f).translationY(0f).setStartDelay(LOGO_MS - 300).setDuration(380).setInterpolator(yavas).start()
        slogan.animate().alpha(1f).translationY(0f).setStartDelay(LOGO_MS - 100).setDuration(380).setInterpolator(yavas).start()
        katman.postDelayed({ kaldir() }, LOGO_MS + BEKLE_MS)
    }
}
