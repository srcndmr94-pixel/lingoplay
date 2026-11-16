package com.example.altyazi

fun main() {
    check(!DeepLPolicy.canRequest("Hello!", ""))
    check(!DeepLPolicy.canRequest("", "test:fx"))
    check(!DeepLPolicy.canRequest("a".repeat(1501), "test:fx"))
    check(!DeepLPolicy.canRequest("Hi", "bad\nkey"))
    check(DeepLPolicy.canRequest("Hello!", "test:fx"))
    check(DeepLPolicy.endpoint(" test:fx ") == "https://api-free.deepl.com/v2/translate")
    check(DeepLPolicy.endpoint("test-key") == "https://api.deepl.com/v2/translate")
    println("DeepL policy: 7/7 passed")
}
