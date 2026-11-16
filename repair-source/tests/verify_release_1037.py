from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/example/altyazi'
read = lambda name: (src / name).read_text()
service, model, policy = read('CaptureService.kt'), read('ModelSetup.kt'), read('LiveEnginePolicy.kt')
q, off, google = read('TranslationQualityGate.kt'), read('OfflineFix.kt'), read('GoogleCeviri.kt')
build = (root/'app/build.gradle.kts').read_text()
ui = (root/'app/src/main/res/layout/activity_main.xml').read_text()
checks = {
    '10.37 APK and monotone code': 'versionName = "10.37"' in build and 'versionCode = 162' in build,
    'application identity unchanged': 'applicationId = "com.example.altyazi"' in build,
    'identical debug keystore retained': (root/'app/debug.keystore').exists(),
    'live model policy used to gate E4B': 'LiveEnginePolicy.usePhoneLlm(prefs.ceviriBicimi)' in service,
    'default balanced uses fast context pipeline': 'LiveEnginePolicy.useFastPipeline(prefs.ceviriBicimi, prefs.mode)' in service,
    'no settings-onResume model preload': 'LlmTranslate.preload(ctx)' not in model.split('fun onResume()')[1].split('fun onPause()')[0],
    'no model-switch eager preload': 'LlmTranslate.preload(ctx)' not in model,
    'human-facing balanced mode updated': 'Dengeli (önerilen) · Google öncelikli' in ui,
    'benchmark remains callable': 'llm.translate(case.source' in read('EngineBenchmark1035.kt'),
    'benchmark report version accurate': 'GameLingo 10.37 - MODEL KARSILASTIRMA' in read('EngineBenchmark1035.kt'),
    'google result checked before ML fallback': 'TranslationQualityGate.mustReject(TranslationQualityGate.check(text, candidate))' in off,
    'cancellation not swallowed': 'catch (e: kotlinx.coroutines.CancellationException) { throw e }' in off,
    'lost profanity detected': 'LOST_PROFANITY' in q,
    'idiom mistranslation detected': 'LITERAL_IDIOM' in q,
    'google cache rejects known low-fidelity translations': 'TranslationQualityGate.mayRemember(kaynak, sonuc)' in google,
    'google rolling latency tracked': 'latency.add(' in google and 'latency.summary()' in google,
    'second opinion only in balanced': 'LiveEnginePolicy.secondOpinionForWarning(prefs.ceviriBicimi)' in service,
    'second opinion compared after rendering': 'TranslationQualityGate.choose(body, duzgun, alternative?.let(::render))' in service,
    'old OCR safeguards retained': 'SubtitlePreparation.confidentFirstRead(text)' in service,
    'old stale subtitle ID logic retained': 'translatingFor' in service,
    'meaning regression tests retained': (root/'test/MeaningSafety1033Test.kt').exists(),
}
for name, ok in checks.items():
    assert ok, 'FAILED: '+name
print(f'PASS {len(checks)}/{len(checks)} 10.37 source integration checks')
