package com.example.altyazi

/** Anahtarsız Google endpoint'inde HTTP 429 alındığında tekrar istek yağdırmaz. */
class GoogleRateGuard {
    private var strikes = 0
    private var blockedUntilMs = 0L

    @Synchronized fun remainingMs(nowMs: Long): Long =
        (blockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized fun on429(nowMs: Long, retryAfterHeader: String?): Long {
        strikes = (strikes + 1).coerceAtMost(4)
        val exponentialMs = when (strikes) {
            1 -> 120_000L
            2 -> 240_000L
            3 -> 480_000L
            else -> 900_000L
        }
        val serverMs = retryAfterHeader?.trim()?.toLongOrNull()
            ?.coerceIn(0L, 900L)?.times(1000L) ?: 0L
        val delay = maxOf(exponentialMs, serverMs)
        blockedUntilMs = maxOf(blockedUntilMs, nowMs + delay)
        return delay
    }

    @Synchronized fun onSuccess() {
        strikes = 0
        blockedUntilMs = 0L
    }

    @Synchronized fun reset() {
        strikes = 0
        blockedUntilMs = 0L
    }
}
