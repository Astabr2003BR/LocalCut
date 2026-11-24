from pathlib import Path
import xml.etree.ElementTree as ET

root = Path('work/app')
gradle = (root / 'build.gradle.kts').read_text()
assert 'applicationId = "com.localcut.editor.toqiexperiment"' in gradle, 'Experimental app ID missing'
manifest = ET.parse(root / 'src/main/AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = manifest.find('application')
assert app is not None and app.get(a + 'label') == 'LocalCut Experimental'
activity = next(x for x in app.findall('activity') if x.get(a + 'name') == '.mobile.MobileHandoffActivity')
assert activity.get(a + 'exported') == 'true'
assert not activity.findall('intent-filter'), 'Experimental build must not claim existing links'
path = root / 'src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt'
source = path.read_text()
old = '                    if (!openChatGptAndFinish()) {\n                        openEditorAndFinish()\n                    }'
new = '                    returnAfterHandoff(request.returnTarget)'
if source.count(old) == 1 and source.count(new) == 0:
    path.write_text(source.replace(old, new, 1))
elif source.count(old) != 0 or source.count(new) != 1:
    raise RuntimeError('Unexpected remote approval implementation; refusing build')
print('Experimental package, label, handoff isolation and approval return verified')
