package com.example.subtitles

data class SubtitleTranslationRequest(
    val subtitleId: Long,
    val rawOcrText: String,
    val normalizedEnglish: String,
    val previousContext: List<String>
)
