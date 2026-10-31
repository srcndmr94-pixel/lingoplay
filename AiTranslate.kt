package com.example.subtitles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

object AiTranslate {

    suspend fun translateWithGemini(
        text: String,
        apiKey: String,
        modelName: String = "gemini-flash-latest"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("401: Empty Key"))

        var attempts = 0
        val maxAttempts = 2

        while (attempts < maxAttempts) {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
                val url = URL("$endpoint?key=$apiKey")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true

                val systemPrompt = """
                    Sen profesyonel bir oyun altyazı çevirmenisin.
                    Sana gelen metin ekran OCR taranmasıdır. Bu yüzden 'If I' kelimesi 'IFL' veya 'Ifl', 'I' harfi 'l' veya '1' olarak hatalı okunmuş olabilir.

                    OYUN BAĞLAMI: Bu bir Wuxia / dövüş sanatları oyunudur (örn. Phantom Blade Zero).
                    Aşağıdaki terimler özel isimdir, birebir çevirme:
                    - "the Order" = Tarikat (asla "sipariş" değil)
                    - "Sect" = Klan / Tarikat (asla "mezhep" değil)
                    - "Perfect Transcension" = Kusursuz Yükseliş
                    - "Sha Chi", "Qi", "Dao" = olduğu gibi bırak
                    - Büyük harfle yazılmış iki kelimelik adlar ("Iron Fever" gibi) beceri/teknik adıdır, anlamını koruyarak çevir veya olduğu gibi bırak

                    GÖREVİN:
                    1. Metindeki 'IFL', 'Ifl', 'I1' gibi OCR yazım hatalarını 'If I' veya 'I' olarak düzelt.
                    2. Metnin TAMAMINI eksiksiz çevir.
                    3. Oyun bağlamına uygun doğal Türkçe altyazı dili kullan.
                    4. Sadece çevrilmiş Türkçe metni döndür. Not veya açıklama ekleme.
                """.trimIndent()

                val jsonBody = JSONObject().apply {
                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemPrompt) })
                        }
                        put("parts", parts)
                    }
                    put("system_instruction", systemInstruction)

                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", text) })
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseText)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val translated = parts.getJSONObject(0).optString("text", "").trim()
                                if (translated.isNotEmpty()) {
                                    return@withContext Result.success(translated)
                                }
                            }
                        }
                    }
                    return@withContext Result.failure(Exception("EMPTY_RESPONSE"))
                } else {
                    conn.errorStream?.use { it.readBytes() }
                    when (responseCode) {
                        503 -> {
                            attempts++
                            if (attempts < maxAttempts) {
                                delay(400)
                                continue
                            }
                            return@withContext Result.failure(Exception("503: High Demand"))
                        }
                        401 -> return@withContext Result.failure(Exception("401: Invalid Key"))
                        404 -> return@withContext Result.failure(Exception("404: Model Not Found"))
                        429 -> return@withContext Result.failure(Exception("429: Rate Limit Exceeded"))
                        else -> return@withContext Result.failure(Exception("$responseCode: HTTP Error"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("NET_ERROR: ${e.localizedMessage}"))
            } finally {
                conn?.disconnect()
            }
        }

        return@withContext Result.failure(Exception("UNKNOWN"))
    }
}