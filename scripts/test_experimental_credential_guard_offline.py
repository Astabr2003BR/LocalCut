"""Offline verification against the actual staged Experimental sources."""
import importlib.util
from pathlib import Path
import unittest

p = Path(__file__).with_name('test_experimental_legacy_bridge_offline.py')
spec = importlib.util.spec_from_file_location('legacy_fixture', p)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

class CredentialGuardPreparationTest(fixture.PrepareTest):
    def test_guard_is_staged_without_destructive_reads(self):
        self.run_prepare()
        main = self.root / 'work/app/src/main/java/com/localcut/editor/bridge'
        store = (main / 'security/AndroidKeystoreBridgeCredentialStore.kt').read_text()
        load = store.split('override fun load()', 1)[1].split('override fun clear()', 1)[0]
        self.assertNotIn('clear()', load)
        self.assertNotIn('getOrCreateSecretKey', load)
        self.assertIn('preferences.contains(KEY_IV)', load)
        self.assertIn('preferences.contains(KEY_CIPHERTEXT)', load)
        helper = (main / 'security/ExperimentalCredentialRead.kt').read_text()
        self.assertIn('!ivPresent && !ciphertextPresent', helper)
        self.assertIn('PAIRING_STATE_UNKNOWN', helper)
        endpoint = (main / 'agent/BridgeEndpointPreference.kt').read_text()
        self.assertIn('current?.httpsBaseUrl == endpoint.httpsBaseUrl', endpoint)
        self.assertIn('catch (_: Exception)', endpoint)
        control = (main / 'agent/LocalCutBridgeControl.kt').read_text()
        self.assertIn('paired = try { credentialStore.load() != null } catch (_: Exception) { true }', control)
        test = self.root / 'work/app/src/test/java/com/localcut/editor/bridge/agent/ExperimentalCredentialGuardTest.kt'
        self.assertTrue(test.exists())

if __name__ == '__main__':
    unittest.main()
