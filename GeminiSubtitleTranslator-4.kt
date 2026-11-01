package com.example.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class GeminiSubtitleTranslator(
    private val apiKeyStore: ApiKeyStore,
    private val model: String = "gemini-3.5-flash-lite"
) : SubtitleTranslator {

    private val systemInstruction = """
        You are a professional English-to-Turkish localization translator specialized in RPG, fantasy, and story-driven video games.
        Translate the current English game subtitle into fluent, natural, idiomatic Turkish suitable for an in-game subtitle overlay.

        Rules:
        1. Translate the intended meaning, never mechanically word-for-word.
        2. Use natural Turkish expressions appropriate to the situation.
        3. Preserve the character's tone and fantasy/period setting without unnecessarily archaic Turkish.
        4. Keep dialogue conversational, believable, and concise enough for a game subtitle overlay.
        5. Use surrounding dialogue only as context for meaning, pronouns, idioms, and incomplete sentences.
        6. Translate ONLY the CURRENT SUBTITLE. Never translate or repeat context lines.
        7. Do not add information not present in the source.
        8. Preserve proper names.
        9. Preserve meaningful sound captions such as [gunshot] or [music].
        10. Do not translate controller-button symbols or button names.
        11. If small obvious OCR recognition errors remain, infer the intended English from context before translating.
        12. Return ONLY the final Turkish subtitle. No explanations, alternatives, prefixes, notes, or quotation marks.

        Localization style examples:
        "A shame, till next time then." -> "Ne yazık. Artık bir dahaki sefere."
        "Have you business to contract with me?" -> "Benimle bir işin mi var?"
        "So, what'll you be needing?" -> "Peki, ne lazım?"
        "What can I do for you?" -> "Sana nasıl yardımcı olabilirim?"
    """.trimIndent()

    override suspend fun translate(source: String, context: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyStore.load()?.trim().orEmpty()
            require(apiKey.isNotEmpty()) { "API anahtarı kayıtlı değil." }

            val userPrompt = buildString {
                if (context.isNotBlank()) {
                    appendLine("CONTEXT (reference only; do not translate or repeat):")
                    appendLine(context.take(1600))
                    appendLine()
                }
                appendLine("CURRENT SUBTITLE (translate only this):")
                append(source.take(800))
            }

            val payload = JSONObject()
                .put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", userPrompt))
                            )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.25)
                        .put("maxOutputTokens", 220)
                )

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 9_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            try {
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    SafeApiLogger.httpError(status, body)
                    throw GeminiHttpException(status, SafeApiLogger.sanitize(body))
                }

                val result = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()

                if (result.isBlank()) throw IOException("Gemini boş yanıt döndürdü.")
                result
            } catch (error: SocketTimeoutException) {
                SafeApiLogger.timeout(error.message)
                throw error
            } finally {
                connection.disconnect()
            }
        }
}

class GeminiHttpException(
    val statusCode: Int,
    message: String
) : IOException(message)
