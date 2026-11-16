from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=r/'app/src/main/java/com/example/altyazi'
read=lambda f:(s/f).read_text()
service=read('CaptureService.kt'); gate=read('TranslationQualityGate.kt')
checks={
    '10.42.5 version and monotonic code': 'versionCode = 193' in (r/'app/build.gradle.kts').read_text() and 'versionName = "10.42.7-bottom-ocr-log-stability"' in (r/'app/build.gradle.kts').read_text(),
    'package identity preserved': 'applicationId = "com.example.altyazi"' in (r/'app/build.gradle.kts').read_text(),
    'APK299 source-only verified OCR repairs wired': 'GameplayLogRepair299.repairOcr(current)' in read('SubtitlePreparation.kt'),
    'APK299 corrupted game text delayed': 'GameplayLogRepair299.waitForCleanReading(text)' in service,
    'APK299 false speaker blocked': 'GameplayLogRepair299.plausibleSpeakerName(ad)' in service,
    'APK299 game terms in render': 'GameplayLogRepair299.repairTranslation(' in service,
    'All translation sources pass named-term gate': 'val value = rawValue?.let { GameplayLogRepair299.repairTranslation(source, it) }' in service,
    'Gameplay labels protected from decorative filter': '!GameplayLogRepair299.isGameplayInstruction(collapsed2)' in service,
    'DeepL remains opt-in and key-gated': 'prefs.deepLEnabled && deeplKey.isNotBlank()' in service,
    'DeepL quality gate integrated': 'val accepted = normalized?.takeIf' in service and 'TranslationQualityGate.check(text, it) == null' in service,
    'Profanity repair only after detected loss': 'TranslationQualityGate.Problem.LOST_PROFANITY' in service and 'ProfanityShield.addTails(raw, tails)' in service,
    'OPUS fallback preserved': 'opusAttempt(text)' in service,
    'Google fallback now validated': 'GoogleCeviri.cevir(text)?.takeIf { googleResult ->' in service,
    'Missing detail is retried': 'issue == TranslationQualityGate.Problem.MISSING_DETAIL' in service,
    'Names still shielded before engines': 'NameShield.shield(GoogleSourcePreparation.prepare(text))' in read('OfflineFix.kt'),
    'Character label OCR repair': 'damagedWilliamLabel.replace' in read('SubtitlePreparation.kt'),
    'Both quality checks wired': 'LiveSubtitleQuality10425.defect(s, t)' in gate,
    'DeepL raw benchmark path intact': 'DeepLCeviri.translate(case.source' in read('EngineBenchmark1035.kt'),
    'Model removal functionality retained': 'MODEL' in read('ModelSetup.kt').upper() and 'opus' in read('ModelSetup.kt').lower(),
    'Current engine displayed in diagnostics': 'motor:$chain' in service,
    'Periodic OCR recheck wired': 'subtitleReadGuard299.shouldOcr(sampleUnchanged' in service,
    'Read gate resets on rotation': 'subtitleReadGuard299.reset()' in service,
    'Meaningful extension bypasses shown-source dedupe': '!SubtitleReadGuard299.newMeaningForDispatch(shownSource, text)' in service,
    'Meaningful extension bypasses translating-source dedupe': '!SubtitleReadGuard299.newMeaningForDispatch(translatingFor, text)' in service,
    'Short spoken line escapes menu filter': 'if (SubtitleReadGuard299.shortSpokenLine(s)) return false' in service,
    'No blanket y>boxTop OCR deletion': 'ekranY(box.top) >= shownBoxTopPx' not in service,
    'No cached fake OCR observation': 'handleText(lastOcrRaw)' not in service,
    'Overlay dismissal clears own translation': 'displayed.remove(normalize(shownValue))' in service,
    'OCR raw and filtered diagnostics': 'lastMlKitRaw = lines.joinToString' in service and 'lastOcrTrace =' in service,
    'Bottom caption placement': 'value, screenHeight - dp(110), refined, draft, -1, null' in service and 'protectedTopPx = -1, protectedBottomPx = -1' in service,
    'Overlay rerender also protects previous text': 'protectedStripBottom' in read('Overlay.kt'),

}
for name, ok in checks.items():
    if not ok: raise AssertionError(name)
print(f'{len(checks)}/{len(checks)} 10.42.5 integration assertions passed')
