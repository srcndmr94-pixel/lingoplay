package com.example.altyazi
fun main() {
    val cases = listOf(
        Triple("I was just walking. Right?", "Yürüyordum. Sağ?", "Yürüyordum, değil mi?"),
        Triple("Turn right?", "Sağa mı dönelim?", "Sağa mı dönelim?"),
        Triple("Right?", "Sağ?", "Sağ?"),
        Triple("Tell you about the birds and the bees.", "Kuşlar ve arılar hakkında konuşalım.", "cinsellik hakkında konuşalım."),
        Triple("The sorority girl?", "Kardeşlik kızı?", "öğrenci topluluğundaki kız?"),
        Triple("You are a poindexter.", "Sen bir pointexter'sın.", "Sen bir ineksin."),
        Triple("You are a scientist.", "Bilim insanısın.", "Bilim insanısın."),
        Triple("poindexter", "Bir ineksin.", "Bir ineksin.")
    )
    cases.forEach { (en, output, expected) ->
        val actual = DialogueDisambiguation1038.repair(en, output)
        check(actual == expected) { "$en => $actual, expected: $expected" }
    }
    val ocr = listOf(
        "[Igasping] Help me!" to "[gasping] Help me!",
        "Jane screams] -[intense music playing]" to "[Jane screams] -[intense music playing]",
        "[Jane] pantingl" to "[Jane panting]",
        "[sighs] I don't know." to "[sighs] I don't know.",
        "I scream when afraid." to "I scream when afraid.",
        "[I wonder] what that means" to "[I wonder] what that means"
    )
    ocr.forEach { (before, expected) ->
        val actual = SubtitlePreparation.repair(before)
        check(actual == expected) { "$before => $actual, expected: $expected" }
    }
    println("PASS ${cases.size} disambiguation + ${ocr.size} OCR tests")
}
