#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"
def replace(file, old, new):
    p=src/file
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1: raise RuntimeError(f"{file}: anchor mismatch: {s.count(old)}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")
replace("CaptureService.kt","val preparedBody = Slang.preserveProfanityContext(rawBody)","val preparedBody = Slang.prepareForTranslation(rawBody, context)")
replace("CaptureService.kt","val restored = Slang.restoreProfanityContext(rawBody, output)","val restored = Slang.restoreDialogueTone(rawBody, Slang.restoreProfanityContext(rawBody, output))")
replace("CaptureService.kt","val finalBody = Slang.restoreProfanityContext(rawBody, result.text)","val finalBody = Slang.restoreDialogueTone(rawBody, Slang.restoreProfanityContext(rawBody, result.text))")
replace("CaptureService.kt","withTimeoutOrNull(1800L) { DeepLCeviri.translate(text, key, previous) }","withTimeoutOrNull(1400L) { DeepLCeviri.translate(text, key, previous) }")
replace("CaptureService.kt","val sutun = 32\n        val satir = 12","val sutun = 48\n        val satir = 24")
replace("CaptureService.kt","if (enBuyukFark < 7)","if (enBuyukFark < 6)")
replace("SubtitleReadGuard299.kt",'"no", "yes", "no way", "thank you", "thanks", "i know",','"no", "yes", "no way", "thank you", "thanks", "i know",\n                "yo", "hey", "huh", "what", "really", "okay", "alright",')
slang=r'''
    // Street-scene context narrows "tryna smoke" to weed, but never "smoke him".
    private val smokeOffer = Regex("""(?i)\b(?:tryna|wanna)\s+smoke\b(?!\s+(?:him|her|them|you|me|us)\b)""")
    private val streetCue = Regex("""(?i)\b(?:y['’]?all|yall|yo|bro|bruh|crackheads?|dealer|plug|weed|dope)\b|\bthe\s+block\b""")
    private val nicotineCue = Regex("""(?i)\b(?:cigarette|cigar|tobacco|nicotine|sigara)\b""")

    fun prepareForTranslation(text: String, context: String): String {
        val cleaned = preserveProfanityContext(text)
        if (!smokeOffer.containsMatchIn(cleaned)) return cleaned
        if (nicotineCue.containsMatchIn(cleaned)) return cleaned
        if (!streetCue.containsMatchIn("$cleaned $context")) return cleaned
        return smokeOffer.replace(cleaned, "want to smoke weed")
    }

    // Fix source-supported slang errors without dropping any other clauses.
    fun restoreDialogueTone(source: String, translated: String): String {
        var out = translated.trim()
        if (Regex("""(?i)\bcrackheads?\b""").containsMatchIn(source)) {
            out = Regex("""(?iu)\buyuşturucu\s+bağımlıları\b""").replace(out, "keşler")
        }
        if (smokeOffer.containsMatchIn(source) &&
            streetCue.containsMatchIn(source) &&
            !nicotineCue.containsMatchIn(source)) {
            out = Regex("""(?iu)\bsigara\s+mı\s+içmeye\s+çalışıyorsunuz\?""")
                .replace(out, "Ot içmek ister misiniz?")
            out = Regex("""(?iu)\bsigara\s+mı\s+içmek\s+istiyorsunuz\?""")
                .replace(out, "Ot içmek ister misiniz?")
        }
        return out
    }

'''
replace("Slang.kt","    private val hints: List<Pair<Regex, String>> = listOf(",slang+"    private val hints: List<Pair<Regex, String>> = listOf(")
test=root/"app/src/test/java/com/example/altyazi/GameLingo202LiveFixTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
test.write_text(r'''package com.example.altyazi

import org.junit.Assert.*
import org.junit.Test

class GameLingo202LiveFixTest {
    @Test fun streetSmokeUsesDrugContext() {
        val en = "Yo, what's up? Y'all tryna smoke?"
        val prepared = Slang.prepareForTranslation(en, "Yo! Crackheads! Woo!")
        assertTrue(prepared.contains("want to smoke weed"))
        assertFalse(prepared.contains("cigarette"))
    }

    @Test fun explicitlyNicotineSmokingIsLeftUnchanged() {
        val en = "Wanna smoke a cigarette?"
        assertEquals(en, Slang.prepareForTranslation(en, "the block"))
    }

    @Test fun smokingSomeoneIsNotRewrittenAsWeed() {
        val en = "We tryna smoke him."
        assertEquals(en, Slang.prepareForTranslation(en, "the block"))
    }

    @Test fun crackheadsKeepsStreetRegisterWithoutDiscardingRest() {
        val result = Slang.restoreDialogueTone(
            "Yo! Crackheads! Woo!", "Hey! Uyuşturucu bağımlıları! Vay!")
        assertEquals("Hey! keşler! Vay!", result)
    }

    @Test fun reportedCigaretteErrorCanBeRepairedWithoutLosingFirstSentence() {
        val result = Slang.restoreDialogueTone(
            "Yo, what's up? Y'all tryna smoke?",
            "Hey, n'aber? Sigara mı içmeye çalışıyorsunuz?")
        assertEquals("Hey, n'aber? Ot içmek ister misiniz?", result)
    }

    @Test fun profanityInLongDialogueDoesNotDiscardClause() {
        val result = Slang.restoreProfanityContext(
            "Fuck you, I'm not leaving without my brother.",
            "Siktir, kardeşimi almadan gitmeyeceğim.")
        assertTrue(result.contains("kardeşimi almadan"))
    }

    @Test fun shortSpokenLinesRemainEligible() {
        assertTrue(SubtitleReadGuard299.shortSpokenLine("Yo"))
        assertTrue(SubtitleReadGuard299.shortSpokenLine("What"))
        assertFalse(SubtitleReadGuard299.shortSpokenLine("SKIP"))
    }

    @Test fun meaningfulExtensionIsNotDiscarded() {
        assertTrue(SubtitleReadGuard299.meaningfulRevision(
            "You two get to safety", "You two get to safety. You can't win this fight."))
    }
}
''', encoding="utf-8")
print("Targeted fixes applied")
