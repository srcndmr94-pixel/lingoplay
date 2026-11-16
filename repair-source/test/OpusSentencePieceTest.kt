package com.example.altyazi

import java.io.ByteArrayOutputStream

private fun varint(value: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var v = value
    do {
        val next = v and 0x7f
        v = v ushr 7
        out.write(if (v == 0) next else next or 0x80)
    } while (v != 0)
    return out.toByteArray()
}
private fun field(tag: Int, bytes: ByteArray): ByteArray = varint(tag) + varint(bytes.size) + bytes
private fun one(surface: String, type: Int = 1): ByteArray {
    // ModelProto.pieces = 1; SentencePiece.piece = 1; type = 3.
    val payload = field(10, surface.toByteArray(Charsets.UTF_8)) + byteArrayOf(24) + varint(type)
    return field(10, payload)
}
private fun training(modelType: Int): ByteArray = field(18, byteArrayOf(24) + varint(modelType))

fun main() {
    val s = OpusSentencePiece.fixture(listOf(
        OpusSentencePiece.Piece("<unk>", -100f, 2),
        OpusSentencePiece.Piece("▁", -6f, 1),
        OpusSentencePiece.Piece("▁Hello", 3f, 1),
        OpusSentencePiece.Piece("▁world", 4f, 1),
        OpusSentencePiece.Piece("!", 1f, 1),
        OpusSentencePiece.Piece("▁w", 0f, 1),
        OpusSentencePiece.Piece("orld", 0f, 1)
    ))
    check(s.tokenize("Hello world!") == listOf("▁Hello", "▁world", "!"))
    check(s.tokenize("  Hello   world !  ") == listOf("▁Hello", "▁world", "▁", "!"))
    check(s.tokenize("Hello") == listOf("▁Hello"))
    check(s.tokenize("").isEmpty())
    check(s.tokenize("Hello 🙂").contains("<unk>"))
    try { OpusSentencePiece.parse(byteArrayOf(0, 1, 2)); error("Malformed model accepted") }
    catch (_: IllegalArgumentException) { /* correct */ }
    val generated = ByteArrayOutputStream()
    generated.write(one("<unk>", 2))
    generated.write(one("▁Hello"))
    repeat(999) { generated.write(one("tok$it")) }
    generated.write(training(1))
    check(OpusSentencePiece.parse(generated.toByteArray()).tokenize("Hello") == listOf("▁Hello"))
    val wrong = generated.toByteArray() + training(2)
    try { OpusSentencePiece.parse(wrong); error("BPE model accepted") }
    catch (_: IllegalArgumentException) { /* correct */ }
    println("OPUS SentencePiece: 8/8 checks passed")
}
