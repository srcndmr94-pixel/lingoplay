#!/usr/bin/env python3
"""Real Kotlin Yo context + caption-switch tests, and runtime wiring checks."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
code = root / 'app/src/main/java/com/example/altyazi'
test = root / 'test'
service = (code / 'CaptureService.kt').read_text()
guard = (code / 'AiGuard.kt').read_text()
assert 'YoContext10427.correct(kaynak, ceviri)' in guard
assert 'YoContext10427.correct(source, GameplayLogRepair299.repairTranslation(source, it))' in service
accepted = service.index('val runId = subtitleGeneration.newSubtitle()')
cleared = service.index('CaptionSwitch10427.shouldClear', accepted)
started = service.index('job.start()', accepted)
assert accepted < cleared < started
assert 'showSubtitle(null)' in service[cleared:started]
assert 'screenHeight - dp(110)' in service, 'bottom placement changed unexpectedly'
assert shutil.which('kotlinc') and shutil.which('java'), 'Kotlin toolchain unavailable'
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'yo-caption.jar'
    cmd = ['kotlinc', str(code/'YoContext10427.kt'), str(code/'CaptionSwitch10427.kt'),
           str(test/'YoAndCaption10427Test.kt'), '-include-runtime', '-d', str(jar)]
    subprocess.run(cmd, check=True, timeout=90)
    p = subprocess.run(['java', '-jar', str(jar)], check=True, capture_output=True, text=True, timeout=25)
    print(p.stdout.strip())
print('PASS: production wiring / old caption cleared before translation launch / bottom position unchanged')
