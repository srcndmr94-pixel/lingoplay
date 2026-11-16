import com.example.altyazi.SourceAwareDialogueFix
fun main() {
    check(SourceAwareDialogueFix.correct("Were not that late.") == "O kadar da geç kalmadık.")
    check(SourceAwareDialogueFix.correct("We're not that late.") == "O kadar da geç kalmadık.")
    check(SourceAwareDialogueFix.correct("You know, we're in some deep fucking shit.") == "Biliyorsun, fena hâlde boka battık.")
    check(SourceAwareDialogueFix.correct("Nice. On my way.") == "Güzel. Yoldayım.")
    check(SourceAwareDialogueFix.correct("He wants it on the quiet. He only wants one of you.")?.contains("gizli") == true)
    check(SourceAwareDialogueFix.correct("Unrelated sentence.") == null)
    check(SourceAwareDialogueFix.correct("You got a point?") == "Sadede gelecek misin?")
    check(SourceAwareDialogueFix.correct("They had bale mazes and Ferris wheel.")?.contains("dönme dolap") == true)
    check(SourceAwareDialogueFix.correct("Yeah, let's take in the view.")?.contains("manzaranın tadını") == true)
    check(SourceAwareDialogueFix.correct("Everybody, hands where we can see them!")?.contains("ellerini") == true)
    check(SourceAwareDialogueFix.correct("And I fucking jumped.")?.contains("amına koyayım") == true)
    check(SourceAwareDialogueFix.correct("What did I fucking say?")?.contains("amına koyayım") == true)
    check(SourceAwareDialogueFix.correct("Jimmy Can to stayed on.") == null) // Ambiguous OCR: never invent a name.
    check(SourceAwareDialogueFix.correct("Jimmy stayed on.") == null)
    check(SourceAwareDialogueFix.correct("Look what the law chased in.")?.contains("polis") == true)
    check(SourceAwareDialogueFix.correct("The locals are feisty!")?.contains("dişli") == true)
    check(SourceAwareDialogueFix.correct("Cracked his head open. Died that night.")?.contains("Kafatası") == true)
    check(SourceAwareDialogueFix.correct("I get that too.") == "Onu da anlıyorum.")
    println("18/18 PASS")
}
