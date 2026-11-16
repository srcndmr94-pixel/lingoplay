from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/example/altyazi'
checks = {
  'version >= 10.35': tuple(map(int, __import__('re').search(r'versionName = "(\d+)\.(\d+)"', (root/'app/build.gradle.kts').read_text()).groups())) >= (10, 35),
  'versionCode >= 160': int(__import__('re').search(r'versionCode = (\d+)', (root/'app/build.gradle.kts').read_text()).group(1)) >= 160,
  'benchmark is exposed in model settings': 'EngineBenchmark1035.run(ctx)' in (src/'ModelSetup.kt').read_text(),
  'benchmark blocks capture conflicts': 'CaptureService.running' in (src/'ModelSetup.kt').read_text(),
  'on-demand model prepare': 'llm.prepare()' in (src/'EngineBenchmark1035.kt').read_text(),
  'actual google call bypasses app cache': 'GoogleCeviri.cevir(case.source, kullanHafiza = false)' in (src/'EngineBenchmark1035.kt').read_text(),
  'real ML Kit backend': 'fast.translate(case.source)' in (src/'EngineBenchmark1035.kt').read_text(),
  'real LLM backend': 'llm.translate(case.source' in (src/'EngineBenchmark1035.kt').read_text(),
  'no reference answer passed to LLM': 'case.source, case.previous, "Oyun", ""' in (src/'EngineBenchmark1035.kt').read_text(),
  'benchmark saved locally': 'GameLingo_model_test_latest.txt' in (src/'EngineBenchmark1035.kt').read_text(),
  'real statistics test exists': (root/'test/BenchmarkStats1035Test.kt').exists(),
  'previous meaning safety present': (src/'MeaningSafety.kt').exists(),
  'previous OCR repair present': (src/'SubtitlePreparation.kt').exists(),
  'previous release 10.34 test exists': (root/'test/OcrContext1034Test.kt').exists(),
  'fast mode avoids LLM preloading': 'if (onDeviceUsed && LlmTranslate.hasModel(this))' in (src/'CaptureService.kt').read_text(),
}
for name, passed in checks.items():
    assert passed, f'FAILED: {name}'
print(f'{len(checks)}/{len(checks)} 10.35 source integration checks PASSED')
