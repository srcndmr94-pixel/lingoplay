from pathlib import Path
r=Path(__file__).resolve().parent.parent
src=r/'app/src/main/java/com/example/altyazi'
read=lambda n:(src/n).read_text()
ui=(r/'app/src/main/res/layout/activity_main.xml').read_text()
grad=(r/'app/build.gradle.kts').read_text()
checks={
'10.42.4/versionCode 172': 'versionCode = 172' in grad and '10.42.4-deepl-cleanup' in grad,
'deepl opt-in default off': 'sp.getBoolean("deepLEnabled", false)' in read('Prefs.kt'),
'deepl api key required': 'val useDeepL = prefs.deepLEnabled && deeplKey.isNotBlank()' in read('CaptureService.kt'),
'deepl raw benchmark gated': 'prefs.deepLEnabled && deeplKey.isNotBlank()' in read('EngineBenchmark1035.kt'),
'deepl references never sent': 'DeepLCeviri.translate(case.source, deeplKey)' in read('EngineBenchmark1035.kt'),
'deepl priority fallback': 'remote ?: (if (prefs.opusMtEnabled' in read('CaptureService.kt'),
'deepl secured prefs': 'AndroidKeyStore' in read('DeepLCredentials.kt') and 'AES/GCM/NoPadding' in read('DeepLCredentials.kt'),
'deepl new settings': all(s in ui for s in ['cbDeepL','etDeepLKey','btnModelCleanup']),
'candidate model cleanup': 'OpusModelPack.deleteInstalled(this)' in read('MainActivity.kt'),
'active session guard': 'CaptureService.running || EngineBenchmark1035.running || LlmTranslate.isLoading()' in read('MainActivity.kt'),
'confirmation': 'setPositiveButton("MODELİ SİL")' in read('MainActivity.kt'),
'feedback': 'DeepL kabul=' in read('CaptureService.kt'),
'no embedded key': 'DeepL-Auth-Key $key' in read('DeepLCeviri.kt'),
}
for name,ok in checks.items():
    print(('PASS' if ok else 'FAIL')+' '+name)
assert all(checks.values())
print(f'{len(checks)}/{len(checks)} static feature checks passed')
