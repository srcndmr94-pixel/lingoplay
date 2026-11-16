package com.example.altyazi

/** Pure request policy; tested without Android or paid API calls. */
object DeepLPolicy {
    fun endpoint(key: String): String = if (key.trim().endsWith(":fx"))
        "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

    fun canRequest(source: String, key: String): Boolean =
        source.isNotBlank() && source.length <= 1500 && key.isNotBlank() &&
            !key.any { it == '\n' || it == '\r' }
}
