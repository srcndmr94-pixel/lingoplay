from pathlib import Path
from hashlib import sha256
import json
root=Path(__file__).resolve().parents[1]
prev=json.loads((root/'tests/quality_baseline_203_sha256.json').read_text())
now=json.loads((root/'tests/quality_baseline_204_sha256.json').read_text())
assert set(prev)==set(now)
allowed={'app/src/main/java/com/example/altyazi/CaptureService.kt',
         'app/src/main/java/com/example/altyazi/TranslationQualityGate.kt'}
assert {p for p in prev if prev[p]!=now[p]}==allowed, 'unexpected core modification'
assert all(sha256((root/p).read_bytes()).hexdigest()==digest for p,digest in now.items())
build=(root/'app/build.gradle.kts').read_text()
assert 'versionCode = 204' in build
assert 'versionName = "10.42.7-quality-rc10"' in build
assert 'applicationIdSuffix = ".benchmark"' in build
assert not list(root.rglob('*.keystore'))
assert not list(root.rglob('*.jks'))
print('PASS: 204 baseline 8/8; only 2 prior core files changed')
