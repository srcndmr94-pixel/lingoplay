package com.example.altyazi
fun main() {
    val tests = listOf(
        "(Kreggl The betrayer!" to "[Kregg] The betrayer!",
        "[Harleenl Why did you leave?" to "[Harleen] Why did you leave?",
        "[Kregg] The betrayer!" to "[Kregg] The betrayer!",
        "(Paul This is a story." to "(Paul This is a story.",
        "What a nice day!" to "What a nice day!",
        "(sighs Stick to the plan." to "[sighs] Stick to the plan.",
        "[Igasping] Help me!" to "[gasping] Help me!"
    )
    tests.forEach { (input, expected) ->
        val got = SubtitlePreparation.repair(input)
        check(got == expected) { "'$input' -> '$got' expected '$expected'" }
    }
    println("PASS ${tests.size} speaker OCR tests")
}
