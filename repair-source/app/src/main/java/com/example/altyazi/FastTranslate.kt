package com.example.altyazi

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Telefonun içindeki, internetsiz çalışan hızlı çeviri.
 * Türkçe modeli bir kereye mahsus internetten iner, sonrası internetsizdir.
 */
class FastTranslate {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    @Volatile
    var ready: Boolean = false
        private set

    /** Model inmediyse sebebi; ekranda gösterilir. */
    @Volatile
    var lastError: String? = null
        private set

    /** Model yoksa indirir. Başarısız olursa sonraki repliklerde yeniden denenir. */
    suspend fun prepare(): Boolean {
        if (ready) return true
        return suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    ready = true
                    lastError = null
                    if (cont.isActive) cont.resume(true)
                }
                .addOnFailureListener { e ->
                    ready = false
                    lastError = e.message ?: "model indirilemedi"
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    /** Hazır değilse bir kez daha indirmeyi dener, sonra çevirir. */
    suspend fun translate(text: String): String? {
        if (!ready && !prepare()) return null
        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { e ->
                    lastError = e.message ?: "çeviri başarısız"
                    if (cont.isActive) cont.resume(null)
                }
        }
    }

    fun close() {
        try {
            translator.close()
        } catch (_: Throwable) {
        }
    }
}
