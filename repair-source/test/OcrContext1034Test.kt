import com.example.altyazi.SubtitlePreparation
import com.example.altyazi.DialogueContextRepair

fun main() {
    val ocrCases = listOf(
        "lever tell you about uh... the fairground?" to "Ever tell you about uh... the fairground?",
        "Wet, Ill walk you out, I need the ladies." to "Well, I'll walk you out, I need the ladies.",
        "Wet paint!" to "Wet paint!",
        "The lever tells you about the danger." to "The lever tells you about the danger.",
        "I'll walk you out." to "I'll walk you out.",
        "Did I ever tell you about that fairground?" to "Did I ever tell you about that fairground?",
        "Wet, Ill mention this tomorrow." to "Wet, Ill mention this tomorrow."
    )
    ocrCases.forEach { (input, expected) ->
        check(SubtitlePreparation.repair(input) == expected) {
            "OCR: '$input' -> '${SubtitlePreparation.repair(input)}' != '$expected'"
        }
    }
    val context = DialogueContextRepair()
    context.remember("Brian was quite the stud back in the day.", 1000)
    context.remember("Was?!", 1200)
    check(context.contextualTranslation("Was?!") == "Eskiden mi?!")
    val isolated = DialogueContextRepair()
    isolated.remember("Was?!", 2000)
    check(isolated.contextualTranslation("Was?!") == null)
    val unrelated = DialogueContextRepair()
    unrelated.remember("He was tall.", 3000)
    unrelated.remember("Was?!", 3200)
    check(unrelated.contextualTranslation("Was?!") == null)
    println("10/10 OCR and contextual tests PASS")
}
