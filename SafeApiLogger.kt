package com.example.subtitles

import android.util.Log

object SafeApiLogger {
    private const val TAG = "SubtitleAI"
    private const val MAX_BODY = 4000
    private val secrets = listOf(
        Regex("(?i)(x-goog-api-key\\s*[:=]\\s*)[^\\s,}]+"),
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,}]+"),
        Regex("(?i)(\"(?:api[_-]?key|access[_-]?token|token)\"\\s*:\\s*\")[^\"]+(\")")
    )

    fun sanitize(value: String?): String {
        var result = value.orEmpty()
        secrets.forEach { pattern ->
            result = pattern.replace(result) { match ->
                if (match.groupValues.size >= 3) {
                    match.groupValues[1] + "[REDACTED]" + match.groupValues[2]
                } else {
                    match.groupValues[1] + "[REDACTED]"
                }
            }
        }
        return if (result.length <= MAX_BODY) result else result.take(MAX_BODY) + "...[TRUNCATED]"
    }

    fun httpError(status: Int, body: String?) {
        Log.e(TAG, "HTTP $status: ${sanitize(body)}")
    }

    fun timeout(message: String?) {
        Log.e(TAG, "timeout: ${sanitize(message)}")
    }
}
