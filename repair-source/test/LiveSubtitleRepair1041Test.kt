import com.example.altyazi.TranslationQualityGate
import com.example.altyazi.SubtitlePreparation
import com.example.altyazi.DialogueDisambiguation1038

fun main() {
    val q = TranslationQualityGate.Problem.MISSING_QUESTION
    check(TranslationQualityGate.check("Where's Oliver? Where's my dad?", "Oliver nerede?") == q)
    check(TranslationQualityGate.check("Where's Oliver? Where's my dad?", "Oliver nerede? Babam nerede?") == null)
    check(TranslationQualityGate.check("What are we doing?", "Ne yapıyoruz?") == null)
    check(!TranslationQualityGate.mayRemember("Where's Oliver? Where's my dad?", "Oliver nerede?"))
    check(!TranslationQualityGate.mustReject(q))
    check(SubtitlePreparation.repair("[grunts] softly]") == "[grunts softly]")
    check(SubtitlePreparation.repair("[Zoe] strainingl") == "[Zoe straining]")
    check(SubtitlePreparation.repair("[groaningl") == "[groaning]")
    check(!SubtitlePreparation.confidentFirstRead("Surrender is your only opt"))
    check(SubtitlePreparation.confidentFirstRead("Surrender is your only option."))
    check(DialogueDisambiguation1038.repair("What are your orders?", "Siparişleriniz neler?") == "emirleriniz neler?")
    check(DialogueDisambiguation1038.repair("I placed my orders.", "Siparişlerimi verdim.") == "Siparişlerimi verdim.")
    check(DialogueDisambiguation1038.repair("It was touch and go for a while.", "Bir süre çok dokunaklıydı.") == "Bir süre durumu kritikti.")
    check(DialogueDisambiguation1038.repair("Emperor, you are avenged.", "İntikamınızı aldınız.") == "İntikamınız alındı.")
    println("14/14 GameLingo 10.41 subtitle regression checks PASS")
}
