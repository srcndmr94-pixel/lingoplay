package com.example.altyazi

/** Basit ve bagimsiz gercek gecikme ozeti. Yanitsiz denemeleri dahil etmez. */
object BenchmarkStats1035 {
    data class Summary(val count: Int, val median: Long?, val p90: Long?, val under500: Int)
    fun summarize(samples: List<Long>): Summary {
        val s = samples.sorted()
        if (s.isEmpty()) return Summary(0, null, null, 0)
        val mid = if (s.size % 2 == 1) s[s.size / 2]
            else s[s.size / 2 - 1] / 2 + s[s.size / 2] / 2 +
                (s[s.size / 2 - 1] % 2 + s[s.size / 2] % 2) / 2
        val p90 = s[((s.size * 9 + 9) / 10 - 1).coerceIn(0, s.lastIndex)]
        return Summary(s.size, mid, p90, s.count { it < 500L })
    }
}
