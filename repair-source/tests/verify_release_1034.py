from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/example/altyazi'
checks = {
    'version at least 10.34': tuple(map(int, __import__('re').search(r'versionName = "(\d+)\.(\d+)', (root/'app/build.gradle.kts').read_text()).groups())) >= (10, 34),
    'versionCode >= 159': int(__import__('re').search(r'versionCode = (\d+)', (root/'app/build.gradle.kts').read_text()).group(1)) >= 159,
    'safe OCR repair included': 'lever(?=' in (src/'SubtitlePreparation.kt').read_text(),
    'safe ambiguous OCR repair included': '^wet,' in (src/'SubtitlePreparation.kt').read_text(),
    'context repair connected to DeepL': 'dialogueContext.contextFor(source)' in (src/'CaptureService.kt').read_text(),
    'preprocessor connected': 'SubtitlePreparation.repair(' in (src/'CaptureService.kt').read_text(),
    'source-aware historical answers do not override model': 'SourceAwareDialogueFix.correct(bodyIn)' not in (src/'CaptureService.kt').read_text(),
    'fast mode avoids E4B preloading': 'if (onDeviceUsed && LlmTranslate.hasModel(this))' in (src/'CaptureService.kt').read_text(),
    'new kotlin tests included': (root/'test/OcrContext1034Test.kt').is_file(),
}
for name, valid in checks.items():
    assert valid, f'FAIL: {name}'
print(f'{len(checks)}/{len(checks)} source integration checks PASS')
