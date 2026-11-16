package com.example.altyazi

/** One bounded retry after a finished failure; never overlap the same subtitle. */
class SubtitleRetry205(private val cooldownMs: Long = 800L) {
    private var key = ""
    private var attempts = 0
    private var failedAt: Long? = null

    @Synchronized fun begin(source: String, nowMs: Long): Boolean {
        val next = source.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        if (next != key) { key = next; attempts = 0; failedAt = null }
        if (attempts >= 2) return false
        val failure = failedAt
        if (failure != null && nowMs >= failure && nowMs - failure < cooldownMs) return false
        attempts++
        failedAt = null
        return true
    }

    @Synchronized fun failed(nowMs: Long) { failedAt = nowMs }
    @Synchronized fun reset() { key = ""; attempts = 0; failedAt = null }
}
