package com.example.altyazi

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private fun checkThat(ok: Boolean, msg: String) { if (!ok) error(msg) }

fun main() {
    val gate = SubtitleGenerationGate()
    checkThat(!gate.isCurrent(0), "zero is not a real request")
    val a = gate.newSubtitle()
    checkThat(gate.isCurrent(a), "first subtitle accepted")
    val b = gate.newSubtitle()
    checkThat(!gate.isCurrent(a) && gate.isCurrent(b), "A completes after B: A must never overlay B")
    val aAgain = gate.newSubtitle()
    checkThat(!gate.isCurrent(a) && gate.isCurrent(aAgain), "repeated source has a different generation")
    gate.invalidate()
    checkThat(!gate.isCurrent(aAgain), "stopped/empty subtitle invalidates pending answer")
    val resumed = gate.newSubtitle()
    checkThat(gate.isCurrent(resumed), "new capture can display")
    val pool = Executors.newFixedThreadPool(6)
    val latch = CountDownLatch(160)
    repeat(160) {
        pool.submit {
            gate.newSubtitle()
            latch.countDown()
        }
    }
    checkThat(latch.await(7, TimeUnit.SECONDS), "parallel updates finish")
    pool.shutdownNow()
    checkThat(!gate.isCurrent(resumed), "all concurrent updates advance generation")
    println("PASS: 8 request-identity scenarios + 160 concurrent updates")
}
