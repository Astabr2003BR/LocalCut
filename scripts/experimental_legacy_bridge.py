"""Transform only the staged Experimental app; do not use on an approved build.

The three pre-existing transport tests run unchanged in intent against test-only
copies of the original implementations. ExperimentalBridgeBlockTest tests the
actual shipped implementations. No production source or credential is touched.
"""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

BASE = Path('src/main/java/com/localcut/editor')
TEST = Path('src/test/java/com/localcut/editor/bridge/transport')
TEMPLATES = Path('scripts/templates')
EXPECTED = {
    'bridge/transport/OkHttpBridgePairingClient.kt': 'f37e37abc608b3b9503df0c55960f7bf232034149464b4168db5129ce67767e3',
    'bridge/transport/OkHttpBridgeSocketClient.kt': '2f71f1681703b4ba029debd57f1cdbc97323a1bdc6a3372498ae38c88153b35e',
    'bridge/transport/BridgePairingHttpClientPolicy.kt': '331eab056f1dbd828964a8bc5c7d0168661b368445208b08ca2014b09b15f1f4',
    'bridge/agent/LocalCutBridgeControl.kt': '139f7dc903b0dcd8fa9c17075dd2e1545ebb2b6a989df9233bd3cb26a0027ab5',
    'bridge/agent/BridgeConnectionService.kt': '5b23a8a8fa5c5be30115e977c8087e1d7d2470c0acae6c457697dc5542b6cf75',
    'bridge/agent/BridgeColdStartRecovery.kt': 'f97db4e56d1e325ec223897d7426b9c53bf5e95e4890b1b3d989c6fd3f4e38cc',
    'LocalCutProcessRuntime.kt': '602e3f70c4592280bb4b03189752f037fc6e5a241f7c4c5a81763dbb533cb941',
    'bridge/agent/BridgeMessageProcessor.kt': '02d925f1c7ff314765fb0eb78a85a88135e9d9cba480a12f0b8f63d4292fd6d8',
    'bridge/agent/BridgeBackgroundCredentialManager.kt': '138df0eef217b7ccfc1aa26937066b99613059ac1042f00a1670953301dd504b',
}
TESTS = {
    'OkHttpBridgePairingClientRemoteApprovalTest.kt': ('dbe9768f2b1321dd770b378fc47a22bbb2199f8ee21df9c42dad038a42f10859', 'OkHttpBridgePairingClient'),
    'OkHttpBridgeSocketClientAuthTest.kt': ('8a8b02c3951b7ed857c7f02607b71c6536768a35029fcf8f55701b6b3d01ac47', 'OkHttpBridgeSocketClient'),
    'OkHttpBridgeSocketClientImmediateCallbackTest.kt': ('eec050bcecca2ee034bfab60f24c0035bb8c8259b186fd5e2638a98d72d3f3c6', 'OkHttpBridgeSocketClient'),
}

def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()

def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Experimental bridge: unknown or ambiguous source structure')
    return text.replace(old, new, 1)

def prepare(root: Path):
    # Refuse direct use outside the explicitly staged experimental package.
    if root.resolve() != Path('work/app').resolve():
        raise RuntimeError('Experimental bridge: not the staged work/app')
    gradle = (root / 'build.gradle.kts').read_text()
    if 'applicationId = "com.localcut.editor.toqiexperiment"' not in gradle:
        raise RuntimeError('Experimental bridge: experimental application ID missing')
    app = ET.parse(root / 'src/main/AndroidManifest.xml').getroot().find('application')
    if app is None or app.get('{http://schemas.android.com/apk/res/android}label') != 'LocalCut Experimental':
        raise RuntimeError('Experimental bridge: experimental application label missing')
    # All bridge validations and transformations happen in memory before the first write.
    source = {}
    for name, expected in EXPECTED.items():
        text = (root / BASE / name).read_text()
        if sha(text) != expected:
            raise RuntimeError('Experimental bridge: unexpected source ' + name)
        source[name] = text
    tests = {}
    for name, (expected, clazz) in TESTS.items():
        text = (root / TEST / name).read_text()
        if sha(text) != expected:
            raise RuntimeError('Experimental bridge: unexpected test ' + name)
        tests[name] = replace_once(text, clazz + '(', 'LegacyTestOnly' + clazz + '(')

    # Only the reviewed runtime may construct/reference the message processor.
    for path in (root / BASE).rglob('*.kt'):
        rel = path.relative_to(root / BASE).as_posix()
        if rel not in {'LocalCutProcessRuntime.kt', 'bridge/agent/BridgeMessageProcessor.kt'} and re.search(r'\bBridgeMessageProcessor\b', path.read_text()):
            raise RuntimeError('Experimental bridge: review new processor path ' + rel)

    # Reject newly introduced direct network clients in the bridge package.
    pattern = re.compile(r'\b(?:OkHttpClient|WebSocket|Socket|HttpURLConnection|URLConnection|HttpClient|Retrofit|InetAddress)\b|\bURL\s*\(|\.newCall\s*\(|\.newWebSocket\s*\(')
    known = {'bridge/transport/OkHttpBridgePairingClient.kt', 'bridge/transport/OkHttpBridgeSocketClient.kt', 'bridge/transport/BridgePairingHttpClientPolicy.kt', 'bridge/agent/BridgeConnectionService.kt'}
    for path in (root / BASE / 'bridge').rglob('*.kt'):
        rel = path.relative_to(root / BASE).as_posix()
        if rel not in known and pattern.search(path.read_text()):
            raise RuntimeError('Experimental bridge: review new network path ' + rel)

    # Pin all reviewed bridge Kotlin sources to reject unseen factories, aliases,
    # clients and changes in endpoint/credential handling. Cold recovery is pinned separately.
    bridge = root / BASE / 'bridge'
    files = sorted(p for p in bridge.rglob('*.kt') if p.relative_to(bridge).as_posix() != 'agent/BridgeColdStartRecovery.kt')
    fingerprint = hashlib.sha256(b''.join(
        p.relative_to(bridge).as_posix().encode() + b'\0' + hashlib.sha256(p.read_bytes()).digest()
        for p in files
    )).hexdigest()
    if fingerprint != 'ab392cfbe7263fb5b5d1c753eeb945c0cd7f4e4dd5b18a2706ad63a70ab33a6d':
        raise RuntimeError('Experimental bridge: unreviewed bridge source set')

    manager = source['bridge/agent/BridgeBackgroundCredentialManager.kt']
    manager_test = root / 'src/test/java/com/localcut/editor/bridge/agent/BridgeBackgroundCredentialManagerTest.kt'
    manager_test_source = manager_test.read_text()
    if sha(manager_test_source) != '48649d2807ea1cffed6c69f6889b636f8cd77d515142e2c66e31148c60ee612f' or manager_test_source.count('BridgeBackgroundCredentialManager(') != 4:
        raise RuntimeError('Experimental bridge: unexpected credential manager test')

    pair = source['bridge/transport/OkHttpBridgePairingClient.kt']
    sock = source['bridge/transport/OkHttpBridgeSocketClient.kt']
    legacy_pair = replace_once(pair, 'class OkHttpBridgePairingClient(', 'class LegacyTestOnlyOkHttpBridgePairingClient(')
    legacy_sock = replace_once(sock, 'class OkHttpBridgeSocketClient(', 'class LegacyTestOnlyOkHttpBridgeSocketClient(')
    writes = {
        root / BASE / 'bridge/transport/OkHttpBridgePairingClient.kt': (TEMPLATES / 'experimental_blocked_pairing.kt.txt').read_text(),
        root / BASE / 'bridge/transport/OkHttpBridgeSocketClient.kt': (TEMPLATES / 'experimental_blocked_socket.kt.txt').read_text(),
        root / TEST / 'LegacyTestOnlyOkHttpBridgePairingClient.kt': legacy_pair,
        root / TEST / 'LegacyTestOnlyOkHttpBridgeSocketClient.kt': legacy_sock,
        root / TEST / 'ExperimentalBridgeBlockTest.kt': (TEMPLATES / 'experimental_bridge_block_test.kt.txt').read_text(),
        root / 'src/test/java/com/localcut/editor/bridge/agent/ExperimentalInboundBlockTest.kt': (TEMPLATES / 'experimental_inbound_block_test.kt.txt').read_text(),
    }
    manager_copy = root / 'src/test/java/com/localcut/editor/bridge/agent/LegacyTestOnlyBridgeBackgroundCredentialManager.kt'
    if manager_copy.exists() or not manager_copy.parent.is_dir():
        raise RuntimeError('Experimental bridge: unexpected generated test destination')
    writes[manager_copy] = replace_once(manager, 'class BridgeBackgroundCredentialManager(', 'class LegacyTestOnlyBridgeBackgroundCredentialManager(')
    writes[manager_test] = manager_test_source.replace('BridgeBackgroundCredentialManager(', 'LegacyTestOnlyBridgeBackgroundCredentialManager(')
    # Fail before renewal can clear an expired credential lacking recovery material.
    writes[root / BASE / 'bridge/agent/BridgeBackgroundCredentialManager.kt'] = replace_once(
        manager, '    fun ensureFresh(): Result<Boolean> = runCatching {\n',
        '    fun ensureFresh(): Result<Boolean> = runCatching {\n        if ("EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED".isNotEmpty()) throw IllegalStateException("EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED")\n')
    for name, text in tests.items():
        writes[root / TEST / name] = text

    control = source['bridge/agent/LocalCutBridgeControl.kt']
    block = 'if ("EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED".isNotEmpty())'
    control = replace_once(control, '    private fun connect(forceRefreshCredential: Boolean) {\n',
        '    private fun connect(forceRefreshCredential: Boolean) {\n        ' + block + ' { update(BridgeUiEvent.Error("Bridge legado indisponível no Experimental.")); return }\n')
    for marker in ['fun claimPendingPairingFromMobile(code: String, onComplete: (Result<Unit>) -> Unit)',
                   'fun approveOAuthRequest(requestId: String, onComplete: (Result<String>) -> Unit)',
                   'fun approveRemoteControlRequest(requestId: String, onComplete: (Result<Unit>) -> Unit)']:
        control = replace_once(control, '    ' + marker + ' {\n', '    ' + marker + ' {\n        ' + block + ' { onComplete(Result.failure(IllegalStateException("EXPERIMENTAL_LEGACY_BRIDGE_BLOCKED"))); return }\n')
    control = replace_once(control, '    fun revokePairing() {\n',
        '    fun revokePairing() {\n        ' + block + ' { update(BridgeUiEvent.Error("Revogação indisponível: não foi revogado no servidor; credenciais preservadas.")); return }\n')
    writes[root / BASE / 'bridge/agent/LocalCutBridgeControl.kt'] = control

    service = source['bridge/agent/BridgeConnectionService.kt']
    service = replace_once(service, '    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {\n',
        '    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {\n        ' + block + ' { stopSelf(startId); return START_NOT_STICKY }\n')
    writes[root / BASE / 'bridge/agent/BridgeConnectionService.kt'] = service
    cold = source['bridge/agent/BridgeColdStartRecovery.kt']
    marker = '    fun reconnectIfNeeded(): Boolean = BridgeColdStartRecoveryPolicy.attemptRecovery('
    if cold.count(marker) != 1 or not cold.endswith('    }\n}\n'):
        raise RuntimeError('Experimental bridge: unknown cold recovery')
    cold = cold[:cold.index(marker)] + '    fun reconnectIfNeeded(): Boolean = false\n}\n'
    writes[root / BASE / 'bridge/agent/BridgeColdStartRecovery.kt'] = cold

    # Defence in depth: the app's actual inbound handler drops messages without
    # posting a callback. Its processor also refuses work, including any callback
    # which reaches process after being queued. Local mobile handoff uses the
    # executor directly and must remain unaffected.
    runtime = source['LocalCutProcessRuntime.kt']
    runtime = replace_once(runtime, '            deviceId = deviceId,\n            executor = bridgeCommandExecutor,\n',
        '            deviceId = deviceId,\n            executor = bridgeCommandExecutor,\n            rejectLegacyInbound = true,\n')
    runtime = replace_once(runtime, '''        bridgeInboundMessageHandler = BridgeInboundMessageHandler { text, reply ->
            mainHandler.post {
                bridgeMessageProcessor.process(text)?.let(reply)
            }
        }
''', '''        bridgeInboundMessageHandler = BridgeInboundMessageHandler { _, _ ->
            // Experimental: never enqueue or execute a legacy Bridge message.
        }
''')
    writes[root / BASE / 'LocalCutProcessRuntime.kt'] = runtime
    processor = source['bridge/agent/BridgeMessageProcessor.kt']
    processor = replace_once(processor, '    termuxAgentPort: BridgeTermuxAgentTaskPort = UnavailableBridgeTermuxAgentTaskPort,\n',
        '    termuxAgentPort: BridgeTermuxAgentTaskPort = UnavailableBridgeTermuxAgentTaskPort,\n    private val rejectLegacyInbound: Boolean = false,\n')
    processor = replace_once(processor, '    fun process(text: String, nowEpochMs: Long = System.currentTimeMillis()): String? {\n',
        '    fun process(text: String, nowEpochMs: Long = System.currentTimeMillis()): String? {\n        if (rejectLegacyInbound) return null\n')
    writes[root / BASE / 'bridge/agent/BridgeMessageProcessor.kt'] = processor
    generated = [root / TEST / 'LegacyTestOnlyOkHttpBridgePairingClient.kt', root / TEST / 'LegacyTestOnlyOkHttpBridgeSocketClient.kt', root / TEST / 'ExperimentalBridgeBlockTest.kt', root / 'src/test/java/com/localcut/editor/bridge/agent/ExperimentalInboundBlockTest.kt']
    for path in generated:
        if path.exists() or not path.parent.is_dir():
            raise RuntimeError('Experimental bridge: unexpected generated test destination')
    for path, text in writes.items():
        path.write_text(text)
    print('Experimental legacy bridge blocked; original transport regression tests use test-only originals')
