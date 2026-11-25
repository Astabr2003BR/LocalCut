"""Offline tests for the Experimental-only paired-server panel transform."""
from pathlib import Path
import hashlib
import os
import runpy
import shutil
import socket
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

import experimental_bridge_panel as panel

REPO = Path(__file__).resolve().parent.parent


class ExperimentalPanelTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='experimental-panel-')
        self.root = Path(self.tmp.name)
        with zipfile.ZipFile(REPO / 'localcut-source-full-build9.zip') as archive:
            for item in archive.infolist():
                if item.filename.startswith('app/src/') and not item.is_dir():
                    target = self.root / 'work' / item.filename
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(item))
        shutil.copytree(REPO / 'overrides/app', self.root / 'work/app', dirs_exist_ok=True)
        shutil.copytree(REPO / 'scripts', self.root / 'scripts', ignore=shutil.ignore_patterns('__pycache__'))
        self.old_cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.old_cwd)
        self.addCleanup(self.tmp.cleanup)
        self.app = Path('work/app')
        self.target = self.app / panel.PANEL
        self.original = self.target.read_bytes()

    def offline(self):
        def deny(*args, **kwargs):
            raise AssertionError('network forbidden')
        return patch.multiple(socket, socket=deny, create_connection=deny, getaddrinfo=deny)

    def test_real_preparer_corrects_only_staged_panel(self):
        shared = REPO / 'overrides/app' / panel.PANEL
        shared_before = shared.read_bytes()
        original_path = sys.path[:]
        try:
            sys.path.insert(0, str(self.root / 'scripts'))
            with self.offline():
                runpy.run_path(str(self.root / 'scripts/prepare_toqi_experimental.py'), run_name='__main__')
        finally:
            sys.path[:] = original_path
        updated = self.target.read_text(encoding='utf-8')
        expected = self.original.decode('utf-8').replace(panel.OLD_MESSAGE, panel.NEW_MESSAGE).replace(panel.OLD_BUTTON, panel.NEW_BUTTON)
        self.assertEqual(updated, expected)
        self.assertNotIn(panel.OLD_MESSAGE, updated)
        self.assertIn('enabled = !state.paired,', updated)
        self.assertIn('enabled = !state.paired && relayDraft.isNotBlank(),', updated)
        self.assertIn('Troca de servidor bloqueada', updated)
        self.assertIn('não foi realizada', updated)
        self.assertEqual(shared_before, shared.read_bytes())
        control = (self.app / 'src/main/java/com/localcut/editor/bridge/agent/LocalCutBridgeControl.kt').read_text()
        revoke = control[control.index('    fun revokePairing()'):]
        self.assertLess(revoke.index('não foi revogado no servidor'), revoke.index('endpointPreference.current()'))

    def test_unreviewed_source_rejected_without_writing(self):
        self.target.write_bytes(self.original + b'\n// unreviewed\n')
        before = self.target.read_bytes()
        with self.offline(), self.assertRaisesRegex(RuntimeError, 'unreviewed panel'):
            panel.prepare(self.app)
        self.assertEqual(self.target.read_bytes(), before)

    def test_non_experimental_id_or_label_rejected(self):
        gradle = self.app / 'build.gradle.kts'
        gradle_original = gradle.read_text()
        gradle.write_text(gradle_original.replace('com.localcut.editor.toqiexperiment', 'com.localcut.editor'))
        with self.offline(), self.assertRaisesRegex(RuntimeError, 'application ID'):
            panel.prepare(self.app)
        self.assertEqual(self.target.read_bytes(), self.original)
        gradle.write_text(gradle_original)
        manifest = self.app / 'src/main/AndroidManifest.xml'
        manifest.write_text(manifest.read_text().replace('LocalCut Experimental', 'LocalCut'))
        with self.offline(), self.assertRaisesRegex(RuntimeError, 'application label'):
            panel.prepare(self.app)
        self.assertEqual(self.target.read_bytes(), self.original)

    def test_not_staged_root_rejected(self):
        with self.offline(), self.assertRaisesRegex(RuntimeError, 'not the staged'):
            panel.prepare(REPO / 'overrides/app')
        self.assertEqual(self.target.read_bytes(), self.original)


if __name__ == '__main__':
    unittest.main()
