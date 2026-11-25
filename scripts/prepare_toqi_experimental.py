from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET
from experimental_legacy_bridge import prepare as prepare_legacy_bridge
from experimental_cold_start_recovery import prepare as prepare_cold_start_recovery
from experimental_credential_guard import prepare as prepare_credential_guard

root = Path('work/app')
gradle = (root / 'build.gradle.kts').read_text()
assert 'applicationId = "com.localcut.editor.toqiexperiment"' in gradle, 'Experimental app ID missing'
manifest = ET.parse(root / 'src/main/AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = manifest.find('application')
assert app is not None and app.get(a + 'label') == 'LocalCut Experimental'
activity = next(x for x in app.findall('activity') if x.get(a + 'name') == '.mobile.MobileHandoffActivity')
assert activity.get(a + 'exported') == 'false', 'Experimental handoff must not accept external intents'
assert not activity.findall('intent-filter'), 'Experimental build must not claim existing links'
path = root / 'src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt'
source = path.read_text()
start = source.index('    private fun handleRemoteApproval(')
end = source.index('    private fun openOAuthCompletionAndFinish(', start)
remote = source[start:end]
template = Path('scripts/templates/experimental_remote_approval.kt.txt').read_text()
expected = 'a663daa12548cdd146c1868aceac9b6224d7f966bcd956f01966648d6edf99b2'
if hashlib.sha256(remote.encode()).hexdigest() == expected:
    source = source[:start] + template + source[end:]
    path.write_text(source)
    remote = template
elif remote != template:
    raise RuntimeError('Unknown handoff implementation; refusing build')
assert remote.count('val consent = RemoteApprovalConsent(') == 1
assert remote.count('approveRemoteControlRequest(request.requestId)') == 1
assert '.setPositiveButton("Autorizar") { _, _ -> consent.accept() }' in remote
assert '.setNegativeButton("Cancelar") { _, _ -> consent.reject() }' in remote
assert '.setOnCancelListener { consent.reject() }' in remote
assert 'openChatGptAndFinish()' not in remote
# The legacy hard block consumes the experimental recovery implementation.
# Prepare it first, never in the shared overrides or the smoke workflow.
prepare_cold_start_recovery(root)
prepare_legacy_bridge(root)
prepare_credential_guard(root)
__import__('experimental_bridge_panel').prepare(root)
print('Experimental package, internal handoff and remote approval consent verified')
