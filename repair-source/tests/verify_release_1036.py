from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/example/altyazi'
b = (src/'EngineBenchmark1035.kt').read_text()
ui = (src/'ModelSetup.kt').read_text()
main = (src/'MainActivity.kt').read_text()
service = (src/'CaptureService.kt').read_text()
gradle = (root/'app/build.gradle.kts').read_text()
checks = {
    'version at least 10.36': tuple(map(int, __import__('re').search(r'versionName = "(\d+)\.(\d+)"', gradle).groups())) >= (10,36) and int(__import__('re').search(r'versionCode = (\d+)', gradle).group(1)) >= 161,
    'latest ZIP workflow-safe filename documented': (root/'10.36-DEGISIKLIK-VE-TEST.md').exists(),
    'report saved first': b.index('progress("Test başladı; rapor kaydedildi")') < b.index('llm.prepare()'),
    'incremental reports per subtitle': 'persist() // Her replikten hemen sonra' in b,
    'partial report on cancel': 'İPTAL EDİLDİ: kısmi rapor saklandı' in b,
    'prepare timeout': 'withTimeoutOrNull(120_000L)' in b,
    'mlkit prepare timeout': 'withTimeoutOrNull(30_000L)' in b,
    'independent model phases': all(s in b for s in ['phase("Google")', 'phase("ML Kit")', 'phase(llm.backendName)']),
    'test cannot start during capture': 'if (CaptureService.running)' in ui,
    'capture cannot start during test': 'if (EngineBenchmark1035.running)' in main and 'if (EngineBenchmark1035.running)' in service,
    'test progress has its own status text': 'benchmarkStatus?.text = "Model testi: $progress"' in ui,
    'test available without llm model': 'if (!LlmTranslate.hasModel(ctx))' not in ui,
    'onPause does not cancel test': 'benchmarkJob?.cancel()' not in ui.split('fun onPause()')[1].split('fun onDestroy()')[0],
    'onDestroy releases test': 'modelSetup?.onDestroy()' in main,
    'original translation pipeline kept': 'runTranslation(source: String)' in service,
    'real Google, MLKit and E4B calls kept': all(s in b for s in ['GoogleCeviri.cevir(case.source, kullanHafiza = false)', 'fast.translate(case.source)', 'llm.translate(case.source']),
}
for name, value in checks.items():
    assert value, 'FAIL: '+name
print(f'{len(checks)}/{len(checks)} 10.36 source connection checks PASS')
