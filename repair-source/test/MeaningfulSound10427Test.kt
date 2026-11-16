package com.example.altyazi
fun main() {
    val cases = listOf(
        "[electricity buzzing]" to "[elektrik cızırtısı]",
        "[electrical buzzing]" to "[elektrik cızırtısı]",
        "[soft buzzing]" to "[hafif vızıltı]",
        "[phone buzzing]" to "[telefon titriyor]",
        "[buzzing]" to "[vızıldıyor]",
        "[groans]" to "[inlemeler]",
        "[crickets chirping]" to "[cırcır böcekleri ötüyor]",
        "[soft whirring]" to "[hafif vınlıyor]"
    )
    cases.forEach { (en, expected) ->
        val actual = SoundTags.translate(en)
        check(actual == expected) { "$en -> $actual (expected $expected)" }
    }
    println("MEANINGFUL SOUND: ${cases.size}/${cases.size} PASS")
}
