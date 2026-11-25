"""Stage manual-disconnect cold-start protection only in the Experimental app.

The shared overrides remain byte-for-byte at their approved baseline. Both the
production source and its unit test are validated before either is written.
"""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET

FILES = {
    'src/main/java/com/localcut/editor/bridge/agent/BridgeColdStartRecovery.kt': ('67f68b0fad590ef0960c6674cded49ac7e51d4d18584584d3ca7f3eedd4b44c2', r'''package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeCredentialStore

internal object BridgeColdStartRecoveryPolicy {
    fun shouldReconnect(
        paired: Boolean,
        alwaysConnected: Boolean,
        sessionEnabled: Boolean,
    ): Boolean = paired && alwaysConnected && sessionEnabled

    fun attemptRecovery(
        paired: Boolean,
        settingsStore: BridgeSettingsStore,
        startReconnect: () -> Boolean,
    ): Boolean {
        if (!shouldReconnect(paired, settingsStore.alwaysConnected, settingsStore.sessionEnabled)) {
            return false
        }
        // Recovery must never enable a session: only an explicit connect may do that.
        return startReconnect()
    }
}

class BridgeColdStartRecovery(
    context: Context,
    private val settingsStore: BridgeSettingsStore = SharedPreferencesBridgeSettingsStore(context),
    private val credentialStore: BridgeCredentialStore = AndroidKeystoreBridgeCredentialStore(context),
) {
    private val appContext = context.applicationContext

    fun reconnectIfNeeded(): Boolean = BridgeColdStartRecoveryPolicy.attemptRecovery(
        paired = credentialStore.load() != null,
        settingsStore = settingsStore,
    ) {
        val intent = Intent(appContext, BridgeConnectionService::class.java)
            .setAction(BridgeConnectionService.ACTION_RECONNECT)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }
}
'''),
    'src/test/java/com/localcut/editor/bridge/agent/BridgeColdStartRecoveryPolicyTest.kt': ('b68aadb55c8f9c4ab96194372d65bacf1c16a6709c0b806bde6f5e668ec8dbc8', r'''package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeColdStartRecoveryPolicyTest {
    @Test
    fun recoveryRequiresPairingAlwaysConnectedAndEnabledSession() {
        for (paired in listOf(false, true)) {
            for (alwaysConnected in listOf(false, true)) {
                for (sessionEnabled in listOf(false, true)) {
                    val settings = InMemoryBridgeSettingsStore(
                        sessionEnabled = sessionEnabled,
                        alwaysConnected = alwaysConnected,
                    )
                    var starts = 0
                    val result = BridgeColdStartRecoveryPolicy.attemptRecovery(paired, settings) {
                        starts++
                        true
                    }
                    val expected = paired && alwaysConnected && sessionEnabled
                    assertEquals(expected, result)
                    assertEquals(if (expected) 1 else 0, starts)
                    assertEquals(sessionEnabled, settings.sessionEnabled)
                    assertEquals(alwaysConnected, settings.alwaysConnected)
                }
            }
        }
    }

    @Test
    fun manualDisconnectStaysDisabledAcrossColdStart() {
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        settings.markManualDisconnect()
        var starts = 0
        val result = BridgeColdStartRecoveryPolicy.attemptRecovery(paired = true, settingsStore = settings) {
            starts++
            true
        }
        assertFalse(result)
        assertEquals(0, starts)
        assertFalse(settings.sessionEnabled)
        assertTrue(settings.alwaysConnected)
    }

    @Test
    fun explicitlyReenabledSessionCanRecover() {
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        settings.markManualDisconnect()
        // Represents the session being enabled by a separate explicit user connection.
        settings.sessionEnabled = true
        var starts = 0
        val result = BridgeColdStartRecoveryPolicy.attemptRecovery(paired = true, settingsStore = settings) {
            starts++
            true
        }
        assertTrue(result)
        assertEquals(1, starts)
        assertTrue(settings.sessionEnabled)
    }

    @Test
    fun failedServiceStartLeavesDesiredSessionUnchanged() {
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        var starts = 0
        val result = BridgeColdStartRecoveryPolicy.attemptRecovery(paired = true, settingsStore = settings) {
            starts++
            false
        }
        assertFalse(result)
        assertEquals(1, starts)
        assertTrue(settings.sessionEnabled)
        assertTrue(settings.alwaysConnected)
    }
}
'''),
}

def prepare(root: Path):
    if root.resolve() != Path('work/app').resolve():
        raise RuntimeError('Experimental cold recovery: not the staged work/app')
    gradle = (root / 'build.gradle.kts').read_text()
    if 'applicationId = "com.localcut.editor.toqiexperiment"' not in gradle:
        raise RuntimeError('Experimental cold recovery: experimental application ID missing')
    app = ET.parse(root / 'src/main/AndroidManifest.xml').getroot().find('application')
    if app is None or app.get('{http://schemas.android.com/apk/res/android}label') != 'LocalCut Experimental':
        raise RuntimeError('Experimental cold recovery: experimental application label missing')
    writes = []
    for relative, (expected, replacement) in FILES.items():
        path = root / relative
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise RuntimeError('Experimental cold recovery: unexpected source or test ' + relative)
        writes.append((path, replacement))
    for path, replacement in writes:
        path.write_text(replacement)
