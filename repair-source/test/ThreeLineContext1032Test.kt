import com.example.altyazi.DialogueContextRepair

fun main() {
    val c = DialogueContextRepair()
    c.remember("This is our chance. If we go now,", 1000)
    c.remember("we might be able to reach the Coalition lines", 2000)
    c.remember("before those two nerds tear us apart.", 3000)
    check(c.joinedTranslationInput("before those two nerds tear us apart.", 3200) ==
        "This is our chance. If we go now, we might be able to reach the Coalition lines before those two nerds tear us apart.")
    val separate = DialogueContextRepair()
    separate.remember("I have to go.", 1000)
    separate.remember("we might reach the lines", 2000)
    check(separate.joinedTranslationInput("we might reach the lines", 2200) == null)
    val old = DialogueContextRepair()
    old.remember("If we go now,", 1000)
    old.remember("we can escape", 9000)
    check(old.joinedTranslationInput("we can escape", 9100) == null)
    println("PASS: three-line join, completed sentence isolation, stale context isolation")
}
