package com.localcut.editor.termux

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineUiSessionTest {
    @Test
    fun successfulPairingProbesStatusAndCapabilities() = runBlocking {
        var cleared = false
        val session = TermuxEngineUiSession(
            pairAction = { Result.success(Unit) },
            pingAction = { Result.success(Unit) },
            statusAction = {
                Result.success(
                    TermuxEngineStatus(
                        protocolVersion = 1,
                        engineVersion = "0.2.0",
                        projectId = "localcut",
                        status = "READY",
                        pid = 123L,
                    ),
                )
            },
            capabilitiesAction = {
                Result.success(
                    TermuxEngineCapabilities(
                        protocolVersion = 1,
                        capabilities = setOf("PING", "GET_STATUS", "GET_CAPABILITIES"),
                    ),
                )
            },
            clearToken = { cleared = true },
        )

        val result = session.pair(
            "2345-6789-ABCD",
            TermuxEngineUiState.initial(paired = false).pairing(),
        )

        assertTrue(result.paired)
        assertEquals(TermuxEngineUiPhase.CONNECTED, result.phase)
        assertEquals("0.2.0", result.engineVersion)
        assertFalse(cleared)
    }

    @Test
    fun authInvalidClearsRejectedCredentialAndAllowsPairingAgain() = runBlocking {
        var cleared = false
        val session = TermuxEngineUiSession(
            pairAction = { Result.success(Unit) },
            pingAction = { Result.failure(IllegalStateException("AUTH_INVALID")) },
            statusAction = { error("status must not run") },
            capabilitiesAction = { error("capabilities must not run") },
            clearToken = { cleared = true },
        )

        val result = session.check(TermuxEngineUiState.initial(paired = true).pairing())

        assertTrue(cleared)
        assertFalse(result.paired)
        assertEquals("AUTH_INVALID", result.errorCode)
        assertEquals("Tentar novamente", result.actionLabel)
    }

    @Test
    fun temporaryUnavailableKeepsExistingPairing() = runBlocking {
        var cleared = false
        val session = TermuxEngineUiSession(
            pairAction = { Result.success(Unit) },
            pingAction = { Result.failure(IllegalStateException("TERMUX_ENGINE_UNAVAILABLE")) },
            statusAction = { error("status must not run") },
            capabilitiesAction = { error("capabilities must not run") },
            clearToken = { cleared = true },
        )

        val result = session.check(TermuxEngineUiState.initial(paired = true).pairing())

        assertFalse(cleared)
        assertTrue(result.paired)
        assertEquals("TERMUX_ENGINE_UNAVAILABLE", result.errorCode)
        assertEquals("Testar conexão", result.actionLabel)
    }
}
