package com.example.altyazi

/**
 * APK-299: OCR taramasının görünmeyen altyazıları atlamasını azaltan küçük, saf Kotlin kuralları.
 * Çeviri motoruna, ekran boyutuna veya bildirim servisine müdahale etmez.
 */
class SubtitleReadGuard299(
    private val idleRecheckMs: Long = 650L,
    private val candidateRecheckMs: Long = 180L
) {
    private var lastOcrAtMs = Long.MIN_VALUE

    /** Görüntü örnekleri aynı görünse bile OCR sonsuza dek durdurulmaz. */
    fun shouldOcr(sampleUnchanged: Boolean, pendingCandidate: Boolean, nowMs: Long): Boolean {
        val threshold = if (pendingCandidate) candidateRecheckMs else idleRecheckMs
        val elapsed = if (lastOcrAtMs == Long.MIN_VALUE || nowMs < lastOcrAtMs)
            Long.MAX_VALUE else nowMs - lastOcrAtMs
        if (!sampleUnchanged || elapsed >= threshold) {
            lastOcrAtMs = nowMs
            return true
        }
        return false
    }

    fun reset() { lastOcrAtMs = Long.MIN_VALUE }

    companion object {
        /**
         * OCR'nin önce kestiği metin sonradan anlamlı biçimde uzarsa bunu
         * aynı replik diye atma. Noktalama ve boşluk farkları uzama sayılmaz.
         * Tamamlanmış ayrı replikler veya sadece birkaç hatalı harf korunur.
         */
        fun meaningfulExtension(previous: String, current: String): Boolean {
            if (previous.isBlank() || current.isBlank()) return false
            val end = previous.trimEnd()
            if ((end.endsWith('.') && !end.endsWith("..")) || end.endsWith('!') || end.endsWith('?')) return false
            fun compact(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
            val old = compact(previous)
            val next = compact(current)
            if (old.length < 12 || !next.startsWith(old)) return false
            val added = next.substring(old.length)
            return added.length >= 8
        }

        private fun words(input: String): List<String> =
            Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?")
                .findAll(input.lowercase())
                .map { it.value.replace("’", "'").replace("'", "") }
                .toList()

        private val NEGATIONS = setOf(
            "no", "not", "never", "dont", "doesnt", "didnt", "cant", "cannot",
            "wont", "wouldnt", "shouldnt", "isnt", "arent", "wasnt", "werent"
        )

        /**
         * A second OCR observation may finish a line even when the previous
         * version ended with a spurious full stop. A short negation is also
         * significant, unlike a punctuation-only edit.
         */
        fun meaningfulRevision(previous: String, current: String): Boolean {
            if (previous.isBlank() || current.isBlank()) return false
            val a = words(previous)
            val b = words(current)
            if (a.isEmpty() || b.isEmpty() || a == b) return false
            if (smallCorrection(previous, current)) return true
            val aNeg = a.filter { it in NEGATIONS }
            val bNeg = b.filter { it in NEGATIONS }
            val sameContext = a.toSet().intersect(b.toSet()).size >= minOf(2, a.size - 1)
            if (sameContext && aNeg != bNeg) return true
            if (b.size < a.size) return false
            // Whole existing words must be retained, allowing a final OCR
            // partial word or one typo in an earlier word.
            var corrections = 0
            for (i in a.indices) {
                val old = a[i]
                val newer = b[i]
                if (old == newer) continue
                if (i == a.lastIndex && newer.startsWith(old) && newer.length > old.length) continue
                if (typoDistance(old, newer) <= 1) corrections++ else return false
                if (corrections > 1) return false
            }
            return if (b.size > a.size) true else {
                // Last word got completed, no extra word arrived yet.
                b.last().startsWith(a.last()) && b.last().length >= a.last().length + 2
            }
        }

        /**
         * OCR'nin tek harflik salinimlari yeniden ceviri baslatmaz.
         * "How... It's gone" / "How... lts gone" ayni repliktir.
         * Gercek olumsuzluk degisimi ve yeni ikinci satir ise yeniden islenir.
         * Bu, meaningfulRevision()'in OCR stabilizasyonunda kullanilan
         * daha genis anlamini degistirmez.
         */
        fun newMeaningForDispatch(previous: String, current: String): Boolean {
            if (!meaningfulRevision(previous, current)) return false
            if (!smallCorrection(previous, current)) return true
            val beforeNegations = words(previous).filter { it in NEGATIONS }
            val afterNegations = words(current).filter { it in NEGATIONS }
            return beforeNegations != afterNegations
        }

        /** One-token stable OCR correction: first-word typo must not be frozen. */
        fun smallCorrection(previous: String, current: String): Boolean {
            val a = words(previous)
            val b = words(current)
            if (a.size < 3 || a.size != b.size || a == b) return false
            val differences = a.indices.filter { a[it] != b[it] }
            if (differences.size != 1) return false
            val i = differences.single()
            return a[i].length >= 3 && b[i].length >= 3 &&
                typoDistance(a[i], b[i]) <= 1
        }

        private fun typoDistance(a: String, b: String): Int {
            if (a == b) return 0
            if (kotlin.math.abs(a.length - b.length) > 1) return 2
            if (a.length == b.length) return if (a.indices.count { a[it] != b[it] } == 1) 1 else 2
            val shorter = if (a.length < b.length) a else b
            val longer = if (a.length < b.length) b else a
            var i = 0
            var j = 0
            var edits = 0
            while (i < shorter.length && j < longer.length) {
                if (shorter[i] == longer[j]) { i++; j++ }
                else { edits++; if (edits > 1) return 2; j++ }
            }
            return 1
        }

        /** Bilinen oyun tuşları/HUD'yi kabul etmeden, noktalamasız kısa emirleri kurtar. */
        fun shortSpokenLine(text: String): Boolean {
            val t = text.trim().lowercase().replace(Regex("\\s+"), " ")
            return t in setOf(
                "help", "help me", "wait", "wait up", "stop", "stop it",
                "run", "run now", "hurry", "hurry up", "get out", "get back",
                "come on", "let's go", "lets go", "go away", "go now",
                "no", "yes", "no way", "thank you", "thanks", "i know",
                "i'm here", "im here", "be careful", "look out"
            )
        }
    }
}
