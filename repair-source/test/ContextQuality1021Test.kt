package com.example.altyazi

fun main() {
    val c = DialogueContextRepair()
    check(c.contextualTranslation("here before?") == null)
    c.remember("Have we been")
    c.remember("here before?")
    check(c.contextualTranslation("here before?") == "Daha önce buraya gelmiş miydik?")
    c.remember("We were young")
    c.remember("another line")
    check(c.contextualTranslation("another line") == null)
    c.reset()
    check(c.contextualTranslation("here before?") == null)

    c.remember("want to take our relationship")
    c.remember("to the next level. So..")
    check(c.contextualTranslation("to the next level. So..")?.contains("İlişkimizi") == true)
    c.remember("a completely new line")
    check(c.contextualTranslation("a completely new line") == null)

    c.reset()
    c.remember("The kid has enough to deal with")
    c.remember("without me messing him up the way I did Mark.")
    check(c.contextualTranslation("without me messing him up the way I did Mark.") ==
        "Ben de Mark'e yaptıklarımı ona yaşatmayayım.")
    c.reset()
    c.remember("Oh, uh, what was it you wanted to tell me yesterday,")
    c.remember("when my dad and Allen showed up?")
    check(c.contextualTranslation("when my dad and Allen showed up?")?.contains("Allen") == true)

    check(SubtitlePreparation.repair("What, didl leave without paying my tab?") ==
        "What, did I leave without paying my tab?")
    check(SubtitlePreparation.repair("Wet, that sounds like a challenge.") ==
        "Well, that sounds like a challenge.")
    check(SubtitlePreparation.repair("The floor is wet, don't slip.") ==
        "The floor is wet, don't slip.")
    check(SubtitlePreparation.repair("[suit Al] Repairs complete.") ==
        "[suit AI] Repairs complete.")
    check(Phrases.lookup("I wanted to spare Oliver from me.") == "Oliver'ı kendimden korumak istedim")
    check(Phrases.lookup("You got to say what you wanted to say.") == "Söylemek istediğini söyledin")
    check(Phrases.lookup("Omni-Man's ship is breaking orbit.") == "Omni-Man'in gemisi yörüngeden ayrılıyor")
    check(Phrases.lookup("He makes Debbie smile.") == "Debbie'yi güldürüyor")
    listOf("spare me", "He's in orbit.", "the art of war", "This wet paint is bad.").forEach {
        check(Phrases.lookup(it) == null)
    }
    println("OK 10.21: context, OCR, dictionary and negative controls")
}
