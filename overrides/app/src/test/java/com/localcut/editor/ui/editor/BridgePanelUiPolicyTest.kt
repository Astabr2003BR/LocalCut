package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeUiPhase
import com.localcut.editor.bridge.agent.BridgeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePanelUiPolicyTest {
    @Test
    fun pairingSummaryIncludesShortPairingCode() {
        val state = BridgeUiState(phase = BridgeUiPhase.PAIRING, pairingCode = "447234")

        assertEquals("Pareando • 447234", BridgePanelUiPolicy.summary(state))
    }

    @Test
    fun onlineAndEditingStatesStayCompact() {
        assertEquals("Online", BridgePanelUiPolicy.summary(BridgeUiState(phase = BridgeUiPhase.ONLINE, paired = true)))
        assertEquals("Editando", BridgePanelUiPolicy.summary(BridgeUiState(phase = BridgeUiPhase.EDITING, paired = true)))
    }

    @Test
    fun exportingSummaryCarriesProgressWhenAvailable() {
        val state = BridgeUiState(phase = BridgeUiPhase.EXPORTING, paired = true, exportProgressPercent = 42)

        assertEquals("Exportando • 42%", BridgePanelUiPolicy.summary(state))
    }

    @Test
    fun errorRequestsAttentionButDoesNotForcePermanentExpansion() {
        val state = BridgeUiState(phase = BridgeUiPhase.ERROR, message = "falhou")

        assertTrue(BridgePanelUiPolicy.needsAttention(state))
        assertFalse(BridgePanelUiPolicy.defaultExpanded(state))
    }
}
