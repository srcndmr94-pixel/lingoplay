package com.example.altyazi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Optional official DeepL API. No key, no request; the key is never logged. */
object DeepLCeviri {
    private const val TIMEOUT_MS = 1800L
    private const val MAX_SOURCE = 1500
    private const val COOLDOWN_MS = 60_000L
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val ok = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    @Volatile private var nextAllowed = 0L
    @Volatile var lastStatus: String = "başlatılmadı"
        private set

    fun status(): String = "DeepL: ${ok.get()} başarılı, ${failed.get()} başarısız · $lastStatus"
    fun resetSession() { ok.set(0); failed.set(0); lastStatus = "hazır" }

    suspend fun translate(text: String, key: String, context: String = ""): String? {
        val source = text.trim()
        if (!DeepLPolicy.canRequest(source, key) || System.currentTimeMillis() < nextAllowed) return null
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("text", JSONArray().put(source))
                    .put("source_lang", "EN")
                    .put("target_lang", "TR")
                    .apply { if (context.isNotBlank()) put("context", context.take(500)) }
                    .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(DeepLPolicy.endpoint(key))
                    .header("Authorization", "DeepL-Auth-Key $key")
                    .header("Accept", "application/json")
                    .post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 403 || response.code == 429 || response.code == 456) nextAllowed = System.currentTimeMillis() + COOLDOWN_MS
                    if (!response.isSuccessful) {
                        failed.incrementAndGet()
                        lastStatus = "HTTP ${response.code}" // no response body or API key in diagnostics
                        null
                    } else {
                        val json = JSONObject(response.body?.string().orEmpty())
                        val translated = json.optJSONArray("translations")?.optJSONObject(0)?.optString("text")?.trim()
                        if (translated.isNullOrBlank()) {
                            failed.incrementAndGet()
                            lastStatus = "boş yanıt"
                            null
                        } else {
                            ok.incrementAndGet()
                            lastStatus = "hazır"
                            translated
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                failed.incrementAndGet(); lastStatus = "ağ/zaman aşımı"; null
            } catch (_: Exception) {
                failed.incrementAndGet(); lastStatus = "geçersiz yanıt"; null
            }
        }
    }
}

