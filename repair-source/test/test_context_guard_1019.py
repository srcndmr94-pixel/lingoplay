"""10.19: Google çok cümle tek blok, tek cümle/kalıp/fallback değişmedi."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/example/altyazi'
cap=(base/'CaptureService.kt').read_text()
of=(base/'OfflineFix.kt').read_text()
g=(base/'GoogleCeviri.kt').read_text()
ts=(base/'TranslationStore.kt').read_text()
checks={
    'çok cümle tek Google çağrısı': 'gucluFn != null && sentences.size > 1' in of and 'return try { cachedRun(cleaned) }' in of,
    'tek cümle davranışı': 'if (sentences.size == 1) return cached(sentences[0])' in of,
    'kalıp önceliği': 'sentences.none { lookup(it) != null }' in of,
    'Google -> Firefox fallback': 'if (ff != null && ff.hazir) ff.cevir(parca)' in cap,
    'Google ham metin korunur': 'val k = NameShield.shield(GoogleSourcePreparation.prepare(text))' in of and 'val norm = SlangNormalizer.normalize(Slang.normalize(GoogleSourcePreparation.prepare(text)))' in of,
    'OCR denetimi kullanılıyor': 'SubtitlePreparation.confidentFirstRead(text)' in cap,
    'OCR dar onarımlar kullanılıyor': 'SubtitlePreparation.repair(SoundTags.repair(' in cap,
    'tanı ham Google yanıtı': 'trace?.invoke("GOOGLE HAM:' in g,
    'hafıza sürümü': bool(__import__('re').search(r'const val HIZLI_SURUM = ([6-9]|[1-9][0-9]+)\b', ts)),
}
for label,ok in checks.items():
    print(('OK' if ok else 'FAILED')+' '+label)
assert all(checks.values())
