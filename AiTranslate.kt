package com.example.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

object AiTranslate {

    private const val API_KEY = "AQ.Ab8RN6LLRxvKT1_z2zC0ODGiwXWfN1u11CHYuNUvTFnTBuRETA"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    suspend fun translateWithClaude(text: String, gameName: String = "RPG Game"): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        if (API_KEY.isBlank()) return@withContext "Hata: API Key yok"

        try {
            val url = URL("$API_URL?key=$API_KEY")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            // Gelişmiş Sistem Yönergesi (OCR Hatalarını ve Uzun Metinleri Düzeltme)
            val systemPrompt = """
                Sen profesyonel bir oyun altyazı çevirmenisin. 
                Sana gelen metin ekran OCR okuması olduğu için harf/boşluk hataları içerebilir (Örn: 'all iance' -> 'alliance', 'Man date' -> 'Mandate', 'Ifl' -> 'If I').
                
                GÖREVİN:
                1. Metindeki OCR okuma hatalarını anlam kurallarına göre zihinsel olarak düzelt.
                2. Oyun bağlamına uygun (Wuxia / Fantasy RPG / Aksiyon) doğal ve akıcı Türkçe altyazı üret.
                3. Terimleri doğru çevir (Örn: 'Mandate' -> 'Kader/Yetki', 'Baishan Sect' -> 'Baishan Mezhebi/Tarikatı', 'Maskwalkers' -> 'Maskeliler').
                4. Sadece Türkçe çeviriyi yanıt ver. Açıklama, not veya ek metin kesinlikle yazma.
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
                            return@withContext parts.getJSONObject(0).optString("text", "").trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext ""
    }
}