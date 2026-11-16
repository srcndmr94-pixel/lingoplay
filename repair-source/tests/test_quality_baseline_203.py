"""203 regression lock. 199 baseline remains immutable; modified files pinned separately."""
from pathlib import Path
import json, hashlib
root = Path(__file__).resolve().parents[1]
old = json.loads((root/'tests/quality_baseline_199_sha256.json').read_text())
new = json.loads((root/'tests/quality_baseline_203_sha256.json').read_text())
allowed = {
    'app/src/main/java/com/example/altyazi/CaptureService.kt',
    'app/src/main/java/com/example/altyazi/TranslationQualityGate.kt',
    'app/src/main/java/com/example/altyazi/RealtimeCaptionPolicy199.kt',
}
assert set(new)==set(old), 'baseline file set unexpectedly changed'
assert {p for p in old if new[p]!=old[p]}==allowed, 'unapproved 199 core changes'
for path, expected in new.items():
    actual=hashlib.sha256((root/path).read_bytes()).hexdigest()
    assert actual==expected, f'Core modification without review: {path}'
assert 'versionCode = 203' in (root/'app/build.gradle.kts').read_text()
assert 'versionName = "10.42.7-quality-rc9"' in (root/'app/build.gradle.kts').read_text()
assert 'applicationIdSuffix = ".benchmark"' in (root/'app/build.gradle.kts').read_text()
assert not list(root.rglob('*.keystore')), 'signing key should not be in public source'
assert not list(root.rglob('*.jks')), 'signing key should not be in public source'
print('PASS: 8/8 pinned core files; 3 reviewed 203 patches, 5 files remain frozen')
