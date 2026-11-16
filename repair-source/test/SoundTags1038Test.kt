package com.example.altyazi
fun main() {
    val pairs = listOf(
        "[crickets chirping]" to "[cırcır böcekleri ötüyor]",
        "[leaves rustling]" to "[yapraklar hışırdıyor]",
        "[deep sigh]" to "[derin bir iç çekiş]",
        "[distorted voice]" to "[bozulmuş ses]",
        "[Jane screams]" to "[Jane çığlık atıyor]",
        "[gasping]" to "[nefes nefese]"
    )
    pairs.forEach { (en, expected) ->
        val value= SoundTags.translate(en)
        check(value==expected) { "$en => $value expected $expected" }
    }
    println("PASS ${pairs.size}/6 CC cue tests")
}
