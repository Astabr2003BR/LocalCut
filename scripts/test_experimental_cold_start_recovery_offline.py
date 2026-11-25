"""Offline tests for opt-in staging; never accesses credentials or devices."""
from pathlib import Path
import os
import shutil
import socket
import tempfile
import unittest
from unittest.mock import patch

import experimental_cold_start_recovery as cold
import experimental_legacy_bridge as legacy

REPO = Path(__file__).resolve().parent.parent

class ColdPreparationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='cold-stage-')
        self.old_cwd = Path.cwd()
        os.chdir(self.tmp.name)
        self.root = Path('work/app')
        for rel in cold.FILES:
            target = self.root / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / 'overrides/app' / rel, target)
        (self.root / 'build.gradle.kts').write_text('applicationId = "com.localcut.editor.toqiexperiment"\n')
        (self.root / 'src/main/AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="LocalCut Experimental"/></manifest>')
        self.network = patch.object(socket, 'socket', side_effect=AssertionError('network forbidden'))
        self.network.start()

    def tearDown(self):
        self.network.stop()
        os.chdir(self.old_cwd)
        self.tmp.cleanup()

    def contents(self):
        return {name: (self.root / name).read_bytes() for name in cold.FILES}

    def test_transform_preserves_shared_files_and_matches_legacy_contract(self):
        before = self.contents()
        cold.prepare(self.root)
        after = self.contents()
        self.assertTrue(all(before[p] != after[p] for p in before))
        main = 'src/main/java/com/localcut/editor/bridge/agent/BridgeColdStartRecovery.kt'
        test = 'src/test/java/com/localcut/editor/bridge/agent/BridgeColdStartRecoveryPolicyTest.kt'
        source = after[main].decode()
        self.assertEqual(legacy.sha(source), legacy.EXPECTED['bridge/agent/BridgeColdStartRecovery.kt'])
        self.assertIn('paired && alwaysConnected && sessionEnabled', source)
        self.assertNotIn('settingsStore.sessionEnabled = true', source)
        for name in ('recoveryRequiresPairingAlwaysConnectedAndEnabledSession', 'manualDisconnectStaysDisabledAcrossColdStart', 'explicitlyReenabledSessionCanRecover', 'failedServiceStartLeavesDesiredSessionUnchanged'):
            self.assertIn(name, after[test].decode())
        for rel, content in before.items():
            self.assertEqual((REPO / 'overrides/app' / rel).read_bytes(), content)

    def test_changed_input_or_test_rejected_before_either_write(self):
        for rel in cold.FILES:
            with self.subTest(rel=rel):
                target = self.root / rel
                original = target.read_bytes()
                target.write_bytes(original + b'\n// unknown change\n')
                before = self.contents()
                with self.assertRaisesRegex(RuntimeError, 'unexpected source or test'):
                    cold.prepare(self.root)
                self.assertEqual(self.contents(), before)
                target.write_bytes(original)

    def test_wrong_root_rejected(self):
        before = self.contents()
        with self.assertRaisesRegex(RuntimeError, 'not the staged'):
            cold.prepare(Path('other/app'))
        self.assertEqual(self.contents(), before)

    def test_non_experimental_id_and_label_rejected(self):
        gradle = self.root / 'build.gradle.kts'
        manifest = self.root / 'src/main/AndroidManifest.xml'
        good_gradle, good_manifest = gradle.read_text(), manifest.read_text()
        for path, bad in ((gradle, good_gradle.replace('toqiexperiment', 'approved')),
                          (manifest, good_manifest.replace('LocalCut Experimental', 'LocalCut'))):
            with self.subTest(path=path):
                previous = path.read_text()
                path.write_text(bad)
                before = self.contents()
                with self.assertRaisesRegex(RuntimeError, 'experimental application'):
                    cold.prepare(self.root)
                self.assertEqual(self.contents(), before)
                path.write_text(previous)

    def test_repeated_preparation_rejected_without_further_changes(self):
        cold.prepare(self.root)
        before = self.contents()
        with self.assertRaisesRegex(RuntimeError, 'unexpected source or test'):
            cold.prepare(self.root)
        self.assertEqual(self.contents(), before)

if __name__ == '__main__':
    unittest.main()
