from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/example/altyazi'
checks = [
    ('MeaningSafety exists', (src/'MeaningSafety.kt').exists()),
    ('Gate incorporates meaning checks', 'MeaningSafety.risk(s, t)' in (src/'TranslationQualityGate.kt').read_text()),
    ('Output gate actually rejects', 'TranslationQualityGate.mustReject(outputIssue)' in (src/'CaptureService.kt').read_text()),
    ('Fallback to alternate engine', 'f?.translate(body)' in (src/'CaptureService.kt').read_text()),
    ('Fast mode skips service preload', 'if (onDeviceUsed && LlmTranslate.hasModel(this))' in (src/'CaptureService.kt').read_text()),
    ('No eager home preload in any mode', 'LlmTranslate.preload(ctx)' not in (src/'ModelSetup.kt').read_text()),
    ('Local AI instruction revised', 'Never reverse negation or prohibition' in (src/'LlmTranslate.kt').read_text()),
    ('Version code advanced', __import__('re').search(r'versionCode = (\d+)', (root/'app/build.gradle.kts').read_text()) and int(__import__('re').search(r'versionCode = (\d+)', (root/'app/build.gradle.kts').read_text()).group(1)) >= 158),
    ('JVM regression present', (root/'test/MeaningSafety1033Test.kt').exists()),
]
for name, passed in checks:
    if not passed:
        raise AssertionError(name)
print(f'{len(checks)}/{len(checks)} integrated source checks PASS')
