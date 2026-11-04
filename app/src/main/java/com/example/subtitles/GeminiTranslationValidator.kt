package com.example.subtitles

object GeminiTranslationValidator {
    sealed class Result {
        data class Valid(val text: String) : Result()
        data class Invalid(val reason: Reason, val rawText: String) : Result()
    }
    enum class Reason { EMPTY_RESPONSE, SAME_AS_SOURCE, MODEL_EXPLANATION, CONTEXT_LEAK, SUSPICIOUSLY_LONG, NO_TURKISH_OUTPUT }

    fun validate(sourceEnglish: String, translatedTurkish: String, previousContext: List<String> = emptyList()): Result {
        val source = normalize(sourceEnglish)
        val translated = normalize(translatedTurkish)
        if (translated.isBlank()) return Result.Invalid(Reason.EMPTY_RESPONSE, translatedTurkish)
        if (source.length >= 4 && source.equals(translated, ignoreCase = true)) return Result.Invalid(Reason.SAME_AS_SOURCE, translatedTurkish)
        if (looksLikeModelExplanation(translated)) return Result.Invalid(Reason.MODEL_EXPLANATION, translatedTurkish)
        if (containsPreviousContext(translated, previousContext)) return Result.Invalid(Reason.CONTEXT_LEAK, translatedTurkish)
        if (isSuspiciouslyLong(source, translated)) return Result.Invalid(Reason.SUSPICIOUSLY_LONG, translatedTurkish)
        if (source.length >= 12 && appearsMostlyEnglish(translated)) return Result.Invalid(Reason.NO_TURKISH_OUTPUT, translatedTurkish)
        return Result.Valid(translatedTurkish.trim())
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()
    private fun looksLikeModelExplanation(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("çeviri:", "türkçe:", "türkçe çeviri:", "translation:", "translated:", "the translation", "here is", "işte çeviri", "alternatif çeviri", "açıklama:").any { lower.startsWith(it) }
    }
    private fun containsPreviousContext(translated: String, previousContext: List<String>): Boolean {
        if (previousContext.isEmpty()) return false
        val output = normalize(translated).lowercase()
        return previousContext.takeLast(3).map(::normalize).filter { it.length >= 15 }.any { output.contains(it.lowercase()) }
    }
    private fun isSuspiciouslyLong(source: String, translated: String): Boolean =
        if (source.length < 5) translated.length > 100 else translated.length > source.length * 4 + 80
    private fun appearsMostlyEnglish(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^a-zA-ZçğıöşüÇĞİÖŞÜ']+")).filter { it.length >= 2 }
        if (words.size < 4) return false
        val common = setOf("the","this","that","these","those","is","are","was","were","have","has","had","do","does","did","you","your","yours","he","she","they","we","and","but","because","what","where","when","why","how","can","could","would","should","will","with","from","into","for","not","don't","can't","it's","i'm","you're","we're","they're")
        val hits = words.count { it in common }
        return hits >= 3 && hits.toDouble() / words.size >= 0.45
    }
}
