"""10.23 UI regression checks; Android runtime must be tested separately."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[1]
base = root/'app/src/main'
xml = (base/'res/layout/activity_region.xml').read_text()
ui = (base/'java/com/example/altyazi/RegionActivity.kt').read_text()
view = (base/'java/com/example/altyazi/RegionView.kt').read_text()
service = (base/'java/com/example/altyazi/CaptureService.kt').read_text()
ET.fromstring(xml)
for item in ('btnEdgeTop', 'btnEdgeBottom', 'btnEdgeLeft', 'btnEdgeRight',
             'sbRegionEdge','btnRegionDefault','btnRegionSave','btnRegionCancel','regionView'):
    assert '@+id/'+item in xml and 'R.id.'+item in ui, item
for prop in ('leftPct','rightPct','topPct','bottomPct'):
    assert 'prefs.'+prop+' =' in ui, prop
assert 'region.setBounds(region.bounds().change(selectedEdge, progress))' in ui
assert 'onRegionChanged?.invoke()' in view
assert 'previewBitmap' in view and 'lastPreviewRaw' in service
assert 'if (!MainActivity.visible && !RegionActivity.visible) lastPreviewRaw = raw' in service
assert 'android.graphics.Rect(0, 0, usable, full.height)' in service
assert 'versionName = "10.24"' in (root/'app/build.gradle.kts').read_text()
print('OK: 10.23 UI controls, preview, coordinate alignment, existing OCR preference binding')
