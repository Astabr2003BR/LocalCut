"""Fail-closed credential handling for the staged Experimental app only."""
from pathlib import Path
import hashlib

BASE = Path('src/main/java/com/localcut/editor')
TEST = Path('src/test/java/com/localcut/editor/bridge/agent/ExperimentalCredentialGuardTest.kt')
TEMPLATES = Path('scripts/templates')

def _replace(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Experimental credential guard: unexpected source structure')
    return text.replace(old, new, 1)

def prepare(root: Path):
    if root.resolve() != Path('work/app').resolve():
        raise RuntimeError('Experimental credential guard: not staged work/app')
    if 'applicationId = "com.localcut.editor.toqiexperiment"' not in (root / 'build.gradle.kts').read_text():
        raise RuntimeError('Experimental credential guard: wrong app ID')
    store_path = root / BASE / 'bridge/security/AndroidKeystoreBridgeCredentialStore.kt'
    endpoint_path = root / BASE / 'bridge/agent/BridgeEndpointPreference.kt'
    control_path = root / BASE / 'bridge/agent/LocalCutBridgeControl.kt'
    store, endpoint, control = (p.read_text() for p in (store_path, endpoint_path, control_path))
    for text, expected in ((store, 'b7deaf304f680cc108aaa96ed2fffbb25846f3d7b2581ba9c988c150132c0c5b'), (endpoint, '5461330b5d293830e7bf4967feb420661e8260ea460c49d19d9090c503d0ebb1')):
        if hashlib.sha256(text.encode()).hexdigest() != expected:
            raise RuntimeError('Experimental credential guard: unreviewed source')
    store = _replace(store, '    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)', '    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)\n    private val preferencesDirectory = java.io.File(context.applicationInfo.dataDir, "shared_prefs")')
    start = store.index('    override fun load(): BridgeStoredCredential? {')
    end = store.index('    override fun clear()', start)
    store = store[:start] + (TEMPLATES / 'experimental_credential_load.kt.txt').read_text() + '\n' + store[end:]
    endpoint = _replace(endpoint,
        '        if (credentialStore.load() != null && current?.httpsBaseUrl != endpoint.httpsBaseUrl) {',
        '''        // A normalized no-op cannot transfer a credential to a new server.
        if (current?.httpsBaseUrl == endpoint.httpsBaseUrl) return Result.success(endpoint)
        // A failed read is an unknown pairing state, never an absent record.
        val credential = try {
            credentialStore.load()
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("PAIRING_STATE_UNKNOWN"))
        }
        if (credential != null) {''')
    # The legacy guard already pins and transforms this file. Unknown is shown
    # conservatively as paired, avoiding a startup crash or unpaired display.
    control = _replace(control, '            paired = credentialStore.load() != null,',
        '            paired = try { credentialStore.load() != null } catch (_: Exception) { true },')
    control = _replace(control,
        'if (error.message == "PAIRING_MUST_BE_REVOKED_BEFORE_ENDPOINT_CHANGE")',
        'if (error.message == "PAIRING_MUST_BE_REVOKED_BEFORE_ENDPOINT_CHANGE" || error.message == "PAIRING_STATE_UNKNOWN")')
    test_path = root / TEST
    helper_path = root / BASE / 'bridge/security/ExperimentalCredentialRead.kt'
    if any(path.exists() or not path.parent.is_dir() for path in (test_path, helper_path)):
        raise RuntimeError('Experimental credential guard: unexpected destination')
    test = (TEMPLATES / 'experimental_credential_guard_test.kt.txt').read_text()
    helper = (TEMPLATES / 'experimental_credential_read.kt.txt').read_text()
    for path, text in ((store_path, store), (endpoint_path, endpoint), (control_path, control), (test_path, test), (helper_path, helper)):
        path.write_text(text)
    print('Experimental credential guard installed; unknown pairing blocks endpoint change')
