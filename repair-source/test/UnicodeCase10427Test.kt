import com.example.altyazi.TranslationQualityGate
import com.example.altyazi.MeaningSafety
import com.example.altyazi.LiveSubtitleQuality10425

/** Standalone JVM regression: Kotlin IGNORE_CASE must preserve Turkish Unicode case-folding. */
fun main() {
    var passed = 0
    fun assertCase(ok: Boolean, message: String) {
        check(ok) { message }
        passed++
    }
    assertCase(TranslationQualityGate.check("Where's our payment?", "Ödememiz nerede?") == null,
        "Capital Ö in a good translation must match the lowercase Turkish requirement")
    assertCase(TranslationQualityGate.check("Where's our payment?", "Bitti mi?") ==
        TranslationQualityGate.Problem.MISSING_DETAIL, "Missing payment must be detected")
    assertCase(MeaningSafety.risk("Leave nobody alive!", "Kimseyi canlı bırak!") ==
        MeaningSafety.Risk.REVERSED_NEGATIVE_COMMAND, "Reversed command")
    assertCase(TranslationQualityGate.check("He called [Soul] to fight.", "[film/dizi] savaşmaya çağırdı.") ==
        TranslationQualityGate.Problem.INVENTED_PLACEHOLDER, "Fabricated placeholder")
    assertCase(LiveSubtitleQuality10425.defect("I'll be back for dessert.", "Geri gelirim.") ==
        LiveSubtitleQuality10425.Defect.MISSING_DETAIL, "Missing dessert")
    assertCase(LiveSubtitleQuality10425.defect("He's just cautious.", "İÇİNE KAPANIK.") == null,
        "Uppercase İ should be recognized in a good paraphrase")
    assertCase(TranslationQualityGate.check("The network service is back up.", "Ağ servisi YEDEKLENİYOR.") ==
        TranslationQualityGate.Problem.LITERAL_IDIOM, "Uppercase Turkish idiom")
    assertCase(TranslationQualityGate.check("Where the fuck you at?", "Neredesin?") ==
        TranslationQualityGate.Problem.LOST_PROFANITY, "Missing profanity")
    println("UNICODE_RUNTIME_PASS $passed/8")
}
