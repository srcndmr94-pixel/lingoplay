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

    override suspend fun translate(source: String, context: String): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.load()?.trim().orEmpty()
        require(apiKey.isNotEmpty()) { "API anahtarı kayıtlı değil." }

        val prompt = buildString {
            appendLine("Translate the CURRENT English game subtitle into natural Turkish.")
            appendLine("Return only the Turkish subtitle. Do not explain. Preserve speaker tags and sound captions.")
            if (context.isNotBlank()) {
                appendLine()
                appendLine(context.take(1600))
            }
            appendLine()
            append("CURRENT SUBTITLE: ")
            append(source.take(800))
        }

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        )

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
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
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
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
