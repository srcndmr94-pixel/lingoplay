package com.example.altyazi
import kotlinx.coroutines.runBlocking
import okhttp3.StubNetwork
fun main() = runBlocking {
    GoogleCeviri.temizle()
    StubNetwork.status = 429
    check(GoogleCeviri.cevir("hello") == null)
    check(StubNetwork.attempts == 1)
    check(!GoogleCeviri.kullanilabilir)
    GoogleCeviri.temizle() // region-screen restart must NOT reset HTTP 429.
    check(!GoogleCeviri.kullanilabilir)
    check(GoogleCeviri.cevir("new subtitle") == null)
    check(StubNetwork.attempts == 1) // zero repeated network attempts
    check("HTTP 429" in GoogleCeviri.durum())
    println("OK: GoogleCeviri production Kotlin compiled; 429 immediate pause, restart doesn't retry")
}
