import com.example.altyazi.SubtitleReadGuard299
import com.example.altyazi.SubtitleOverlayPlacement299

fun main() {
    var passed = 0
    fun verify(condition: Boolean, name: String) {
        check(condition) { "FAIL: $name" }
        passed++
    }
    val gate = SubtitleReadGuard299()
    val source = java.io.File("app/src/main/java/com/example/altyazi/CaptureService.kt").takeIf { it.exists() }?.readText()
    if (source != null) {
        verify("handleText(lastOcrRaw)" !in source, "cached OCR cannot become a second reading")
        verify("ekranY(box.top) >= shownBoxTopPx" !in source, "do not delete every line below overlay")
        verify("value, screenHeight - dp(110), refined, draft, -1, null" in source && "protectedTopPx = -1, protectedBottomPx = -1" in source, "single-app mode keeps caption at bottom")
    }
    verify(gate.shouldOcr(false, true, 0L), "first new frame is observed")
    verify(!gate.shouldOcr(true, true, 100L), "cached OCR cannot count as second reading")
    verify(gate.shouldOcr(true, true, 181L), "fresh sample due at 180 ms")
    verify(SubtitleReadGuard299.meaningfulRevision("The cure arrived.", "The cure arrived. It saved her."), "fake period must not close subtitle")
    verify(SubtitleReadGuard299.meaningfulRevision("Wait", "Wait for me"), "short extension accepted")
    verify(SubtitleReadGuard299.meaningfulRevision("I will go", "I will not go"), "inserted negation")
    verify(SubtitleReadGuard299.meaningfulRevision("I can do that", "I can't do that"), "contraction changes meaning")
    verify(SubtitleReadGuard299.meaningfulRevision("He staved until morning", "He stayed until morning"), "one OCR typo in first half")
    verify(SubtitleReadGuard299.smallCorrection("He staved until morning", "He stayed until morning"), "correction needs 2 new observations")
    verify(!SubtitleReadGuard299.meaningfulRevision("Run now!", "Run now..."), "punctuation alone not new subtitle")
    verify(!SubtitleReadGuard299.meaningfulRevision("Look out", "Look there"), "different dialogue not a revision")
    verify(!SubtitleReadGuard299.meaningfulRevision("Soul: Move now", "Xie: Move now"), "speaker switch not a near typo")
    verify(!SubtitleReadGuard299.meaningfulRevision("Slaughter. Crush.", "Slaughter. Crush"), "spacing punctuation ignored")
    val oldLine = "One day I might become one of them."
    verify(SubtitleReadGuard299.meaningfulRevision(oldLine, oldLine + " When that day comes"), "second subtitle line appears")
    val safe = SubtitleOverlayPlacement299.choose(960, 700, 910, 880, 72, 40, true, 8, 6)
    verify(safe != null && !safe.below && safe.topPx + 72 <= 694, "below position falls back above band")
    val below = SubtitleOverlayPlacement299.choose(960, 400, 610, 700, 72, 40, true, 8, 6)
    verify(below != null && below.below && below.topPx >= 616 + 40 + 6, "both boxes safely below band")
    val above = SubtitleOverlayPlacement299.choose(960, 750, 920, 680, 120, 55, false, 8, 6)
    verify(above != null && !above.below && above.topPx + 120 <= 744, "large subtitle and previous stay above")
    val hidden = SubtitleOverlayPlacement299.choose(160, 35, 130, 10, 80, 0, true, 8, 6)
    verify(hidden == null, "no safe room: fail closed, never cover source")
    val noPrevious = SubtitleOverlayPlacement299.choose(960, 160, 800, 820, 120, 70, true, 8, 6)
    verify(noPrevious != null && !noPrevious.showPrevious && (noPrevious.below && noPrevious.topPx >= 806 || !noPrevious.below && noPrevious.topPx + 120 <= 154), "previous skipped when no room")
    println("OcrFlowReport299: $passed/$passed PASS")
}
