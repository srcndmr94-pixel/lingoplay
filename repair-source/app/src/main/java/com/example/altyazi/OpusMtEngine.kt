package com.example.altyazi

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer

/**
 * LOCAL OPUS-MT EN->TR ONNX inference. No server, tokens, Google or Gemini.
 * Greedy decoder is a correctness-first baseline, NOT a measured <500 ms engine.
 * Never enable for all users before measured on-device tests against Google.
 * Model pack is created by tools/opus_mt/export_model.py.
 */
class OpusMtEngine(context: Context) : Closeable {
    private val folder = OpusModelPack.directory(context)
    private val initMutex = Mutex()
    private val translateMutex = Mutex()
    private val env = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var sourceSpm: OpusSentencePiece? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var targetVocab: Map<Int, String> = emptyMap()
    private var decoderStartId = -1
    private var eosId = -1
    private var padId = -1

    @Volatile var lastError: String? = null
        private set
    val ready get() = encoder != null && decoder != null

    suspend fun prepare(): Boolean = initMutex.withLock {
        if (ready) return@withLock true
        val invalid = OpusModelPack.validate(folder)
        if (invalid != null) { lastError = invalid; return@withLock false }
        return@withLock try {
            withContext(Dispatchers.IO) {
                val spm = OpusSentencePiece.parse(File(folder, "source.spm").readBytes())
                val metadata = JSONObject(File(folder, "model_info.json").readText())
                val json = JSONObject(File(folder, "vocab.json").readText())
                val map = HashMap<String, Int>(json.length())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getInt(key)
                }
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val enc = env.createSession(File(folder, "encoder_model.onnx").absolutePath, options)
                try {
                    val dec = env.createSession(File(folder, "decoder_model.onnx").absolutePath, options)
                    sourceSpm = spm
                    vocab = map
                    targetVocab = map.entries.associate { it.value to it.key }
                    decoderStartId = metadata.getInt("decoder_start_token_id")
                    eosId = metadata.getInt("eos_token_id")
                    padId = metadata.getInt("pad_token_id")
                    check(encoder == null && decoder == null)
                    encoder = enc
                    decoder = dec
                    lastError = null
                } catch (t: Throwable) {
                    enc.close()
                    throw t
                } finally { options.close() }
            }
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            false
        }
    }

    suspend fun translate(text: String, maxTokens: Int = 64): String? = translateMutex.withLock {
        if (!ready && !prepare()) return@withLock null
        val en = encoder ?: return@withLock null
        val de = decoder ?: return@withLock null
        val spm = sourceSpm ?: return@withLock null
        return@withLock try {
            withContext(Dispatchers.Default) {
                val tokens = spm.tokenize(text).take(160)
                if (tokens.isEmpty()) return@withContext null
                // Marian uses source piece IDs from the shared vocabulary and EOS.
                val ids = (tokens.map { vocab[it] ?: vocab["<unk>"] ?: 0 } + eosId).map(Int::toLong).toLongArray()
                val mask = LongArray(ids.size) { 1L }
                val encoderInputs = mutableMapOf<String, OnnxTensor>()
                encoderInputs["input_ids"] = tensor(ids)
                if (en.inputNames.contains("attention_mask")) encoderInputs["attention_mask"] = tensor(mask)
                try {
                    en.run(encoderInputs).use { encoded ->
                        val hidden = (encoded.get("last_hidden_state").orElse(null) ?: encoded[0]) as OnnxTensor
                        val decoded = ArrayList<Int>(maxTokens)
                        decoded.add(decoderStartId)
                        var step = 0
                        while (step < maxTokens.coerceIn(1, 96)) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            step++
                            val decoderInputs = mutableMapOf<String, OnnxTensor>()
                            decoderInputs["input_ids"] = tensor(decoded.map(Int::toLong).toLongArray())
                            val names = de.inputNames
                            // Exact supported Optimum export, no decoder-past required.
                            // Optimum may call the decoder input 'decoder_input_ids'.
                            if (!names.contains("input_ids") && names.contains("decoder_input_ids")) {
                                decoderInputs["decoder_input_ids"] = decoderInputs.remove("input_ids")!!
                            }
                            if (names.contains("encoder_hidden_states")) decoderInputs["encoder_hidden_states"] = hidden
                            else if (names.contains("encoder_outputs")) decoderInputs["encoder_outputs"] = hidden
                            else error("Decoder encoder_hidden_states girişi yok")
                            if (names.contains("encoder_attention_mask")) decoderInputs["encoder_attention_mask"] = tensor(mask)
                            else if (names.contains("attention_mask")) decoderInputs["attention_mask"] = tensor(mask)
                            try {
                                de.run(decoderInputs).use { res ->
                                    val logits = (res.get("logits").orElse(null) ?: res[0]) as OnnxTensor
                                    val shape = logits.info.shape
                                    require(shape.size == 3 && shape[2] in 1..200_000) { "Beklenmeyen decoder logits şekli" }
                                    val vocabularySize = shape[2].toInt()
                                    val buf = logits.floatBuffer
                                    val offset = (shape[1] - 1).toInt() * vocabularySize
                                    var best = Float.NEGATIVE_INFINITY
                                    var next = -1
                                    for (j in 0 until vocabularySize) {
                                        val value = buf.get(offset + j)
                                        if (!value.isNaN() && value > best) { best = value; next = j }
                                    }
                                    require(next >= 0) { "Decoder boş çıktı" }
                                    decoded.add(next)
                                }
                            } finally { decoderInputs.values.filter { it !== hidden }.toSet().forEach { it.close() } }
                            if (decoded.last() == eosId) break
                        }
                        decode(decoded)
                    }
                } finally { encoderInputs.values.forEach { it.close() } }
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            null
        }
    }

    private fun tensor(values: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))

    private fun decode(tokens: List<Int>): String = tokens.asSequence()
        .filter { it != decoderStartId && it != eosId && it != padId }
        .map { targetVocab[it] ?: "" }
        .filter { it.isNotEmpty() && !it.startsWith('<') }
        .joinToString("")
        .replace('▁', ' ').trim()

    override fun close() {
        encoder?.close(); decoder?.close()
        encoder = null; decoder = null
    }
}
