package com.example.altyazi

fun main() {
    val r = GoogleRateGuard()
    check(r.remainingMs(100L) == 0L)
    check(r.on429(1_000L, null) == 120_000L)
    check(r.remainingMs(1_000L + 119_999L) == 1L)
    check(r.remainingMs(121_000L) == 0L)
    check(r.on429(121_000L, "400") == 400_000L)
    check(r.remainingMs(121_000L + 400_000L) == 0L)
    check(r.on429(600_000L, "malformed") == 480_000L)
    check(r.on429(1_100_000L, "99999999") == 900_000L)
    r.onSuccess()
    check(r.on429(2_100_000L, null) == 120_000L)
    r.reset()
    check(r.remainingMs(2_100_000L) == 0L)

    check(SubtitlePreparation.repair("We have never been mnore unified,") ==
        "We have never been more unified,")
    check(SubtitlePreparation.repair("It is timne we were at war.") ==
        "It is time we were at war.")
    check(SubtitlePreparation.repair("H don't know.") == "I don't know.")
    check(SubtitlePreparation.repair("Art met Paul yesterday.") == "Art met Paul yesterday.")
    check(GoogleSourcePreparation.prepare("I'm coming, too.") == "I'm coming as well.")
    check(GoogleSourcePreparation.prepare("It's too much.") == "It's too much.")
    println("OK: 429 backoff, cooldown, reset, conservative OCR, too context (13 checks)")
}
