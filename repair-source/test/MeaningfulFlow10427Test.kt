package com.example.altyazi

fun main() {
    var passed = 0
    fun verify(ok: Boolean, description: String) {
        check(ok) { description }
        passed++
    }
    val dialogue = DialogueContextRepair()
    val base = 100_000L
    dialogue.remember("My friend said there was a fire.", base)
    dialogue.remember("Nobody knows where she is.", base + 500)
    dialogue.remember("I have to find her.", base + 1000)
    dialogue.remember("This could be dangerous.", base + 1500)
    val context = dialogue.contextFor("This could be dangerous.", base + 1600)
    verify(context.lines().size == 3, "Exactly three previous subtitles: $context")
    verify(context.lines() == listOf("My friend said there was a fire.", "Nobody knows where she is.", "I have to find her."), "Correct order: $context")
    verify(!context.contains("This could be dangerous"), "Do not send current subtitle twice")
    verify(dialogue.contextFor("This could be dangerous.", base + 31_000).isEmpty(), "No old-scene context")
    verify(dialogue.contextFor("Unrelated source", base + 1600).isEmpty(), "Never borrow wrong turn context")
    dialogue.reset()
    verify(dialogue.contextFor("This could be dangerous.", base + 1600).isEmpty(), "Context cleared when session restarts")

    val joined = DialogueContextRepair()
    joined.remember("My name is", 10_000L)
    joined.remember("was the assistant director of", 10_200L)
    verify(joined.joinedTranslationInput("was the assistant director of", 10_201L) == null, "Do not merge contradictory auxiliaries")
    joined.reset()
    joined.remember("If I can do that,", 11_000L)
    joined.remember("imagine what I can do to your masks.", 11_300L)
    verify(joined.joinedTranslationInput("imagine what I can do to your masks.", 11_301L) ==
       "If I can do that, imagine what I can do to your masks.", "Valid clause continuation must still join")

    joined.reset()
    joined.remember("You gonna leave them squirming", 12_000L)
    joined.remember("like turtles on their backs?", 12_300L)
    verify(joined.joinedTranslationInput("like turtles on their backs?", 12_301L) ==
        "You gonna leave them squirming like turtles on their backs?", "Adverbial continuation should join")
    joined.reset()
    joined.remember("We are working", 13_000L)
    joined.remember("She is ready.", 13_100L)
    verify(joined.joinedTranslationInput("She is ready.", 13_101L) == null,
        "New capitalized speech must never merge")

    verify(TranslationQualityGate.check("I'm an assistant director.", "Ben [ad], [film/dizi] yardımcı yönetmeniyim.") ==
       TranslationQualityGate.Problem.INVENTED_PLACEHOLDER, "Fictional placeholders detected")
    verify(TranslationQualityGate.mustReject(TranslationQualityGate.Problem.INVENTED_PLACEHOLDER), "Placeholders blocked at overlay")
    verify(TranslationQualityGate.check("[Eve] Hello!", "[Eve] Merhaba!") == null, "Real speaker names preserved")
    verify(TranslationQualityGate.check("[groans]", "[inlemeler]") == null, "Legitimate sound cues preserved")
    verify(TranslationQualityGate.mayRemember("Hello.", "Merhaba."), "Normal output can be remembered")
    verify(!TranslationQualityGate.mayRemember("I'm a doctor.", "Ben [ad] doktorum."), "Poisoned translation never cached")
    println("MEANINGFUL FLOW: $passed/$passed PASS")
}
