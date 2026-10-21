package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeSettingsStoreTest {
    @Test
    fun defaultsToDisconnectedAndAlwaysConnectedOff() {
        val store: BridgeSettingsStore = InMemoryBridgeSettingsStore()

        assertFalse(store.sessionEnabled)
        assertFalse(store.alwaysConnected)
        assertFalse(store.shouldRecoverAfterBoot())
    }

    @Test
    fun modeBSessionCanBeEnabledWithoutModeC() {
        val store: BridgeSettingsStore = InMemoryBridgeSettingsStore()

        store.sessionEnabled = true

        assertTrue(store.sessionEnabled)
        assertFalse(store.alwaysConnected)
        assertFalse(store.shouldRecoverAfterBoot())
    }

    @Test
    fun modeCRequiresExplicitAlwaysConnectedAndExistingSessionIntent() {
        val store: BridgeSettingsStore = InMemoryBridgeSettingsStore()
        store.alwaysConnected = true
        assertFalse(store.shouldRecoverAfterBoot())

        store.sessionEnabled = true
        assertTrue(store.shouldRecoverAfterBoot())

        store.alwaysConnected = false
        assertFalse(store.shouldRecoverAfterBoot())
    }

    @Test
    fun manualDisconnectDisablesSessionAndPreventsBootRecoveryButKeepsUserPreference() {
        val store: BridgeSettingsStore = InMemoryBridgeSettingsStore(
            sessionEnabled = true,
            alwaysConnected = true,
        )

        store.markManualDisconnect()

        assertFalse(store.sessionEnabled)
        assertTrue(store.alwaysConnected)
        assertFalse(store.shouldRecoverAfterBoot())
    }
}
