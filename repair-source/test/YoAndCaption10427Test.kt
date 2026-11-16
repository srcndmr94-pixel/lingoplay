package com.example.altyazi

fun main() {
    val examples = listOf(
        Triple("Yo, what's up?", "Lan, naber?", "Naber?"),
        Triple("Yo, is Raymond around?", "Lan, Raymond burada mı?", "Raymond burada mı?"),
        Triple("Yo! We going up.", "Lan, yukarı çıkıyoruz.", "Hey, yukarı çıkıyoruz."),
        Triple("Yo!", "Lan!", "Hey!"),
        Triple("Yo, what the fuck?", "Ne oluyor lan?", "Ne oluyor lan?"),
        Triple("Yo, what the fuck?", "Lan, ne oluyor?", "Lan, ne oluyor?"),
        Triple("Yo, what the fuck?", "Hey, bu ne lan?", "Bu ne lan?"),
        Triple("Yo, we're here to pick up.", "Lan, malı almaya geldik.", "Malı almaya geldik."),
        Triple("Yo, what's up?", "Naber lan?", "Naber?"),
        Triple("Yo, Ray!", "Hey, Ray!", "Hey, Ray!"),
        Triple("Yo! Crackheads! Woo!", "Lan, keşler! Woo!", "Hey, keşler! Woo!"),
        Triple("Hey, yo, Ray!", "Hey, hey, Ray!", "Hey, Ray!"),
        Triple("What the fuck?", "Bu ne lan?", "Bu ne lan?"),
        Triple("Go!", "Hadi!", "Hadi!")
    )
    for ((source, got, want) in examples) {
        check(YoContext10427.correct(source, got) == want) { "[$source]: ${YoContext10427.correct(source, got)} != $want" }
        check(YoContext10427.correct(source, want) == want) { "Idempotence: $source" }
    }
    check(CaptionSwitch10427.shouldClear("Really, huh?", "Where you at, Vice City girls?", false))
    check(CaptionSwitch10427.shouldClear("What?", "Are you sure?", false))
    check(!CaptionSwitch10427.shouldClear("", "New line", false))
    check(!CaptionSwitch10427.shouldClear("Same line", "Same line", false))
    check(!CaptionSwitch10427.shouldClear("Really, huh?", "Real1y, huh?", true))
    println("PASS: ${examples.size} YO+idempotence tests, 5 caption switch tests")
}
