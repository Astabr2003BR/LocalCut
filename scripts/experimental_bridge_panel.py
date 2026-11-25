"""Correct the paired-server panel only in the staged Experimental app."""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET

PANEL = Path('src/main/java/com/localcut/editor/ui/editor/BridgePanel.kt')
EXPECTED_SHA256 = '871ba5b416c990ff5a9c5fae8e18a3a67ed44fec2f2ad0ef1d17371c6ceaf640'
OLD_MESSAGE = 'Pareamento seguro ativo. Revogue antes de trocar o servidor Relay.'
NEW_MESSAGE = ('Pareamento existente ou estado desconhecido. Troca de servidor bloqueada no Experimental. '
               'A revogação pelo Experimental está indisponível e não foi realizada.')
OLD_BUTTON = 'OutlinedButton(onClick = onRevokePairing) { Text("Revogar pareamento") }'
NEW_BUTTON = 'OutlinedButton(onClick = onRevokePairing, enabled = false) { Text("Revogação indisponível") }'


def prepare(root: Path):
    root = Path(root)
    if root.is_symlink() or root.resolve() != Path('work/app').resolve():
        raise RuntimeError('Experimental panel: not the staged work/app')
    gradle = (root / 'build.gradle.kts').read_text(encoding='utf-8')
    if 'applicationId = "com.localcut.editor.toqiexperiment"' not in gradle:
        raise RuntimeError('Experimental panel: experimental application ID missing')
    app = ET.parse(root / 'src/main/AndroidManifest.xml').getroot().find('application')
    if app is None or app.get('{http://schemas.android.com/apk/res/android}label') != 'LocalCut Experimental':
        raise RuntimeError('Experimental panel: experimental application label missing')
    path = root / PANEL
    if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
        raise RuntimeError('Experimental panel: unsafe panel path')
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != EXPECTED_SHA256:
        raise RuntimeError('Experimental panel: unreviewed panel source')
    source = raw.decode('utf-8')
    if source.count(OLD_MESSAGE) != 1 or source.count(OLD_BUTTON) != 1:
        raise RuntimeError('Experimental panel: unknown or ambiguous panel structure')
    updated = source.replace(OLD_MESSAGE, NEW_MESSAGE, 1).replace(OLD_BUTTON, NEW_BUTTON, 1)
    path.write_bytes(updated.encode('utf-8'))
