package com.example.altyazi

fun main() {
    val idioms = listOf(
        "I'm having a ball" to "Çok eğleniyorum",
        "We're having a ball" to "Çok eğleniyoruz",
        "Don't throw me under the bus!" to "Beni zor durumda bırakma",
        "You extinguished families." to "Aileleri yok ettin"
    )
    idioms.forEach { (input, expected) ->
        check(Phrases.lookup(input) == expected) { "Wrong idiom: $input" }
    }
    listOf("My ball is red", "I got on the bus", "I was playing with a ball").forEach {
        check(Phrases.lookup(it) == null) { "Accidentally rewrote unrelated text: $it" }
    }
    println("OK: ${idioms.size} idioms + 3 nonmatching cases")
}
