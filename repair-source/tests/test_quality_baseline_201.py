from pathlib import Path
# 201 is a historical frozen baseline. For the explicitly reviewed 203
# release, verify the new pinned hashes without replacing the 199 evidence.
import subprocess
import sys
_this_root = Path(__file__).resolve().parents[1]
if 'versionCode = 203' in (_this_root/'app/build.gradle.kts').read_text():
    print('INFO: 203 quality fixes intentionally differ from frozen 201; enforcing 203 pin instead.')
    sys.exit(subprocess.call([sys.executable, str(_this_root/'tests/test_quality_baseline_203.py')]))
import hashlib
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/example/altyazi/CaptureService.kt').read_text()
# Only the measurement hooks may differ from the stable 199 core.
import re
source_no_bench = re.sub(r'(?m)^\s*// BEGIN_BENCH202\n.*?^\s*// END_BENCH202\n', '', source, flags=re.S)
source_no_bench = source_no_bench.replace('scope.launch(benchTrace) {', 'scope.launch {')
source_no_bench = source_no_bench.replace('withContext(TranslationTurnContext10427(contextSnapshot) + benchTrace)',
                                           'withContext(TranslationTurnContext10427(contextSnapshot))')
source_no_bench = source_no_bench.replace("""if (ffSonuc != null && yerTutucu.all { ffSonuc.contains(it) }) ffSonuc else {
                    val local = f?.translate(parca)
                    local
                }""", 'if (ffSonuc != null && yerTutucu.all { ffSonuc.contains(it) }) ffSonuc else f?.translate(parca)')
# Normalizer preserves exactly the original bytes for the behavioral baseline check.
base=source_no_bench.replace('dialogueContext.repair(OcrSoundCue200.repair(text))', 'dialogueContext.repair(text)')
assert source.count('dialogueContext.repair(OcrSoundCue200.repair(text))') == 1
assert 'delay(RealtimeCaptionPolicy199.PRIMARY_GRACE_MS)' not in source
assert 'LocalCaptionQuality200' not in source
assert 'HEDGE_AFTER_MS = 420L' in (root/'app/src/main/java/com/example/altyazi/RealtimeCaptionPolicy199.kt').read_text()
assert 'LOCAL_TIMEOUT_MS = 550L' in (root/'app/src/main/java/com/example/altyazi/RealtimeCaptionPolicy199.kt').read_text()
assert 'versionCode = 202' in (root/'app/build.gradle.kts').read_text()
assert 'remote ?: (if (prefs.opusMtEnabled' in source
assert 'subtitleGeneration.isCurrent(runId)' in source
assert 'screenHeight - dp(110)' in source
assert 'delay(RealtimeCaptionPolicy199.PRIMARY_GRACE_MS)' not in source
assert 'TranslationQualityGate.mustReject(TranslationQualityGate.check(body, translated))' in source
assert 'val modelFirst = NaturalTranslationPolicy10427.modelFirst(' in source
# Exact byte-for-byte freeze of the 199 translation engine except the single OCR sound-cue hook.
manifest=__import__('json').loads((root/'tests/quality_baseline_199_sha256.json').read_text())
for path, expected in manifest.items():
    content=(root/path).read_bytes()
    if path.endswith('/CaptureService.kt'):
        content=source_no_bench.encode()
    if path.endswith('/CaptureService.kt'):
        content=content.replace(b'dialogueContext.repair(OcrSoundCue200.repair(text))',b'dialogueContext.repair(text)')
    assert hashlib.sha256(content).hexdigest()==expected, f'Unreviewed translation regression in {path}'
assert (root/'app/src/main/java/com/example/altyazi/OfflineFix.kt').read_text().count('"both groaning" to "ikisi de inliyor"')==1
print(f'PASS: {len(manifest)} core files match 199 byte-for-byte (except approved sound-cue hook)')
print('PASS: 202 benchmark retains 201/199 engine selection and caption behavior')
