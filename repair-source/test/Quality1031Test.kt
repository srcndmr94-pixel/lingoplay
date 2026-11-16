import com.example.altyazi.SourceAwareDialogueFix
import com.example.altyazi.SubtitlePreparation

fun main() {
    val ocr = mapOf(
        "your mnom used to" to "your mom used to",
        "It always find my way back to you." to "I always find my way back to you.",
        "You take the left, tIl take the right." to "You take the left, I'll take the right.",
        "She's stronger than she look S." to "She's stronger than she looks.",
        "[all gruntingl" to "[all grunting]",
        "Jimmy Can to stayed on." to "Jimmy Can to stayed on.",
        "[Allen] Okay." to "[Allen] Okay."
    )
    ocr.forEach { (src, expected) -> check(SubtitlePreparation.repair(src) == expected) { "OCR: $src -> ${SubtitlePreparation.repair(src)}" } }
    val translated = mapOf(
        "Uh, yeah, Zoe, this is not gonna work for me." to "Zoe, böyle olmaz.",
        "Okay now we're talking." to "İşte şimdi oldu!",
        "She can't lay more if we kill her." to "Onu öldürürsek bir daha yumurtlayamaz.",
        "And super pissed off." to "Üstelik çok öfkeli.",
        "You take the left, I'll take the right." to "Sen soldan git, ben sağdan gideceğim.",
        "I need you on lookout." to "Gözcülük yapmanı istiyorum."
    )
    translated.forEach { (src, expected) -> check(SourceAwareDialogueFix.correct(src) == expected) { "MT: $src" } }
    check(SourceAwareDialogueFix.correct("Completely new dialogue never seen before.") == null)
    println("PASS: ${ocr.size + translated.size + 1} cases")
}
