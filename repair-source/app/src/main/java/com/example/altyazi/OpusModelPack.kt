package com.example.altyazi

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Model weights are deliberately NOT bundled in the APK.
 * A package prepared by tools/opus_mt/export_model.py is imported once from
 * a user-selected ZIP. Only validated packages become active.
 */
object OpusModelPack {
    private const val DIR = "opus_mt_en_tr"
    private val importing = AtomicBoolean(false)
    val isImporting: Boolean get() = importing.get()
    private val REQUIRED = setOf(
        "encoder_model.onnx", "decoder_model.onnx", "source.spm", "target.spm", "vocab.json", "model_info.json"
    )
    private const val MAX_FILE_BYTES = 600L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 1_000L * 1024 * 1024

    fun directory(context: Context) = File(context.filesDir, DIR)
    fun isInstalled(context: Context) = validate(directory(context)) == null
    fun status(context: Context): String = validate(directory(context))?.let { "OPUS-MT hazır değil: $it" }
        ?: "OPUS-MT model paketi yüklü (ilk çeviri test edilmedi)"

    /** Only the OPUS model directory is removed; unrelated data stays intact. Caller blocks active sessions. */
    fun deleteInstalled(context: Context): Boolean {
        if (isImporting) return false
        val dir = directory(context)
        if (!dir.exists()) return true
        return dir.deleteRecursively() && !dir.exists()
    }

    /** Returns null on success or a short reason. Always leaves the old pack intact on failure. */
    fun import(context: Context, uri: Uri): String? {
        if (!importing.compareAndSet(false, true)) return "Başka model yüklemesi sürüyor"
        val target = directory(context)
        val temp = File(context.filesDir, "$DIR.importing")
        val backup = File(context.filesDir, "$DIR.previous")
        temp.deleteRecursively()
        if (!temp.mkdirs()) {
            importing.set(false)
            return "Geçici klasör oluşturulamadı"
        }
        return try {
            var total = 0L
            val seen = HashSet<String>()
            context.contentResolver.openInputStream(uri)?.use { incoming ->
                ZipInputStream(incoming.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        // No traversal, duplicate files, symlinks, or nested directories.
                        require(!entry.isDirectory && name in REQUIRED && seen.add(name)) {
                            "Beklenmeyen veya tekrarlanan model dosyası: $name"
                        }
                        val output = File(temp, name)
                        FileOutputStream(output).use { stream ->
                            val buf = ByteArray(64 * 1024)
                            var fileSize = 0L
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                fileSize += n
                                total += n
                                require(fileSize <= MAX_FILE_BYTES && total <= MAX_TOTAL_BYTES) { "Paket çok büyük" }
                                stream.write(buf, 0, n)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Seçilen dosya açılamadı")
            val reason = validate(temp)
            require(reason == null) { reason ?: "Geçersiz model" }
            backup.deleteRecursively()
            if (target.exists()) require(target.renameTo(backup)) { "Eski paket yedeklenemedi" }
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Model paketi etkinleştirilemedi")
            }
            backup.deleteRecursively()
            null
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        } finally {
            temp.deleteRecursively()
            importing.set(false)
        }
    }

    /** A model's identity is pinned: don't accept an unrelated language/model. */
    internal fun validate(root: File): String? {
        if (!root.isDirectory) return "model klasörü yok"
        if (!REQUIRED.all { File(root, it).isFile }) return "bazı model dosyaları eksik"
        if (File(root, "encoder_model.onnx").length() < 100_000 ||
            File(root, "decoder_model.onnx").length() < 100_000) return "ONNX dosyaları eksik veya bozuk"
        if (File(root, "source.spm").length() < 10_000 ||
            File(root, "target.spm").length() < 10_000) return "SentencePiece dosyaları bozuk"
        return try {
            val info = JSONObject(File(root, "model_info.json").readText())
            if (info.optString("model_id") != "Helsinki-NLP/opus-tatoeba-en-tr") "farklı model; bu çalıştırıcıyla uyumlu değil"
            else if (info.optInt("format_version") != 1) "bilinmeyen model paketi sürümü"
            else if (!info.has("pad_token_id") || !info.has("eos_token_id") || !info.has("decoder_start_token_id")) "token ayarları eksik"
            else if (JSONObject(File(root, "vocab.json").readText()).length() < 1000) "vocab eksik"
            else null
        } catch (t: Exception) { "model metaverisi okunamadı: ${t.javaClass.simpleName}" }
    }
}
