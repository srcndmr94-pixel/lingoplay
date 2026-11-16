package com.example.altyazi

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SubtitleRepair205Test {
    @Test fun echoedEnglishCannotPublish() {
        for (output in listOf("Get out now!", "get out now", "GET OUT NOW...")) {
            assertTrue(TranslationQualityGate.mustReject(TranslationQualityGate.check("Get out now!", output)))
        }
    }
    @Test fun mixedEnglishCannotPublish() {
        assertTrue(TranslationQualityGate.mustReject(
            TranslationQualityGate.check("You're not alone.", "You're yalnız değilsin.")))
        assertFalse(TranslationQualityGate.mustReject(
            TranslationQualityGate.check("You're not alone.", "Yalnız değilsin.")))
    }
    @Test fun negationChangeIsNotADuplicate() {
        assertTrue(SubtitleReadGuard299.newMeaningForDispatch("You can go now.", "You can't go now."))
    }
    @Test fun changedPronounIsNotADuplicate() {
        assertTrue(SubtitleReadGuard299.newMeaningForDispatch("He is here.", "She is here."))
    }
    @Test fun changedLocationIsNotADuplicate() {
        assertTrue(SubtitleReadGuard299.newMeaningForDispatch("Stay here now.", "Stay there now."))
    }
    @Test fun changedNumberIsNotADuplicate() {
        assertTrue(SubtitleReadGuard299.newMeaningForDispatch("Wait 10 minutes.", "Wait 20 minutes."))
    }
    @Test fun punctuationIsStillADuplicate() {
        assertFalse(SubtitleReadGuard299.newMeaningForDispatch("Come here!", "Come here."))
    }
    @Test fun appendedClauseIsNotADuplicate() {
        assertTrue(SubtitleReadGuard299.newMeaningForDispatch("You two get to safety.", "You two get to safety. You hear me?"))
    }
    @Test fun ordinaryOcrWobbleDoesNotRedispatch() {
        assertFalse(SubtitleReadGuard299.newMeaningForDispatch("How... It's gone.", "How... lts gone."))
    }
    @Test fun failedTranslationCanRetryAfterCooldown() {
        val retry = SubtitleRetry205()
        assertTrue(retry.begin("Get out!", 0))
        retry.failed(100)
        assertFalse(retry.begin("Get out.", 899))
        assertTrue(retry.begin("Get out!", 900))
    }
    @Test fun failureDoesNotLoopForever() {
        val retry = SubtitleRetry205()
        assertTrue(retry.begin("Run!", 0))
        retry.failed(1)
        assertTrue(retry.begin("Run!", 801))
        retry.failed(802)
        assertFalse(retry.begin("Run!", 10000))
    }
    @Test fun newSubtitleDoesNotInheritFailureBudget() {
        val retry = SubtitleRetry205()
        retry.begin("Run!", 0)
        retry.failed(1)
        assertTrue(retry.begin("Stop!", 2))
    }
    @Test fun gapAllowsRepeatedUtteranceAgain() {
        val retry = SubtitleRetry205()
        retry.begin("Run!", 0)
        retry.failed(1)
        retry.begin("Run!", 801)
        retry.reset()
        assertTrue(retry.begin("Run!", 802))
    }
    @Test fun shortSpeechSurvivesLengthFilter() {
        assertTrue(SubtitleReadGuard299.shortSpokenLine("No"))
        assertTrue(SubtitleReadGuard299.shortSpokenLine("Go now"))
        assertFalse(SubtitleReadGuard299.shortSpokenLine("R2"))
    }
    @Test fun glyphGarbageIsNotDilutedByLongSentence() {
        assertTrue(OcrQuality.bozukMu("We have to leave this pla|ce right now"))
        assertTrue(OcrQuality.bozukMu("We have to leave this pla\uFFFDce right now"))
    }
    @Test fun cleanDialogueAndDigitsArePreserved() {
        assertFalse(OcrQuality.bozukMu("We have to leave this place right now"))
        assertFalse(OcrQuality.bozukMu("Wait 20 minutes before you go."))
    }
    @Test fun specificIdiomRunsBeforePossessiveShitRule() {
        val shielded = ProfanityShield.shield("Get your shit together!")
        assertTrue(shielded.text.contains("pull yourself together", ignoreCase = true))
        assertFalse(shielded.text.contains("stuff"))
    }
    @Test fun possessionsKeepTheirMeaning() {
        val shielded = ProfanityShield.shield("Take your shit with you.")
        assertTrue(shielded.text.contains("your stuff"))
        assertTrue(shielded.tails.isEmpty())
    }
    @Test fun existingSwearIsNotDoubled() {
        assertEquals("Siktir git!", ProfanityShield.addTails("Siktir git!", listOf("amına koyayım")))
    }
    @Test fun swearingIsNotAppendedToUnrelatedSecondSentence() {
        assertTrue(ProfanityShield.shield("Don't fucking move. The child is safe.").tails.isEmpty())
    }
    @Test fun directInsultIsRestoredInPlace() {
        val shielded = ProfanityShield.shield("You are a bastard.")
        assertEquals(1, shielded.slots.size)
        assertEquals("Sen bir piçsin.", ProfanityShield.restore(
            "Sen bir " + shielded.slots[0].token + "sin.", shielded.slots, Locale.forLanguageTag("tr")))
    }
}
