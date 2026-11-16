package com.example.altyazi

fun main() {
    var n = 0
    fun test(v: Boolean, text: String) { check(v) { text }; n++ }
    val gate = StableCaptionGate199()
    var current = 1L
    test(gate.firstValidated(1L) { it == current }, "first may show")
    test(!gate.firstValidated(1L) { it == current }, "late rewrite is forbidden")
    current = 2L
    test(!gate.firstValidated(1L) { it == current }, "old replik cannot return")
    test(gate.firstValidated(2L) { it == current }, "new replik can show")
    test(!gate.firstValidated(0L) { true }, "zero invalid")
    test(!RealtimeCaptionPolicy199.needsLocalHedge(false, true, "Hello"), "fast mode unchanged")
    test(!RealtimeCaptionPolicy199.needsLocalHedge(true, false, "Hello"), "without strong engine unchanged")
    test(!RealtimeCaptionPolicy199.needsLocalHedge(true, true, "[gasps]"), "sound tags instant")
    test(RealtimeCaptionPolicy199.needsLocalHedge(true, true, "I didn't start this."), "unknown dialogue hedged")
    test(RealtimeCaptionPolicy199.HEDGE_AFTER_MS < 500, "backup early")
    test(RealtimeCaptionPolicy199.LOCAL_TIMEOUT_MS < 600, "backup bounded")
    println("REALTIME 199: $n/$n PASS")
}
