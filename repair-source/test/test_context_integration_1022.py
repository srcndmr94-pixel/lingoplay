"""10.22 bağlam akışının 10.23 içinde korunmasını kontrol eder."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
c=(r/'app/src/main/java/com/example/altyazi/CaptureService.kt').read_text()
d=(r/'app/src/main/java/com/example/altyazi/DialogueContextRepair.kt').read_text()
v=(r/'app/build.gradle.kts').read_text()
assert 'if (hizli && prefs.googleCeviri && GoogleCeviri.kullanilabilir && nameIn == null)' in c
assert 'dialogueContext.joinedTranslationInput(source)' in c
assert 'fastTranslateKeepingName(joined)' in c
assert 'publish(source, full, remember = false, tag = " · bağlı replik")' in c
assert c.index('val dictHit = OfflineFix.lookup(bodyIn)') < c.index('dialogueContext.joinedTranslationInput(source)') < c.index('// 2) Hafıza.')
assert 'nowMs - before.timestampMs > 7_000L' in d
assert 'sentenceEnding.containsMatchIn(previous)' in d
assert 'current.firstOrNull()?.isLowerCase() != true' in d
assert 'versionCode = 157' in v and 'versionName = "10.24"' in v
print('OK 10.22: production integration, old phrase priority, no persistent cross-context cache')
