from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/example/altyazi'
service=(src/'CaptureService.kt').read_text()
required=['SourceAwareDialogueFix.kt','TranslationQualityGate.kt']
for filename in required:
 assert (src/filename).exists(), f'Missing previously shipped feature: {filename}'
assert 'SourceAwareDialogueFix.correct(bodyIn)' not in service, 'Fixed scene translations still override live engine'
assert 'TranslationQualityGate.check(source, value)' in service, 'Output gate missing'
assert 'TranslationQualityGate.mayRemember(source, value)' in service, 'Cache gate missing'
assert 'TranslationQualityGate.choose(body, duzgun, alternative?.let(::render))' in service, 'Fallback gate missing'
print('Release regression checks: 6/6 PASS')
