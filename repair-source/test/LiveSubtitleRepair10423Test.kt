package com.example.altyazi

fun main() {
    val names = listOf(
        Triple("Eve.", "Havva.", "Eve."),
        Triple("[Mark] Eve.", "Havva.", "Eve."),
        Triple("I saw Eve yesterday.", "Dün Havva'yı gördüm.", "Dün Eve'yı gördüm.")
    )
    // The first two demonstrate the name restored in source-anchored vocatives.
    names.take(2).forEach { (src, candidate, expected) ->
        val actual = NameFix.koru(src, candidate)
        check(actual == expected) { "name $src => $actual expected $expected" }
    }
    val roles = listOf(
        "[representative]" to "[temsilci]",
        "[delegate]" to "[delege]",
        "[representative 2]" to "[temsilci 2]",
        "[insects trilling]" to "[cırcır böcekleri ötüyor]",
        "[insects triling]" to "[cırcır böcekleri ötüyor]",
        "[crickets chirping]" to "[cırcır böcekleri ötüyor]"
    )
    roles.forEach { (src, expected) ->
        val actual = SoundTags.translate(src)
        check(actual == expected) { "cue $src => $actual expected $expected" }
    }
    val ocr = listOf(
        "But i'm still nerv Ous about them," to "But i'm still nervous about them,",
        "...it won't make a ifference." to "...it won't make a difference.",
        "[insects triling]" to "[insects trilling]",
        "The boy was nervous." to "The boy was nervous."
    )
    ocr.forEach { (src, expected) ->
        val actual = SubtitlePreparation.repair(src)
        check(actual == expected) { "OCR $src => $actual expected $expected" }
    }
    val ctx = DialogueContextRepair()
    ctx.remember("Nothing we do would make", 1000L)
    ctx.remember("any difference at all. -[insects trilling]", 1300L)
    check(ctx.joinedTranslationInput("any difference at all. -[insects trilling]", 1350L) ==
        "Nothing we do would make any difference at all. -[insects trilling]")
    check(ctx.joinedTranslationInput("[Eve] Nothing will change.", 1350L) == null)
    check(TranslationQualityGate.check(
        "Then my dad threw his back out at work,", "Sonra babam sırtını işten attı.") ==
        TranslationQualityGate.Problem.LITERAL_IDIOM)
    check(DialogueDisambiguation1038.repair(
        "Then my dad threw his back out at work,", "Sonra babam sırtını işten attı.") ==
        "Sonra babam işte belini incitti.")
    check(DialogueDisambiguation1038.repair(
        "...it won't make a difference.", "...bir şey olmaz.") ==
        "...hiçbir şeyi değiştirmeyecek.")
    check(DialogueDisambiguation1038.repair(
        "We went home.", "Sonra babam sırtını işten attı.") ==
        "Sonra babam sırtını işten attı.")
    val contextual = DialogueContextRepair()
    contextual.remember("[Eve] And then my powers came back.", 1000L)
    contextual.remember("[Mark] They just came back?", 1200L)
    check(contextual.contextualTranslation("[Mark] They just came back?") == "Kendiliğinden mi geri geldiler?")
    println("19/19 GameLingo 10.42.3 focus tests PASS")
}
