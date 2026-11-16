package com.example.altyazi

fun main() {
    val expected = listOf(
        "Jonathan Crane." to 2,
        "I spoke with Jonathan Crane yesterday." to 2,
        "Jane Overstreet!" to 2,
        "That was Jonathan Crane's idea." to 2,
        "The Brain Bowl at Galactic Grill." to 4,
        "Emperor Argall is gone." to 0,
        "Old Batman isn't here." to 0,
        "Your Brain Is Here." to 0,
        "Jonathan came back." to 0,
        "Jonathan crane was here." to 0,
        "Jonathan-Crane is a hyphenated label." to 0
    )
    expected.forEach { (s, count) ->
        val n = ProperNameSequence1039.protectedStarts(s).size
        check(n == count) { "Name detection failed for $s: $n expected $count" }
    }
    check(NameShield.shield("Jonathan Crane.").slots.map { it.ad } == listOf("Jonathan", "Crane"))
    val encoded = NameShield.shield("I spoke to Jonathan Crane yesterday.")
    check(encoded.slots.map { it.ad } == listOf("Jonathan", "Crane"))
    check(NameShield.restore("I spoke to Qx1 Qx2 yesterday.", encoded.slots) ==
        "I spoke to Jonathan Crane yesterday.")
    println("PASS ${expected.size} name recognition + 3 full NameShield tests")
}
