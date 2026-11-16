import com.example.altyazi.SubtitleReadGuard299

fun main() {
    var n = 0
    fun assertion(passed: Boolean, msg: String) { check(passed) { msg }; n++ }

    val g = SubtitleReadGuard299()
    assertion(g.shouldOcr(false, false, 1000L), "changed frame must run immediately")
    assertion(!g.shouldOcr(true, false, 1100L), "identical frame should be skipped briefly")
    assertion(!g.shouldOcr(true, false, 1649L), "idle recheck not yet due")
    assertion(g.shouldOcr(true, false, 1650L), "idle recheck must happen")
    assertion(!g.shouldOcr(true, true, 1800L), "candidate check not due yet")
    assertion(g.shouldOcr(true, true, 1830L), "pending candidate should be reconsidered early")
    assertion(g.shouldOcr(false, false, 1831L), "changed frame bypasses cooldown")
    g.reset()
    assertion(g.shouldOcr(true, false, 1832L), "reset must force fresh OCR")
    assertion(g.shouldOcr(true, false, 0L), "monotonic reset must not lock scanning")

    fun grow(a: String, b: String) = SubtitleReadGuard299.meaningfulExtension(a,b)
    assertion(grow("But he left behind a special medicine, Taken regularly, it keeps the",
        "But he left behind a special medicine, Taken regularly, it keeps the effects under control."), "long truncated line must be eligible for update")
    assertion(grow("The heavens in disarray... the stars fall out of place... a wandering",
        "The heavens in disarray... the stars fall out of place... a wandering warrior arrives."), "unfinished extended dialogue")
    assertion(grow("One day, I may become one of those things too. When that day",
        "One day, I may become one of those things too. When that day comes, don't hesitate."), "multi sentence truncation")
    assertion(!grow("Slaughter. Crush. Grind them to dust.", "Slaughter. Crush. Grind them to dust, wow."), "completed caption is not silently extended")
    assertion(!grow("There is a medicine", "There is a medecine"), "OCR one-character variation not extension")
    assertion(!grow("The souls of our fallen now return", "The souls of our fallen now returns."), "small OCR ending change not extension")
    assertion(!grow("Xie Qiudong", "Xie Qiudong!"), "punctuation should not create a new event")
    assertion(!grow("The souls return", "They were never here"), "different lines aren't expansions")

    for (caption in listOf("NO", "HELP ME", "WAIT", "GET OUT", "Come on", "I'M HERE", "Thank you", "RUN")) {
        assertion(SubtitleReadGuard299.shortSpokenLine(caption), "short shouted dialogue lost: $caption")
    }
    for (hud in listOf("O Move O Zoom", "Sprint", "A Carry", "Touchpad only", "L1", "R2", "MAP", "Controller Preset 1")) {
        assertion(!SubtitleReadGuard299.shortSpokenLine(hud), "HUD incorrectly treated as dialogue: $hud")
    }
    println("SubtitleReadGuard299: $n/$n PASS")
}
