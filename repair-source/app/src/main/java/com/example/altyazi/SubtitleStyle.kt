package com.example.altyazi

import android.graphics.Color

/**
 * Altyazı görünümü seçeneklerini gerçek renk/saydamlık değerlerine çevirir.
 *
 * Hem ekrana çizen servis hem ayar ekranındaki önizleme buradan okuyor.
 * Tek yerde durmasının sebebi şu: iki yerde ayrı ayrı yazılsaydı, biri
 * değiştiğinde önizleme ile gerçek görünüm birbirinden ayrı düşerdi.
 */
object SubtitleStyle {

    fun yaziRengi(secim: Int): Int = when (secim) {
        Prefs.COLOR_YELLOW -> Color.rgb(255, 221, 87)
        Prefs.COLOR_GREEN -> Color.rgb(154, 255, 140)
        Prefs.COLOR_CYAN -> Color.rgb(120, 231, 255)
        else -> Color.WHITE
    }

    fun arkaAlfa(secim: Int): Int = when (secim) {
        Prefs.BG_SOLID -> 255
        Prefs.BG_NONE -> 0
        else -> 235   // "Yarı saydam" artık neredeyse koyu siyah: arkadaki İngilizce okunmasın
    }

    fun arkaRenk(secim: Int): Int = when (secim) {
        Prefs.BGC_GRAY -> Color.rgb(38, 38, 40)
        Prefs.BGC_NAVY -> Color.rgb(12, 22, 48)
        else -> Color.BLACK
    }
}
