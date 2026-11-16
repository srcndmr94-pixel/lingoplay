from pathlib import Path
from zipfile import ZipFile
root = Path(__file__).resolve().parents[1]
prefix = 'app/src/main/java/com/example/altyazi/'
with ZipFile(root.parent / 'GameLingo204_Kaynak.zip') as z:
    for name in ['Overlay.kt', 'SubtitleOverlayPlacement299.kt', 'DeepLCeviri.kt',
                 'GoogleCeviri.kt', 'FirefoxCeviri.kt', 'OpusMtEngine.kt']:
        assert z.read(prefix + name) == (root / (prefix + name)).read_bytes(), name
    before = z.read(prefix + 'CaptureService.kt').decode()
after = (root / (prefix + 'CaptureService.kt')).read_text()
start = '        suspend fun opusAttempt(text: String): String? {'
end = '        if (useDeepL) log('
assert before.split(start, 1)[1].split(end, 1)[0] == after.split(start, 1)[1].split(end, 1)[0], 'engine order changed'
assert 'else localRescue?.join()' in after
assert 'subtitleRetry205.failed(' in after
assert 'translatingFor = "" // Finished failure' in after
assert 'if (collapsed.length < 3 && !SubtitleReadGuard299.shortSpokenLine(collapsed))' in after
print('PASS: engine order, providers and overlay unchanged; retry and short speech wired')
