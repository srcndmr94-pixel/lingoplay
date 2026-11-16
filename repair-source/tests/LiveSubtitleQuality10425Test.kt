import com.example.altyazi.LiveSubtitleQuality10425 as L
import com.example.altyazi.TranslationQualityGate as G
import com.example.altyazi.SourceAwareDialogueFix as S
import com.example.altyazi.SubtitlePreparation as P

fun main() {
    var passed = 0
    fun test(label: String, result: Boolean) {
        check(result) { "FAILED: $label" }
        passed++
    }
    test("DeepL English contraction residue", L.defect("Isn't that kinda... extreme?", "Isn't biraz... aşırı değil mi?") == L.Defect.ENGLISH_RESIDUE)
    test("Apostrophe curly", L.defect("Isn't it?", "Isn’t doğru mu?") == L.Defect.ENGLISH_RESIDUE)
    test("Clean Turkish stays clean", L.defect("Isn't that extreme?", "Bu biraz aşırı değil mi?") == null)
    test("Mark name isn't residue", L.defect("[Mark] Okay.", "[Mark] Tamam.") == null)
    test("Eve name isn't residue", L.defect("[Mark] Eve.", "[Mark] Eve.") == null)
    test("Dropped dessert", L.defect("Enjoy your dinner, guys. I'll be back for dessert.", "Yemeğinizin tadını çıkarın çocuklar.") == L.Defect.MISSING_DETAIL)
    test("Dessert preserved", L.defect("Enjoy your dinner, guys. I'll be back for dessert.", "Afiyet olsun. Tatlıya döneceğim.") == null)
    test("Dropped cautious", L.defect("He's not a cop, alright? He's just cautious.", "O polis değil, tamam mı?") == L.Defect.MISSING_DETAIL)
    test("Cautious preserved", L.defect("He's not a cop, alright? He's just cautious.", "O polis değil, sadece temkinli.") == null)
    test("Payment dropped", L.defect("Where's our payment? Please let us know", "Lütfen bize haber verin.") == L.Defect.MISSING_DETAIL)
    test("Payment preserved", L.defect("Where's our payment? Please let us know", "Ödememiz nerede? Lütfen bize haber verin.") == null)
    test("Different dessert context unchanged", L.defect("I like this cake", "Bu pastayı seviyorum.") == null)
    test("Meaning missing detail integrates gate", G.check("Enjoy your dinner, guys. I'll be back for dessert.", "Yemeğinizin tadını çıkarın çocuklar.") == G.Problem.MISSING_DETAIL)
    test("Cautious missing detail integrates gate", G.check("He's not a cop, alright? He's just cautious.", "O polis değil, tamam mı?") == G.Problem.MISSING_DETAIL)
    test("English fragment integrates gate", G.check("Isn't that kinda... extreme?", "Isn't biraz... aşırı değil mi?") == G.Problem.ENGLISH_FRAGMENT)
    test("No extra missing-detail false positive", G.check("Don't panic!", "Sakin ol!") != G.Problem.MISSING_DETAIL)
    test("Original rule no negative reversal", G.check("Leave no one alive.", "Kimseyi canlı bırak.") == G.Problem.REVERSED_COMMAND)
    test("Original rule correct", G.check("Leave no one alive.", "Kimseyi sağ bırakma.") == null)
    test("Dialogue for all we know", S.correct("Dude could be a cop for all we know.") == "Bu adam polis bile olabilir, nereden bilelim?")
    test("Dialogue keep off meaning", S.correct("Yeah, yeah. You gotta know when to get off, Jason.")!!.contains("çekileceğini"))
    test("Dialogue resumed network", S.correct("Our planetary defense network is back up.")!!.contains("yeniden devrede"))
    test("Dialogue both clauses", S.correct("Enjoy your dinner, guys. I'll be back for dessert.")!!.contains("Tatlıya geri geleceğim"))
    test("Dialogue cautious", S.correct("He's not a cop, alright? He's just cautious.")!!.contains("temkinli"))
    test("Dialogue Oliver", S.correct("Then why don't you go see Oliver?")!!.contains("görmüyorsun"))
    test("Dialogue shoes idiom", S.correct("I got big shoes to fill.")!!.contains("sorumluluk"))
    test("Dialogue not spurious", S.correct("This is a completely new scene.") == null)
    test("Dialogue profanity", S.correct("And I fucking jumped.")!!.contains("amına koyayım"))
    test("Old dialogue maintained", S.correct("Well, Brian was quite the stud back in the day.")!!.contains("çapkındı"))
    test("Lowercase William speaker", P.repair("[william] Mark?") == "[William] Mark?")
    test("Damaged William speaker", P.repair("[Wiliam] Hmm.") == "[William] Hmm.")
    test("Sound cue unchanged", P.repair("[groans]") == "[groans]")
    test("Normal William word unchanged", P.repair("The name william appears") == "The name william appears")
    println("$passed/$passed GameLingo 10.42.5 live quality tests passed")
}
