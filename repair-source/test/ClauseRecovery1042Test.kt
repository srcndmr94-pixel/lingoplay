import com.example.altyazi.ClauseRecovery1042 as C
import com.example.altyazi.TranslationQualityGate as G
import com.example.altyazi.SourceAwareDialogueFix as F

fun main() {
    val missing = listOf(
        "Enjoy your dinner, guys. I'll be back for dessert." to "Yemeğinizin tadını çıkarın çocuklar.",
        "I don't think we should tell him about, you know, our troubles. Not just yet." to "Ona sorunlarımızı söylememeliyiz.",
        "He's not a cop, alright? He's just cautious." to "O polis değil, tamam mı?"
    )
    missing.forEach { (src, tr) ->
        check(G.check(src,tr) == null) // 10.42.1 legacy gate missed these.
        check(C.clauses(src)?.size == 2)
        check(C.needsSecondAttempt(src,tr))
    }
    check(!C.needsSecondAttempt("He wants it on the quiet. He only wants one of you.",
        "Bu işin gizli kalmasını istiyor. Sadece birinizi istiyor."))
    check(!C.needsSecondAttempt("Enjoy your dinner, guys. I'll be back for dessert.",
        "Afiyet olsun millet, tatlıya geri döneceğim."))
    check(C.needsSecondAttempt("Watch out! -And I fucking jumped.",
        "Dikkat et! - Ve ben de atladım.", lostProfanity = true))
    check(F.correct("And I fucking jumped.") == "Ben de atladım, amına koyayım.")
    check(!C.needsSecondAttempt("It started going faster and faster and it was getting harder to hold on.",
        "Gitgide hızlandı, tutunmak zorlaştı."))
    check(C.clauses("I don't think so.") == null)
    println("15/15 clause recovery assertions PASS")
}
