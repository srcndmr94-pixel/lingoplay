package com.example.altyazi

fun main() {
    val examples = listOf(
        "Isn't he the most hand some" to "Isn't he the most handsome",
        "six mnonths ago" to "six months ago",
        "Your momn was here" to "Your mom was here",
        "It's a tw O-week trip" to "It's a two-week trip",
        "to a wara million miles away" to "to a war a million miles away",
        "There'sa Satan" to "There's a Satan",
        "Aint playing with caution" to "Ain't playing with caution",
        "Don't touch my hand some more" to "Don't touch my hand some more",
        "I have one hand, some people have two" to "I have one hand, some people have two",
        "There's a Satan" to "There's a Satan",
        "The Guardians, all the Thraxans." to "The Guardians, all the Thraxans.",
        "I'm having a ball" to "I'm having a ball"
    )
    examples.forEach { (before, expected) ->
        check(SubtitlePreparation.repair(before) == expected) {
            "OCR failed: '${SubtitlePreparation.repair(before)}', expected '$expected'"
        }
    }
    val confident = listOf("I'm here for you.", "No. God no.", "[Eve]", "[sighs]", "The Guardians, all the Thraxans.")
    confident.forEach { check(SubtitlePreparation.confidentFirstRead(it)) { "Unexpected delay: $it" } }
    val uncertain = listOf("The Guardians, al the Thraxans.", "It's a tw O-week trip", "This has|extra rubbish", "The 1q2 value")
    uncertain.forEach { check(!SubtitlePreparation.confidentFirstRead(it)) { "Accepted noisy read: $it" } }
    println("OK: ${examples.size} OCR cases + ${confident.size + uncertain.size} read-gate cases")
}
