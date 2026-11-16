package com.example.altyazi
fun main() {
    val cases = listOf(
        "[passionate kissingl" to "[passionate kissing]",
        "[gruntingl" to "[grunting]",
        "[both gruntingl" to "[both grunting]",
        "(sighs Stick to the plan." to "[sighs] Stick to the plan.",
        "[laughs Wait, what?" to "[laughs] Wait, what?",
        "[Nolan] Listen to me." to "[Nolan] Listen to me.",
        "I was speaking to an important person." to "I was speaking to an important person."
    )
    cases.forEach { (source, expected) ->
        check(SubtitlePreparation.repair(source) == expected) {
            "Source=$source expected=$expected actual=${SubtitlePreparation.repair(source)}"
        }
    }
    println("PASS ${cases.size}/7 generic OCR sound-tag repairs")
}
