package com.localcut.editor.bridge.agent

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
