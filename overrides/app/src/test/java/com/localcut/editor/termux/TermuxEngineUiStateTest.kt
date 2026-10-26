package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineUiStateTest {
    @Test
    fun initialStatesExposeSafePrimaryAction() {
        val notPaired = TermuxEngineUiState.initial(paired = false)
        assertEquals(TermuxEngineUiPhase.NOT_PAIRED, notPaired.phase)
        assertFalse(notPaired.paired)
        assertEquals("Conectar Termux", notPaired.actionLabel)

        val paired = TermuxEngineUiState.initial(paired = true)
        assertEquals(TermuxEngineUiPhase.PAIRED_OFFLINE, paired.phase)
        assertTrue(paired.paired)
        assertEquals("Testar conexão", paired.actionLabel)
    }

    @Test
    fun connectingAndConnectedStatesKeepPairingSemantics() {
        val connecting = TermuxEngineUiState.initial(paired = false).pairing()
        assertEquals(TermuxEngineUiPhase.CONNECTING, connecting.phase)
        assertTrue(connecting.busy)
        assertFalse(connecting.paired)

        val connected = connecting.connected(
            engineVersion = "0.2.0",
            capabilities = setOf("PING", "GET_STATUS", "GET_CAPABILITIES"),
        )
        assertEquals(TermuxEngineUiPhase.CONNECTED, connected.phase)
        assertTrue(connected.paired)
        assertFalse(connected.busy)
        assertEquals("Termux Agent Engine conectado", connected.statusText)
        assertEquals("Testar conexão", connected.actionLabel)
    }

    @Test
    fun failurePreservesWhetherAValidPairingAlreadyExisted() {
        val pairingFailure = TermuxEngineUiState.initial(paired = false)
            .pairing()
            .failed("PAIRING_INVALID")
        assertFalse(pairingFailure.paired)
        assertEquals("Tentar novamente", pairingFailure.actionLabel)

        val availabilityFailure = TermuxEngineUiState.initial(paired = true)
            .pairing()
            .failed("TERMUX_ENGINE_UNAVAILABLE")
        assertTrue(availabilityFailure.paired)
        assertEquals("Testar conexão", availabilityFailure.actionLabel)
    }
}
