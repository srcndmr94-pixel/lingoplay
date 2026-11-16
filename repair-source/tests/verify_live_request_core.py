"""Static wiring checks for Android-side request identity (not an APK test)."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/example/altyazi/CaptureService.kt').read_text()
g=(root/'app/src/main/java/com/example/altyazi/SubtitleGenerationGate.kt').read_text()
assert 'private val subtitleGeneration = SubtitleGenerationGate()' in s
assert 'val runId = subtitleGeneration.newSubtitle()' in s
assert 'runTranslation(source, runId)' in s
assert 'if (runId != null && !subtitleGeneration.isCurrent(runId))' in s
assert 'inFlight.remove(oldestId)?.cancel()' in s
assert 'kotlinx.coroutines.CoroutineStart.LAZY' in s
assert s.count('subtitleGeneration.invalidate()') >= 4
assert 'inFlight.remove(runId)' in s
assert 'startedAt.remove(source, startedMs)' in s
start=s.index('private suspend fun runTranslation(source: String, runId: Long)')
end=s.index('    // ----------------------------------------------------- menü / görev yazısı',start)
chunk=s[start:end]
assert not re.search(r'(?<!ForRun\()\bpublish\(',chunk), 'One path bypassed request ID'
assert chunk.count('publishForRun(runId,')>=15
print('PASS: 11 integrated latest-request safeguards (static source checks)')
