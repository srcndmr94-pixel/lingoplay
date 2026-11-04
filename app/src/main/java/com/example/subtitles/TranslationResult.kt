package com.example.subtitles

sealed interface TranslationResult {
    val subtitleId: Long
    data class Success(override val subtitleId: Long, val translation: String, val source: TranslationSource) : TranslationResult
    data class Failure(override val subtitleId: Long, val reason: String) : TranslationResult
}

enum class TranslationSource { GEMINI, OFFLINE }
