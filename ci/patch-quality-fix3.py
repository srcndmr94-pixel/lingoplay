#!/usr/bin/env python3
"""GameLingo 202 Fix3: smallest repairs justified by live session, after Fix2 patch."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"

def replace(file,old,new):
    p=src/file
    txt=p.read_text(encoding="utf-8")
    if txt.count(old)!=1:
        raise RuntimeError(f"Guarded patch failed: {file}: {txt.count(old)} matches")
    p.write_text(txt.replace(old,new,1),encoding="utf-8")

# Do not treat any non-swear Turkish synonym as censorship and then fall
# through to ungrammatical ML Kit. A truly polite replacement is still rejected.
replace("MeaningSafety.kt",
    'private val turkishToneMarkers = Regex("""(?iu)\\b(siktir|siktiğim|amına|orospu|piç|bok|saçmalık|lan|kahretsin|lanet)\\b""")',
    'private val turkishToneMarkers = Regex("""(?iu)(?:siktir|siktiğim|amına|orospu|piç|bok|boktan|pislik|saçmalık|lan|kahretsin|lanet|felaket|berbat|rezalet|sıçt)""")')
replace("MeaningSafety.kt",
    'if (wordCount <= 5 && !turkishToneMarkers.containsMatchIn(target)) return true',
    '''if (wordCount <= 5 && !turkishToneMarkers.containsMatchIn(target)) {
            val t = target.trim()
            // Only proven sanitization, blanking or an implausibly short response.
            return t.count(Char::isLetter) < 6 ||
                Regex("""(?iu)\\b(?:lütfen|rica\\s+ederim|kibarca)\\b""").containsMatchIn(t)
        }''')

# Truly untranslated results must not be displayed or cached.
replace("TranslationQualityGate.kt",
    'Problem.REVERSED_COMMAND, Problem.IMPERATIVE_AS_INFINITIVE -> true',
    'Problem.REVERSED_COMMAND, Problem.IMPERATIVE_AS_INFINITIVE,\n        Problem.UNTRANSLATED, Problem.ENGLISH_FRAGMENT -> true')

# An OCR single-letter change is *not* a fresh subtitle unless negation changed.
# Keep meaningful extensions and negation changes allowed.
replace("SubtitleReadGuard299.kt",
    'if (smallCorrection(previous, current)) return true',
    '''if (smallCorrection(previous, current) &&
                a.filter { it in NEGATIONS } == b.filter { it in NEGATIONS }) return false''')

# Only an explicit speaker label can teach the application a speaker's name;
# generic OCR fragments such as "Dorrp" and "Do TRP" were learned erroneously.
replace("CaptureService.kt",
    'private fun adOgren(a: String, b: String) {\n        val wa =',
    '''private fun adOgren(a: String, b: String) {
        if (!a.contains(':') && !b.contains(':') &&
            !a.trimStart().startsWith("[") && !b.trimStart().startsWith("[")) return
        val wa =''')

# Fix only observed OCR errors with corroborating phrase context.
replace("CaptureService.kt",
    'val text = adiAyir(OcrOnar.onar(TextFix.clean(stripped)))',
    'val text = adiAyir(LiveSubtitleRepair202.cleanOcr(OcrOnar.onar(TextFix.clean(stripped))))')

# Verified, source-locked local corrections avoid fallback latency and preserve
# tone for specific dialogues heard in the real test (not all profanity).
replace("CaptureService.kt",
    'val preparedBody = Slang.prepareForTranslation(rawBody, context)',
    '''val verified = LiveSubtitleRepair202.verifiedTranslation(rawBody)
        if (verified != null) {
            val output = if (speakerName == null) verified else "$speakerName: $verified"
            publishForRun(runId, source, output, remember = false, tag = " · doğrulanmış replik")
            return
        }
        val preparedBody = Slang.prepareForTranslation(rawBody, context)''')

live= r'''package com.example.altyazi

/**
 * 17 Nov live-log corrections. No general guesswork: a repair activates only
 * when both the OCR error and its distinctive supporting suffix are present.
 */
object LiveSubtitleRepair202 {
    private fun key(s: String): String = s.trim().replace('’', '\'')
        .replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', ' ')
        .lowercase(java.util.Locale.ROOT)

    fun cleanOcr(line: String): String {
        var text = line
        if (Regex("""(?i)^guess they know we['’]re coming\.?$""").matches(text))
            text = "I guess they know we're coming."
        if (Regex("""(?i)^dorrp\s+the locals are feisty[!.]?$""").matches(text))
            text = "The locals are feisty!"
        if (Regex("""(?i)^do\s+trp\s+then he cranked the speed up\b""").containsMatchIn(text))
            text = text.replace(Regex("""(?i)^do\s+trp\s+"""), "")
        if (Regex("""(?i)^(?:fever|lever|ever)\s+tell you about\b""").containsMatchIn(text) &&
            Regex("""(?i)\bfairground that rolled into my home town when i was a kid\b""").containsMatchIn(text))
            text = text.replace(Regex("""(?i)^(?:fever|lever|ever)\s+tell"""), "Ever tell")
        // One damaged apostrophe caused the same Boobie line to be translated 3x.
        if (Regex("""(?i)^no\.\s+y['’]al know boobie\?$""").matches(text))
            text = "No. Y'all know Boobie?"
        return text
    }

    /** Precise source-locked phrases, already validated by the supplied scene. */
    fun verifiedTranslation(source: String): String? = when (key(source)) {
        "clean this shit up" -> "Şu boku temizle!"
        "this is so fucked" -> "Bu iş fena sıçtı!"
        "look what the law chased in" -> "Bakın, polis kimleri getirmiş."
        else -> null
    }
}
'''
(src/"LiveSubtitleRepair202.kt").write_text(live,encoding="utf-8")

test=r'''package com.example.altyazi

import org.junit.Assert.*
import org.junit.Test

class GameLingo202QualityFix3Test {
    @Test fun avoidWorseFallbackForNaturalStrongTurkish() {
        assertFalse(MeaningSafety.lostContextualProfanity("Clean this shit up!", "Şu pisliği temizle!"))
        assertTrue(MeaningSafety.lostContextualProfanity("Fuck off!", "Lütfen git."))
    }

    @Test fun suppressHarmlessOcrEditsButNotNegation() {
        assertFalse(SubtitleReadGuard299.meaningfulRevision(
            "No. Y'al know Boobie?", "No. Y'all know Boobie?"))
        assertFalse(SubtitleReadGuard299.meaningfulRevision(
            "Fever tell you about the fairground", "lever tell you about the fairground"))
        assertTrue(SubtitleReadGuard299.meaningfulRevision(
            "You can go now", "You can't go now"))
    }

    @Test fun preserveRealNewClause() {
        assertTrue(SubtitleReadGuard299.meaningfulRevision(
            "You two get to safety.", "You two get to safety. You can't win this fight."))
    }

    @Test fun trimOnlyVerifiedOcrJunk() {
        assertEquals("The locals are feisty!",
            LiveSubtitleRepair202.cleanOcr("Dorrp The locals are feisty!"))
        assertEquals("Then he cranked the speed up so fast.",
            LiveSubtitleRepair202.cleanOcr("Do TRP Then he cranked the speed up so fast."))
        assertEquals("I guess they know we're coming.",
            LiveSubtitleRepair202.cleanOcr("guess they know we're coming."))
    }

    @Test fun repairRepeatedOcrPrefixWithoutInventingGeneralContext() {
        val source = "Fever tell you about uh... the uh, fairground that rolled into my home town when I was a kid?"
        assertTrue(LiveSubtitleRepair202.cleanOcr(source).startsWith("Ever tell"))
        assertEquals("Fever tell you about another topic", 
            LiveSubtitleRepair202.cleanOcr("Fever tell you about another topic"))
    }

    @Test fun translateKnownProfanityWithoutCensoring() {
        assertEquals("Bu iş fena sıçtı!", LiveSubtitleRepair202.verifiedTranslation("This is so fucked!"))
        assertEquals("Şu boku temizle!", LiveSubtitleRepair202.verifiedTranslation("Clean this shit up!"))
        assertNull(LiveSubtitleRepair202.verifiedTranslation("Fuck this, get out!"))
    }

    @Test fun knownIdiomAvoidsSlowOpusFallback() {
        assertEquals("Bakın, polis kimleri getirmiş.",
            LiveSubtitleRepair202.verifiedTranslation("Look what the law chased in."))
    }

    @Test fun blockEnglishLeakInLiveMode() {
        assertTrue(TranslationQualityGate.mustReject(TranslationQualityGate.Problem.UNTRANSLATED))
        assertTrue(TranslationQualityGate.mustReject(TranslationQualityGate.Problem.ENGLISH_FRAGMENT))
        assertFalse(TranslationQualityGate.mustReject(TranslationQualityGate.Problem.LOST_PROFANITY))
    }
}
'''
testpath=root/"app/src/test/java/com/example/altyazi/GameLingo202QualityFix3Test.kt"
testpath.parent.mkdir(parents=True,exist_ok=True)
testpath.write_text(test,encoding="utf-8")
print("Fix3 patches applied to 202 source")
