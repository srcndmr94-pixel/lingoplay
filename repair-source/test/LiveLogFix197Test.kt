import com.example.altyazi.SourceAwareDialogueFix
import com.example.altyazi.GameplayLogRepair299

fun main() {
    fun test(input: String, expected: String) {
        check(SourceAwareDialogueFix.correct(input) == expected) {
            "Unexpected: $input => ${SourceAwareDialogueFix.correct(input)}"
        }
    }
    test("[Phase Two] You're Phase One..", "[Phase Two] Sen Phase One'sın...")
    test("..Phase Two,", "...Phase Two,")
    test("Phase Three,", "Phase Three,")
    test("[Phase] Four,", "Phase Four,")
    test("[Phase] Five.", "Phase Five.")
    test("They've never been out of their tanks", "Tanklarından hiç çıkmadılar.")
    test("long enough to learn to talk.", "Konuşmayı öğrenecek kadar uzun süre.")
    check(SourceAwareDialogueFix.correct("They're in their tanks.") == null)
    check(SourceAwareDialogueFix.correct("We're in phase one of the plan.") == null)
    check(!GameplayLogRepair299.plausibleSpeakerName("Hey"))
    check(!GameplayLogRepair299.plausibleSpeakerName("Yo"))
    check(GameplayLogRepair299.plausibleSpeakerName("Raymond"))
    check(GameplayLogRepair299.plausibleSpeakerName("Old Man Gen"))
    check(GameplayLogRepair299.repairOcr("Normal dialogue.") == "Normal dialogue.")
    println("14/14 LIVE LOG PASS")
}
