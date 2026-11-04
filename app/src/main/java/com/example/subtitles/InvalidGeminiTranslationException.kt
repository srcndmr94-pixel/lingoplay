package com.example.subtitles

class InvalidGeminiTranslationException(
    val reason: GeminiTranslationValidator.Reason
) : Exception("Invalid Gemini translation: $reason")
