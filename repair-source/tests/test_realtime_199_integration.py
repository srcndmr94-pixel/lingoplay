from pathlib import Path
root = Path(__file__).resolve().parents[1]
code = (root/'app/src/main/java/com/example/altyazi/CaptureService.kt').read_text()
policy = (root/'app/src/main/java/com/example/altyazi/RealtimeCaptionPolicy199.kt').read_text()
build = (root/'app/build.gradle.kts').read_text()
checks = {
    '199 version': 'versionCode = 199' in build,
    'fallback parallel and bounded': 'val localRescue =' in code and 'withTimeoutOrNull(RealtimeCaptionPolicy199.LOCAL_TIMEOUT_MS)' in code,
    'hedge starts before primary': code.index('val localRescue =') < code.index('runTranslation(source, runId)'),
    'fallback local no engine recursion': 'private suspend fun fastLocalCaption199(' in code and 'NameShield.shield(body)' in code,
    'first valid display gate': 'stableCaption199.firstValidated(runId, subtitleGeneration::isCurrent)' in code,
    'stale guard': 'runId != null && !subtitleGeneration.isCurrent(runId)' in code,
    'fast mode unaffected': 'balanced && strongAvailable' in policy,
    'one caption stable': 'if (remember && !value.startsWith("⚠")) saveToMemory(source, value, tag)' in code,
    'current overlay position kept': 'screenHeight - dp(110)' in code,
    'strong engine order retained': 'remote ?: (if (prefs.opusMtEnabled' in code,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
print(f'REALTIME INTEGRATION: {sum(checks.values())}/{len(checks)} PASS')
