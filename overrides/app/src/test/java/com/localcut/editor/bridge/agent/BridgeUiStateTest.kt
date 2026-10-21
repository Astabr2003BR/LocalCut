package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeUiStateTest {
    @Test
    fun connectionLifecycleUsesExpectedUserFacingPhases() {
        var state = BridgeUiState()
        state = state.reduce(BridgeUiEvent.Pairing("482913"))
        assertEquals(BridgeUiPhase.PAIRING, state.phase)
        assertEquals("482913", state.pairingCode)

        state = state.reduce(BridgeUiEvent.Connecting)
        assertEquals(BridgeUiPhase.CONNECTING, state.phase)

        state = state.reduce(BridgeUiEvent.Connected)
        assertEquals(BridgeUiPhase.ONLINE, state.phase)
        assertTrue(state.paired)

        state = state.reduce(BridgeUiEvent.Editing)
        assertEquals(BridgeUiPhase.EDITING, state.phase)

        state = state.reduce(BridgeUiEvent.Exporting(42))
        assertEquals(BridgeUiPhase.EXPORTING, state.phase)
        assertEquals(42, state.exportProgressPercent)

        state = state.reduce(BridgeUiEvent.Disconnected)
        assertEquals(BridgeUiPhase.OFFLINE, state.phase)
        assertTrue(state.paired)
    }

    @Test
    fun manualRevokeReturnsToUnpairedOfflineAndPreservesRelay() {
        val state = BridgeUiState(
            phase = BridgeUiPhase.ONLINE,
            paired = true,
            alwaysConnected = true,
            relayBaseUrl = "https://bridge.example.com",
        ).reduce(BridgeUiEvent.PairingRevoked)

        assertEquals(BridgeUiPhase.OFFLINE, state.phase)
        assertFalse(state.paired)
        assertFalse(state.alwaysConnected)
        assertEquals("https://bridge.example.com", state.relayBaseUrl)
    }

    @Test
    fun relayChangeUpdatesValueWithoutChangingPhase() {
        val state = BridgeUiState(phase = BridgeUiPhase.OFFLINE)
            .reduce(BridgeUiEvent.RelayBaseUrlChanged("https://bridge.example.com"))

        assertEquals(BridgeUiPhase.OFFLINE, state.phase)
        assertEquals("https://bridge.example.com", state.relayBaseUrl)
    }

    @Test
    fun errorMessageRedactsTokensAndUris() {
        val state = BridgeUiState().reduce(
            BridgeUiEvent.Error("Bearer secret-token content://private/video file:///data/user/0/app/token"),
        )

        assertEquals(BridgeUiPhase.ERROR, state.phase)
        assertFalse(state.message!!.contains("secret-token"))
        assertFalse(state.message!!.contains("content://"))
        assertFalse(state.message!!.contains("file://"))
    }

    @Test
    fun alwaysConnectedPreferenceCanChangeWithoutChangingPhase() {
        val state = BridgeUiState(phase = BridgeUiPhase.ONLINE, paired = true)
            .reduce(BridgeUiEvent.AlwaysConnectedChanged(true))

        assertEquals(BridgeUiPhase.ONLINE, state.phase)
        assertTrue(state.alwaysConnected)
    }
}
