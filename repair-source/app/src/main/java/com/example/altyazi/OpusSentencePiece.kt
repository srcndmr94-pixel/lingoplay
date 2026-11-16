package com.example.altyazi

import java.text.Normalizer

/**
 * Small, dependency-free SentencePiece *unigram* reader for Marian/OPUS models.
 * Does not read arbitrary protobuf fields beyond ModelProto.pieces. Models using
 * BPE, custom normalization rules, or byte fallback MUST be rejected by pack
 * preparation instead of silently returning incorrect token IDs.
 */
internal class OpusSentencePiece private constructor(private val pieces: List<Piece>) {
    data class Piece(val surface: String, val score: Float, val type: Int)

    companion object {
        private const val NORMAL = 1
        private const val UNKNOWN = 2
        private const val USER_DEFINED = 4
        private const val MAX_PIECE_BYTES = 8192

        fun parse(bytes: ByteArray): OpusSentencePiece {
            require(bytes.size in 16..4_000_000) { "SentencePiece model boyutu geçersiz" }
            val outer = ProtoCursor(bytes)
            val items = ArrayList<Piece>()
            var modelType = 1 // SentencePiece UNIGRAM default
            while (!outer.end()) {
                val tag = outer.varint().toInt()
                require(tag > 0)
                if (tag ushr 3 == 1 && tag and 7 == 2) {
                    val inner = ProtoCursor(outer.bytes(32_000))
                    var surface = ""
                    var score = -100f
                    var type = NORMAL
                    while (!inner.end()) {
                        val f = inner.varint().toInt()
                        when {
                            f ushr 3 == 1 && f and 7 == 2 -> surface = inner.bytes(MAX_PIECE_BYTES).toString(Charsets.UTF_8)
                            f ushr 3 == 2 && f and 7 == 5 -> score = Float.fromBits(inner.fixed32())
                            f ushr 3 == 3 && f and 7 == 0 -> type = inner.varint().toInt()
                            else -> inner.skip(f and 7)
                        }
                    }
                    if (surface.isNotEmpty()) items.add(Piece(surface, score, type))
                } else if (tag ushr 3 == 2 && tag and 7 == 2) {
                    // TrainerSpec.model_type (field 3): BPE requires a different encoder.
                    val trainer = ProtoCursor(outer.bytes(100_000))
                    while (!trainer.end()) {
                        val f = trainer.varint().toInt()
                        if (f ushr 3 == 3 && f and 7 == 0) modelType = trainer.varint().toInt()
                        else trainer.skip(f and 7)
                    }
                } else outer.skip(tag and 7)
            }
            require(modelType == 1) { "Bu OPUS modeli SentencePiece unigram kullanmıyor" }
            require(items.size >= 1000) { "Eksik SentencePiece modeli: ${items.size} token" }
            require(items.any { it.type == UNKNOWN }) { "SentencePiece <unk> eksik" }
            require(items.none { it.type == 6 }) { "Byte fallback bu sürümde desteklenmiyor" }
            return OpusSentencePiece(items)
        }

        /** Tiny artificial vocabulary for deterministic tokenization tests only. */
        internal fun fixture(p: List<Piece>): OpusSentencePiece = OpusSentencePiece(p)
    }

    private val byPrefix: Map<Char, List<Piece>> = pieces
        .filter { it.type == NORMAL || it.type == USER_DEFINED }
        .groupBy { it.surface[0] }
    private val unk: Piece = pieces.firstOrNull { it.type == UNKNOWN } ?: Piece("<unk>", -100f, UNKNOWN)

    /** Tokenize with unigram Viterbi and explicit unknown-character fallback. */
    fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return emptyList()
        val input = "▁" + normalized.replace(' ', '▁')
        val n = input.length
        val costs = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val bestPiece = arrayOfNulls<String>(n + 1)
        val prev = IntArray(n + 1)
        costs[0] = 0.0
        for (i in 0 until n) {
            if (costs[i] == Double.NEGATIVE_INFINITY) continue
            var matched = false
            for (p in byPrefix[input[i]].orEmpty()) {
                if (!input.startsWith(p.surface, i)) continue
                val j = i + p.surface.length
                val next = costs[i] + p.score
                if (next > costs[j]) {
                    costs[j] = next
                    bestPiece[j] = p.surface
                    prev[j] = i
                }
                matched = true
            }
            if (!matched || costs[i + 1] == Double.NEGATIVE_INFINITY) {
                val length = if (Character.isHighSurrogate(input[i]) && i + 1 < n) 2 else 1
                val j = i + length
                val next = costs[i] - 100.0
                if (next > costs[j]) {
                    costs[j] = next
                    bestPiece[j] = unk.surface
                    prev[j] = i
                }
            }
        }
        require(costs[n] != Double.NEGATIVE_INFINITY) { "Metin tokenlara ayrılamadı" }
        val result = ArrayList<String>()
        var end = n
        while (end > 0) {
            result.add(bestPiece[end] ?: unk.surface)
            val before = prev[end]
            check(before < end)
            end = before
        }
        result.reverse()
        return result
    }
}

/** A constrained protobuf decoder; rejects malformed varints and oversized segments. */
internal class ProtoCursor(private val data: ByteArray) {
    private var at = 0
    fun end(): Boolean = at == data.size
    fun varint(): Long {
        var out = 0L
        for (shift in 0..63 step 7) {
            require(at < data.size) { "Bozuk protobuf: yarım varint" }
            val b = data[at++].toInt() and 255
            if (shift == 63) require(b <= 1)
            out = out or ((b and 127).toLong() shl shift)
            if (b and 128 == 0) return out
        }
        error("Bozuk protobuf: uzun varint")
    }
    fun bytes(max: Int): ByteArray {
        val length = varint()
        require(length in 0..max.toLong() && length <= data.size - at) { "Bozuk protobuf: boyut" }
        val out = data.copyOfRange(at, at + length.toInt())
        at += length.toInt()
        return out
    }
    fun fixed32(): Int {
        require(data.size - at >= 4)
        var v = 0
        for (i in 0..3) v = v or ((data[at++].toInt() and 255) shl (8 * i))
        return v
    }
    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> { require(data.size - at >= 8); at += 8 }
            2 -> bytes(data.size)
            5 -> { require(data.size - at >= 4); at += 4 }
            else -> error("Desteklenmeyen protobuf alanı: $wire")
        }
    }
}
