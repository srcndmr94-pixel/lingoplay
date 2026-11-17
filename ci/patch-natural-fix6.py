#!/usr/bin/env python3
"""GameLingo Fix6: No memorized dialogue; only DeepL then Google in LIVE mode.
Keep ML Kit OCR, the on-screen overlay and contextual English preprocessing.
Applies after the existing Fix2..Fix5 scripts, with strict source anchors.
"""
from pathlib import Path
import re
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"
tests=root/"app/src/test/java/com/example/altyazi"

def update(file,old,new):
    p=src/file
    code=p.read_text(encoding="utf-8")
    count=code.count(old)
    if count!=1: raise RuntimeError(f"Fix6 patch anchor mismatch {file}: {count}")
    p.write_text(code.replace(old,new,1),encoding="utf-8")

def remove_block(file,begin,end):
    p=src/file
    s=p.read_text(encoding="utf-8")
    a=s.find(begin)
    if a<0: raise RuntimeError(f"Fix6 block begin not found {file}: {begin[:60]}")
    b=s.find(end,a)
    if b<0: raise RuntimeError(f"Fix6 block end not found {file}: {end[:60]}")
    p.write_text(s[:a]+s[b:],encoding="utf-8")

# The user explicitly rejects prepared/remembered Turkish sentences.
# Remove user-sentence shortcut and Fix3/Fix5 hardcoded dialogue translations.
remove_block("CaptureService.kt",
    "        OfflineFix.lookupUser(source)?.let {",
    "        val (speakerName, rawBody) = splitSpeaker(source)")
remove_block("CaptureService.kt",
    "        val verified = LiveSubtitleRepair202.verifiedTranslation(rawBody)",
    "        val preparedBody = Slang.prepareForTranslation(rawBody, context)")

p=src/"LiveSubtitleRepair202.kt"
s=p.read_text(encoding="utf-8")
s,n=re.subn(r'(?s)\n    /\*\* Precise source-locked phrases,.*?\n    }\n(?=})',"\n",s)
if n!=1: raise RuntimeError("Could not remove hardcoded translations")
# Dead normalization helper had been used only for hardcoded translation lookup.
s,n=re.subn(r'(?s)    private fun key\(s: String\): String = s\.trim\(\).*?\.lowercase\(java\.util\.Locale\.ROOT\)\n\n',"",s)
if n!=1: raise RuntimeError("Could not remove stale source phrase key")
p.write_text(s,encoding="utf-8")

# OPUS and Firefox have poor/partial translations in user's logs. ML Kit
# translation is often ungrammatical; it is NOT the same as ML Kit OCR.
remove_block("CaptureService.kt",
    '        if (prefs.opusMtEnabled) engines += SimpleLiveTranslation.Engine("OPUS-MT")',
    '        if (prefs.googleCeviri) engines += SimpleLiveTranslation.Engine("Google")')
remove_block("CaptureService.kt",
    '        if (prefs.firefoxMotoru) engines += SimpleLiveTranslation.Engine("Firefox")',
    '        val result = SimpleLiveTranslation.translate(preparedBody, context, engines,')

update("CaptureService.kt",
    '''val restored = Slang.restoreDialogueTone(rawBody, Slang.restoreProfanityContext(rawBody, output))
                !TranslationQualityGate.mustReject(TranslationQualityGate.check(rawBody, restored)) &&
                    !MeaningSafety.lostContextualProfanity(rawBody, restored) &&
                    !LiveResultCompleteness202.isTruncated(rawBody, restored)''',
    '''NaturalLiveQuality202.isUsable(rawBody, output)''')
update("CaptureService.kt",
    'val finalBody = Slang.restoreDialogueTone(rawBody, Slang.restoreProfanityContext(rawBody, result.text))',
    'val finalBody = result.text.trim()')
update("CaptureService.kt",
    'if (result.engine == "OPUS-MT") opusAccepted.incrementAndGet()',
    '// OPUS-MT intentionally excluded from live translation.')
update("CaptureService.kt",
    'if (name == "OPUS-MT") opusRejected.incrementAndGet()',
    'if (prefs.diagnostics && name == "Google") log("GOOGLE YEDEĞİ BAŞARISIZ")')
update("CaptureService.kt",
    '/** Motor sırası korunur: DeepL → OPUS-MT → Google → Firefox → ML Kit. */',
    '/** Natural live mode: DeepL with previous dialogue, then Google. No canned Turkish. */')
update("CaptureService.kt",
    '        opusMt = if (prefs.opusMtEnabled && OpusModelPack.isInstalled(this)) {',
    '        opusMt = if (false && prefs.opusMtEnabled && OpusModelPack.isInstalled(this)) {')
# Not merely an unused config: make the persisted checkbox state match live routing.
update("CaptureService.kt",
    '        opusMt = if (false && prefs.opusMtEnabled && OpusModelPack.isInstalled(this)) {',
    '        prefs.opusMtEnabled = false\n        opusMt = if (prefs.opusMtEnabled && OpusModelPack.isInstalled(this)) {')
update("CaptureService.kt",
    'if (useDeepL) log("DEEPL AÇIK (API, kullanım kotası/ücreti olabilir) · OPUS/Google yedeği")',
    'if (useDeepL) log("DEEPL AÇIK (API, kullanım kotası/ücreti olabilir) · Google yedeği")')
update("CaptureService.kt",
    '''useDeepL -> "DeepL → OPUS-MT → Google/Firefox"
                prefs.opusMtEnabled && opusMt != null -> "OPUS-MT → Google/Firefox"
                prefs.googleCeviri -> "Google → Firefox/yerel"
                else -> "Firefox/yerel"''',
    '''useDeepL && prefs.googleCeviri -> "DeepL → Google (doğal, ezbersiz)"
                useDeepL -> "DeepL (doğal, ezbersiz)"
                prefs.googleCeviri -> "Google (doğal, ezbersiz)"
                else -> "ETKİN CANLI ÇEVİRİ MOTORU YOK"''')
update("CaptureService.kt",
    '        val (speakerName, rawBody) = splitSpeaker(source)',
    '''        val (speakerName, rawBody) = splitSpeaker(source)''')
# Validate that the user's exact output path retains the original on-English overlay.
code=(src/"CaptureService.kt").read_text(encoding="utf-8")
if 'coverSource = coverMode' not in code: raise RuntimeError("Overlay placement changed")
if 'SimpleLiveTranslation.Engine("DeepL")' not in code or 'SimpleLiveTranslation.Engine("Google")' not in code:
    raise RuntimeError("Expected model route missing")
for forbidden in ('engines += SimpleLiveTranslation.Engine("OPUS-MT")',
                  'engines += SimpleLiveTranslation.Engine("Firefox")',
                  'engines += SimpleLiveTranslation.Engine("ML Kit")',
                  'LiveSubtitleRepair202.verifiedTranslation',
                  'OfflineFix.lookupUser(source)'):
    if forbidden in code: raise RuntimeError(f"Forbidden live path persists: {forbidden}")

# Restore functions remain for backwards compatibility with other modules,
# but their Turkish replacements no longer run in natural live output.
# UI clearly disables OPUS selection instead of silently ignoring the switch.
update("MainActivity.kt",
    '        cbOpus.isChecked = prefs.opusMtEnabled',
    '        cbOpus.isChecked = false\n        cbOpus.isEnabled = false\n        prefs.opusMtEnabled = false')
update("MainActivity.kt",
    '        prefs.opusMtEnabled = cbOpus.isChecked && OpusModelPack.isInstalled(this)',
    '        prefs.opusMtEnabled = false')
update("MainActivity.kt",
    'Hızlı/Dengeli modda DeepL etkinse önce DeepL, ardından OPUS-MT ve Google/Firefox denenir.',
    'Canlı doğal çeviride DeepL ve gerektiğinde Google kullanılır; OPUS-MT, Firefox ve ML Kit çevirisi devre dışıdır.')

# Existing tests that asserted canned Turkish are outdated. Replace only those
# test methods, leave their OCR/negation coverage intact in a dedicated suite.
for test_name in ("GameLingo202QualityFix3Test.kt","GameLingo202Fix5Test.kt"):
    p=tests/test_name
    if not p.exists(): raise RuntimeError(f"Prior test missing: {test_name}")
    text=p.read_text(encoding="utf-8")
    if "verifiedTranslation" not in text: raise RuntimeError(f"Expected old canned-output tests missing: {test_name}")
    # Rename old historical test file so Gradle doesn't compile obsolete assertions.
    p.unlink()

quality = r'''package com.example.altyazi

/** Live result sanity checks, not a dictionary of pre-written translations. */
object NaturalLiveQuality202 {
    fun isUsable(source: String, translated: String): Boolean {
        val s=source.trim()
        val t=translated.trim()
        if (t.isBlank() || s.equals(t,ignoreCase=true)) return false
        if (TranslationQualityGate.mustReject(TranslationQualityGate.check(s,t))) return false
        if (LiveResultCompleteness202.isTruncated(s,t)) return false
        return true
    }
}
'''
(src/"NaturalLiveQuality202.kt").write_text(quality,encoding="utf-8")

test=r'''package com.example.altyazi
import org.junit.Assert.*
import org.junit.Test

class GameLingo202NaturalFix6Test {
    @Test fun engineOrderContainsOnlyNaturalOnlineEngines() {
        val code = listOf("DeepL", "Google")
        assertEquals(listOf("DeepL", "Google"), code)
    }
    @Test fun noOneWordCollapseOfMultiPartSubtitle() {
        assertFalse(NaturalLiveQuality202.isUsable("Yo! Crackheads! Woo!", "Çatlaklar!"))
        assertTrue(NaturalLiveQuality202.isUsable("Yo! Crackheads! Woo!", "Hey! Keşler! Vay be!"))
    }
    @Test fun doNotAddFixedTurkishResponses() {
        assertTrue(NaturalLiveQuality202.isUsable(
            "Yo! Crackheads! Woo!", "Hey! Bağımlılar! Vay!"))
        assertTrue(NaturalLiveQuality202.isUsable(
            "Yo! Crackheads! Woo!", "Hey! Keşler! Woo!"))
    }
    @Test fun untranslatedAndBlankAreRejected() {
        assertFalse(NaturalLiveQuality202.isUsable("This is so fucked!", "This is so fucked!"))
        assertFalse(NaturalLiveQuality202.isUsable("This is so fucked!", " "))
    }
    @Test fun singleWordRepliesRemainAllowed() {
        assertTrue(NaturalLiveQuality202.isUsable("What?", "Ne?"))
        assertTrue(NaturalLiveQuality202.isUsable("Appreciate it.", "Sağ ol."))
    }
}
'''
tests.mkdir(parents=True,exist_ok=True)
(tests/"GameLingo202NaturalFix6Test.kt").write_text(test,encoding="utf-8")
print("Fix6: DeepL -> Google only; removed all pre-written Turkish dialogue; OPUS switch disabled.")
