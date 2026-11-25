"""Offline tests of real Experimental source preparation (no SDK and no network)."""
from pathlib import Path
import os
import runpy
import shutil
import socket
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

REPO = Path(__file__).resolve().parent.parent

class PrepareTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='localcut-exp-test-')
        self.root = Path(self.temp.name)
        # Extract only app sources, never the signing keystore or other archive contents.
        with zipfile.ZipFile(REPO / 'localcut-source-full-build9.zip') as archive:
            for item in archive.infolist():
                if item.filename.startswith('app/src/') and not item.is_dir():
                    dest = self.root / 'work' / item.filename
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_bytes(archive.read(item))
        shutil.copytree(REPO / 'overrides/app', self.root / 'work/app', dirs_exist_ok=True)
        shutil.copytree(REPO / 'scripts', self.root / 'scripts', ignore=shutil.ignore_patterns('__pycache__'))

    def tearDown(self):
        self.temp.cleanup()

    def run_prepare(self):
        old_cwd = Path.cwd()
        old_path = sys.path[:]
        sys.modules.pop('experimental_legacy_bridge', None)
        try:
            os.chdir(self.root)
            sys.path.insert(0, str(self.root / 'scripts'))
            def deny(*args, **kwargs):
                raise AssertionError('network forbidden during offline test')
            with patch.object(socket, 'socket', deny), patch.object(socket, 'create_connection', deny), patch.object(socket, 'getaddrinfo', deny):
                runpy.run_path(str(self.root / 'scripts/prepare_toqi_experimental.py'), run_name='__main__')
        finally:
            os.chdir(old_cwd)
            sys.path[:] = old_path
            sys.modules.pop('experimental_legacy_bridge', None)

    def test_real_transformation(self):
        main = self.root / 'work/app/src/main/java/com/localcut/editor'
        portable_before = (main / 'mobile/PortableStudioRelay.kt').read_bytes()
        media_before = (main / 'mobile/RemoteMediaDownloader.kt').read_bytes()
        self.run_prepare()
        self.assertEqual(portable_before, (main / 'mobile/PortableStudioRelay.kt').read_bytes())
        self.assertEqual(media_before, (main / 'mobile/RemoteMediaDownloader.kt').read_bytes())
        tests = self.root / 'work/app/src/test/java/com/localcut/editor/bridge/transport'
        pairing = (main / 'bridge/transport/OkHttpBridgePairingClient.kt').read_text()
        socket_code = (main / 'bridge/transport/OkHttpBridgeSocketClient.kt').read_text()
        self.assertNotIn('.newCall(', pairing)
        self.assertNotIn('.newWebSocket(', socket_code)
        self.assertEqual(pairing.count('= blocked()'), 8)
        self.assertIn('EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED', socket_code)
        runtime = (main / 'LocalCutProcessRuntime.kt').read_text()
        self.assertIn('rejectLegacyInbound = true', runtime)
        handler = runtime.split('bridgeInboundMessageHandler = BridgeInboundMessageHandler', 1)[1].split('bridgeControl =', 1)[0]
        self.assertNotIn('post', handler)
        self.assertNotIn('process(', handler)
        processor = (main / 'bridge/agent/BridgeMessageProcessor.kt').read_text()
        self.assertLess(processor.index('if (rejectLegacyInbound) return null'), processor.index('val messageId = extractMessageId(text)'))
        inbound_test = main.parent.parent.parent.parent.parent / 'test/java/com/localcut/editor/bridge/agent/ExperimentalInboundBlockTest.kt'
        self.assertEqual(inbound_test.read_text(), (self.root / 'scripts/templates/experimental_inbound_block_test.kt.txt').read_text())
        self.assertIn('BridgeCapability.SEEK', inbound_test.read_text())
        self.assertIn('assertEquals(0, playback.seeks)', inbound_test.read_text())
        self.assertIn('assertEquals(1, playback.seeks)', inbound_test.read_text())
        control = (main / 'bridge/agent/LocalCutBridgeControl.kt').read_text()
        for name in ('private fun connect(forceRefreshCredential', 'fun claimPendingPairingFromMobile', 'fun approveOAuthRequest', 'fun approveRemoteControlRequest', 'fun revokePairing'):
            self.assertIn('EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED', control[control.index(name):control.index(name) + 450])
        service = (main / 'bridge/agent/BridgeConnectionService.kt').read_text()
        self.assertIn('return START_NOT_STICKY', service[service.index('override fun onStartCommand'):service.index('override fun onStartCommand') + 250])
        self.assertIn('fun reconnectIfNeeded(): Boolean = false', (main / 'bridge/agent/BridgeColdStartRecovery.kt').read_text())
        self.assertIn('newCall(', (tests / 'LegacyTestOnlyOkHttpBridgePairingClient.kt').read_text())
        self.assertIn('newWebSocket(', (tests / 'LegacyTestOnlyOkHttpBridgeSocketClient.kt').read_text())
        self.assertTrue((tests / 'ExperimentalBridgeBlockTest.kt').exists())
        self.assertFalse(any('LegacyTestOnly' in x.read_text() for x in main.rglob('*.kt')))

    def test_unknown_source_rejected_without_partial_bridge_transform(self):
        p = self.root / 'work/app/src/main/java/com/localcut/editor/bridge/transport/OkHttpBridgeSocketClient.kt'
        original = p.read_text() + '\n// changed\n'
        p.write_text(original)
        pairing = p.parent / 'OkHttpBridgePairingClient.kt'
        before = pairing.read_text()
        with self.assertRaisesRegex(RuntimeError, 'unexpected source'):
            self.run_prepare()
        self.assertEqual(p.read_text(), original)
        self.assertEqual(pairing.read_text(), before)

    def test_new_bridge_network_path_rejected(self):
        p = self.root / 'work/app/src/main/java/com/localcut/editor/bridge/transport/NewClient.kt'
        p.write_text('package com.localcut.editor.bridge.transport\nimport okhttp3.OkHttpClient\n')
        with self.assertRaisesRegex(RuntimeError, 'review new network path'):
            self.run_prepare()

    def test_new_processor_path_rejected_before_bridge_writes(self):
        main = self.root / 'work/app/src/main/java/com/localcut/editor'
        pairing = main / 'bridge/transport/OkHttpBridgePairingClient.kt'
        before = pairing.read_bytes()
        (main / 'NewEntry.kt').write_text('package com.localcut.editor\nval x: BridgeMessageProcessor? = null\n')
        with self.assertRaisesRegex(RuntimeError, 'review new processor path'):
            self.run_prepare()
        self.assertEqual(pairing.read_bytes(), before)

    def test_unreviewed_client_or_endpoint_policy_rejected_without_bridge_writes(self):
        main = self.root / 'work/app/src/main/java/com/localcut/editor'
        pairing = main / 'bridge/transport/OkHttpBridgePairingClient.kt'
        before = pairing.read_bytes()
        p = main / 'bridge/agent/BridgeEndpointPreference.kt'
        p.write_text(p.read_text() + '\n// unreviewed modification\n')
        with self.assertRaisesRegex(RuntimeError, 'unreviewed bridge source set'):
            self.run_prepare()
        self.assertEqual(before, pairing.read_bytes())

    def test_background_manager_preserves_credentials_before_renewal(self):
        self.run_prepare()
        main = self.root / 'work/app/src/main/java/com/localcut/editor'
        manager = (main / 'bridge/agent/BridgeBackgroundCredentialManager.kt').read_text()
        self.assertLess(manager.index('throw IllegalStateException("EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED")'), manager.index('credentialStore.load()'))
        control = (main / 'bridge/agent/LocalCutBridgeControl.kt').read_text()
        revoke = control[control.index('    fun revokePairing()'):]
        self.assertLess(revoke.index('não foi revogado no servidor'), revoke.index('endpointPreference.current()'))
        test_dir = self.root / 'work/app/src/test/java/com/localcut/editor/bridge/agent'
        self.assertTrue((test_dir / 'LegacyTestOnlyBridgeBackgroundCredentialManager.kt').exists())
        self.assertIn('LegacyTestOnlyBridgeBackgroundCredentialManager(', (test_dir / 'BridgeBackgroundCredentialManagerTest.kt').read_text())

    def test_non_experimental_app_rejected(self):
        p = self.root / 'work/app/build.gradle.kts'
        p.write_text(p.read_text().replace('com.localcut.editor.toqiexperiment', 'com.localcut.editor'))
        with self.assertRaisesRegex(AssertionError, 'Experimental app ID missing'):
            self.run_prepare()

if __name__ == '__main__':
    unittest.main()
