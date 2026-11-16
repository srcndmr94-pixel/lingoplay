"""Statik entegrasyon koruma testi: asıl JVM davranışını Kotlin testleri doğrular."""
from pathlib import Path
b=Path(__file__).resolve().parents[1]
p=b/'app/src/main/java/com/example/altyazi'
cap=(p/'CaptureService.kt').read_text()
fix=(p/'OfflineFix.kt').read_text()
cache=(p/'TranslationStore.kt').read_text()
assert 'val source = dialogueContext.repair(text)' in cap
assert 'dialogueContext.remember(source)' in cap
assert cap.index('if (inFlight.contains(source)') < cap.index('dialogueContext.remember(source)')
assert 'dialogueContext.reset()' in cap
assert 'GoogleSourcePreparation.prepare(text)' in fix
assert 'val norm = SlangNormalizer.normalize(Slang.normalize(GoogleSourcePreparation.prepare(text)))' in fix
assert 'const val HIZLI_SURUM = 5' in cache
import re
v = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)"', (b/'app/build.gradle.kts').read_text())
assert v is not None and (int(v[1]), int(v[2])) >= (10, 20)
print('10.20 akış koruma testi geçti')
