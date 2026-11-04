package com.example.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTranslationValidatorTest {
    @Test fun normalTurkishTranslationIsValid() = assertValid("Wait for me here.", "Beni burada bekle.")
    @Test fun emptyResponseIsInvalid() = assertInvalid("Stay here.", "", GeminiTranslationValidator.Reason.EMPTY_RESPONSE)
    @Test fun sameAsSourceIsInvalid() = assertInvalid("Take cover!", " TAKE   COVER! ", GeminiTranslationValidator.Reason.SAME_AS_SOURCE)
    @Test fun modelExplanationIsInvalid() = assertInvalid("Come with me.", "Türkçe çeviri: Benimle gel.", GeminiTranslationValidator.Reason.MODEL_EXPLANATION)
    @Test fun profanityIsAllowed() = assertValid("What the fuck are you doing?", "Ne bok yiyorsun?")
    @Test fun shortGameLineIsAllowed() = assertValid("Run!", "Koş!")
    @Test fun properNamesAreAllowed() = assertValid("Batman is in Gotham.", "Batman Gotham'da.")

    private fun assertValid(source: String, translation: String) {
        assertTrue(GeminiTranslationValidator.validate(source, translation) is GeminiTranslationValidator.Result.Valid)
    }

    private fun assertInvalid(source: String, translation: String, reason: GeminiTranslationValidator.Reason) {
        val result = GeminiTranslationValidator.validate(source, translation)
        assertTrue(result is GeminiTranslationValidator.Result.Invalid)
        assertEquals(reason, (result as GeminiTranslationValidator.Result.Invalid).reason)
    }
}
