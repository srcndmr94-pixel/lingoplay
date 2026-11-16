"""10.39 name and speaker-cue JVM regressions without Android SDK.
Requires installed kotlinc + java. Uses the unmodified production NameShield body
with only dictionary/name-book stubs; no Android services are simulated.
"""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
code = root / 'app/src/main/java/com/example/altyazi'
test = root / 'test'
source = (code / 'OfflineFix.kt').read_text()
start = source.index('object NameShield {')
end = source.index('/** Uygulama genelinde tek örnek. */', start)
with tempfile.TemporaryDirectory() as temp:
    directory = Path(temp)
    (directory / 'NameShieldExtract.kt').write_text('''package com.example.altyazi
object Sozluk { const val hazir = true; fun icinde(word: String) = true }
object AdDefteri { fun adMi(word: String) = false }
''' + source[start:end])
    plans = [
        ([code/'ProperNameSequence1039.kt', directory/'NameShieldExtract.kt', test/'ProperNameSequence1039Test.kt'], directory/'names.jar'),
        ([code/'GameplayLogRepair299.kt', code/'SubtitlePreparation.kt', test/'OcrSpeaker1039Test.kt'], directory/'ocr.jar'),
    ]
    for inputs, jar in plans:
        subprocess.run(['kotlinc', *map(str, inputs), '-include-runtime', '-d', str(jar)], check=True)
        subprocess.run(['java', '-jar', str(jar)], check=True)
