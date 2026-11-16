package com.example.altyazi

import android.content.Context

/**
 * "Sözlüğüm": kullanıcının kendi düzeltmeleri. Her satır "ingilizce = türkçe",
 * isimler için "@İsim". Çeviride sözlüğün de önünde gelir; kaydedildiği an geçerlidir.
 */
object UserDict {
    private const val PREFS = "sozluk"
    private const val KEY = "satirlar"

    const val ORNEK = "# Örnekler (başında # olan satırlar dikkate alınmaz):\n" +
        "# this stinks = Bu işte bir bit yeniği var\n" +
        "# @Matches\n"

    fun load(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""

    fun save(context: Context, text: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, text).apply()
        OfflineFix.setUserEntries(text)
    }

    /** Sözlüğe tek satır ekler (aynı İngilizce varsa eskisini değiştirir). */
    fun add(context: Context, english: String, turkish: String) {
        val key = SubtitleGlossary.keyOf(english)
        val kept = load(context).lines().filter { line ->
            val eq = line.indexOf('=')
            !(eq > 0 && !line.trimStart().startsWith("#") && SubtitleGlossary.keyOf(line.substring(0, eq)) == key)
        }.joinToString("\n").trimEnd()
        val line = english.trim().trimEnd('.', '!', '?', '…') + " = " + turkish.trim()
        save(context, if (kept.isBlank()) line else "$kept\n$line")
    }

    /** Uygulama/servis açılırken sözlüğü çeviriye yükler. */
    fun apply(context: Context) = OfflineFix.setUserEntries(load(context))
}
