package com.example.altyazi

/** A bounded rolling latency snapshot. Only real network calls are recorded. */
class LatencyWindow1037(private val capacity: Int = 60) {
    init { require(capacity > 0) }
    private val values = java.util.ArrayDeque<Long>()

    @Synchronized fun add(ms: Long) {
        values.addLast(ms.coerceAtLeast(0L))
        if (values.size > capacity) values.removeFirst()
    }

    @Synchronized fun clear() = values.clear()

    @Synchronized fun summary(): String? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        fun percentile(p: Double): Long = sorted[
            kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        ]
        return "son ${sorted.size} ağ çağrısı: p50 ${percentile(0.5)} ms, p90 ${percentile(0.9)} ms"
    }
}
