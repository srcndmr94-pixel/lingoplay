#!/usr/bin/env python3
"""Fix7: evaluate optional Google variant for overly formal DeepL dialogue.
No hardcoded complete translations and no unconditional engine removal.
"""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"
tests=root/"app/src/test/java/com/example/altyazi"

def replace(file,old,new):
    p=src/file
    data=p.read_text(encoding="utf-8")
    n=data.count(old)
    if n!=1:
        raise RuntimeError(f"Fix7 source mismatch {file}: {n} matches")
    p.write_text(data.replace(old,new,1),encoding="utf-8")

replace("CaptureService.kt",
    '        if (result.engine == "DeepL") deepLAccepted.incrementAndGet()',
    '''        var selectedResult = result
        // Only request a second translation where the first sounds overly formal
        // for informal English. Google is an alternative, not a blind replacement.
        if (result.engine == "DeepL" && prefs.googleCeviri &&
            GoogleCeviri.kullanilabilir &&
            NaturalLiveQuality202.shouldCompare(rawBody, result.text)) {
            val alternative = withTimeoutOrNull(850L) {
                GoogleCeviri.cevir(preparedBody, kullanHafiza = false)
            }
            if (!alternative.isNullOrBlank() &&
                NaturalLiveQuality202.preferAlternative(rawBody, result.text, alternative)) {
                selectedResult = SimpleLiveTranslation.Result("Google", alternative.trim())
                if (prefs.diagnostics) log("DOĞAL DİYALOG: Google alternatifi seçildi (üslup)")
            } else if (prefs.diagnostics) {
                log("DOĞAL DİYALOG: DeepL korundu (Google daha iyi değil/yetişmedi)")
            }
        }
        if (result.engine == "DeepL") deepLAccepted.incrementAndGet()''')

replace("CaptureService.kt",
    'val finalBody = result.text.trim()',
    'val finalBody = selectedResult.text.trim()')
# Replace the result ENGINE label without requiring Python or JS interpolation.
replace("CaptureService.kt",
    '" · $'+'{result.engine}"',
    '" · $'+'{selectedResult.engine}"')

quality=r'''package com.example.altyazi

/**
 * A conservative ranking gate, not a sentence dictionary.
 *
 * The DeepL primary preserves previous English dialogue in its API context.
 * Google is sampled only for informal lines whose DeepL output is formal or
 * clearly softens strong language. Prefer the candidate ONLY if structural
 * meaning checks and colloquial-register evidence favor it.
 *
 * There is no general way to prove full naturalness using regexes; keep the
 * primary when evidence is weak and log when alternatives are considered.
 */
object NaturalLiveQuality202 {
    private val sourceWords = Regex("""\p{L}+""")
    private val informalSource = Regex(
        """(?i)\b(?:yo|bruh|bro|dude|ain['’]?t|gonna|wanna|tryna|y['’]?all|what['’]?s\s+up|fuck(?:ed|ing)?|shit|crackheads?|damn|hell|ya\s+know)\b"""
    )
    private val strongSource = Regex(
        """(?i)\b(?:fuck(?:ed|ing)?|shit|motherfucker|asshole|bitch)\b"""
    )
    private val strongTurkish = Regex(
        """(?iu)(?:siktir|sik|bok|lan\b|ulan\b|göt|amına|piç|yavşak|rezalet|berbat|felaket|kahretsin)"""
    )
    private val formalTurkish = Regex(
        """(?iu)(?:uyuşturucu\s+bağımlıları|bilirsiniz|sizler|efendim|müsaade|rica\s+ederim|lütfen|çalışıyorsunuz|yapabilirsiniz\s+mi|olabilir\s+misiniz)"""
    )
    private val colloquialTurkish = Regex(
        """(?iu)(?:keşler?|n['’]?aber|kanka|abi\b|lan\b|ulan\b|siktir|bok|vay\s+be|bilirsin\b|hadi\b|be\b|ya\b)"""
    )
    private val addictionMeaning = Regex("""(?iu)(?:keş|bağıml|uyuşturucu|kokain)""")
    private val dangerousProblems = setOf(
        TranslationQualityGate.Problem.LOST_NEGATION,
        TranslationQualityGate.Problem.LOST_PROHIBITION,
        TranslationQualityGate.Problem.REVERSED_COMMAND,
        TranslationQualityGate.Problem.IMPERATIVE_AS_INFINITIVE,
        TranslationQualityGate.Problem.MISSING_DETAIL,
        TranslationQualityGate.Problem.MISSING_QUESTION,
        TranslationQualityGate.Problem.ENGLISH_FRAGMENT,
        TranslationQualityGate.Problem.UNTRANSLATED
    )

    fun isUsable(source: String, translated: String): Boolean {
        val s=source.trim()
        val t=translated.trim()
        if (t.isBlank() || s.equals(t, ignoreCase=true)) return false
        if (TranslationQualityGate.mustReject(TranslationQualityGate.check(s,t))) return false
        if (LiveResultCompleteness202.isTruncated(s,t)) return false
        return true
    }

    fun shouldCompare(source: String, deepL: String): Boolean {
        if (!isUsable(source, deepL)) return false
        if (!informalSource.containsMatchIn(source)) return false
        return formalTurkish.containsMatchIn(deepL) ||
            (strongSource.containsMatchIn(source) && !strongTurkish.containsMatchIn(deepL))
    }

    private fun registerScore(source: String, translated: String): Int {
        var score=0
        if (formalTurkish.containsMatchIn(translated)) score -= 3
        if (colloquialTurkish.containsMatchIn(translated)) score += 3
        if (strongSource.containsMatchIn(source) && strongTurkish.containsMatchIn(translated)) score += 2
        return score
    }

    fun preferAlternative(source: String, deepL: String, alternative: String): Boolean {
        if (!shouldCompare(source, deepL)) return false
        if (!isUsable(source, alternative)) return false
        // Warnings are useful here but never force an automatic rewrite.
        val newProblem = TranslationQualityGate.check(source, alternative)
        if (newProblem in dangerousProblems) return false
        // The literal "crackhead" refers to addiction, not being eccentric;
        // this is a lexical meaning constraint, not a memorized Turkish line.
        if (Regex("""(?i)\bcrackheads?\b""").containsMatchIn(source) &&
            !addictionMeaning.containsMatchIn(alternative)) return false
        val words = sourceWords.findAll(source).count()
        if (words >= 7 && alternative.length * 100 < deepL.length * 65) return false
        // Do not drop two or more exclamations or distinct questions.
        if (source.count { it == '!' } >= 2 &&
            alternative.count { it == '!' } + 1 < deepL.count { it == '!' }) return false
        if (source.count { it == '?' } >= 1 &&
            deepL.contains('?') && !alternative.contains('?')) return false
        // A visibly more conversational alternative is required. No forced edits.
        return registerScore(source, alternative) >= registerScore(source, deepL) + 2
    }
}
'''
(src/"NaturalLiveQuality202.kt").write_text(quality,encoding="utf-8")

test=r'''package com.example.altyazi
import org.junit.Assert.*
import org.junit.Test

class GameLingo202NaturalQualityFix7Test {
    @Test fun compareInformalSpeechWithStiffTurkish() {
        assertTrue(NaturalLiveQuality202.shouldCompare(
            "Yo! Crackheads! Woo!", "Hey! Uyuşturucu bağımlıları! Vay!"))
    }

    @Test fun preferNaturalColloquialCandidateOnlyWhenMeaningPreserved() {
        assertTrue(NaturalLiveQuality202.preferAlternative(
            "Yo! Crackheads! Woo!",
            "Hey! Uyuşturucu bağımlıları! Vay!",
            "Hey! Keşler! Vay be!"))
    }

    @Test fun doNotSelectFluentButInaccurateWordChoice() {
        assertFalse(NaturalLiveQuality202.preferAlternative(
            "Yo! Crackheads! Woo!",
            "Hey! Uyuşturucu bağımlıları! Vay!",
            "Hey! Çatlaklar! Vay be!"))
    }

    @Test fun doNotWasteGoogleRequestsOnGoodOrdinaryTranslations() {
        assertFalse(NaturalLiveQuality202.shouldCompare(
            "I guess they know we're coming.",
            "Sanırım geldiğimizi biliyorlar."))
        assertFalse(NaturalLiveQuality202.shouldCompare(
            "Yo, what the fuck?", "Hey, ne oluyor lan?"))
    }

    @Test fun compareAwkwardHesitantSpeechWithoutInventingItsReplacement() {
        assertTrue(NaturalLiveQuality202.shouldCompare(
            "Be cool, don't... you know.",
            "Sakin olun, sakın... bilirsiniz işte."))
        assertFalse(NaturalLiveQuality202.preferAlternative(
            "Be cool, don't... you know.",
            "Sakin olun, sakın... bilirsiniz işte.",
            "Sorry."))
    }

    @Test fun doNotDiscardRealShortResponses() {
        assertTrue(NaturalLiveQuality202.isUsable("What?", "Ne?"))
        assertTrue(NaturalLiveQuality202.isUsable("Appreciate it.", "Sağ ol."))
        assertFalse(NaturalLiveQuality202.isUsable("What?", "What?"))
    }

    @Test fun noMandatoryReplacementWhenGoogleCandidateIsWeak() {
        assertFalse(NaturalLiveQuality202.preferAlternative(
            "Yo! Crackheads! Woo!",
            "Hey! Uyuşturucu bağımlıları! Vay!",
            "Hey! Bağımlılar! Vay!"))
    }
}
'''
tests.mkdir(parents=True,exist_ok=True)
(tests/"GameLingo202NaturalQualityFix7Test.kt").write_text(test,encoding="utf-8")
print("Fix7: optional Google alternative for overly formal informal dialogue; no canned output.")
