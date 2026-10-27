package com.localcut.editor.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineAutoConnectPolicyTest {
    @Test
    fun pairedOfflineStateAutoProbesExactlyOncePerInitialState() {
        assertTrue(TermuxEngineAutoConnectPolicy.shouldProbe(TermuxEngineUiState.initial(paired = true)))
        assertFalse(TermuxEngineAutoConnectPolicy.shouldProbe(TermuxEngineUiState.initial(paired = false)))
        assertFalse(
            TermuxEngineAutoConnectPolicy.shouldProbe(
                TermuxEngineUiState.initial(paired = true).pairing(),
            ),
        )
        assertFalse(
            TermuxEngineAutoConnectPolicy.shouldProbe(
                TermuxEngineUiState.initial(paired = true).connected("0.1.0", setOf("PING")),
            ),
        )
    }

    @Test
    fun pairedEngineIsHealthCheckedWhenConnectedOrOfflineButNotDuringManualConnect() {
        assertTrue(
            TermuxEngineAutoConnectPolicy.shouldHealthCheck(
                TermuxEngineUiState.initial(paired = true).connected("0.4.0", setOf("PING")),
            ),
        )
        assertTrue(
            TermuxEngineAutoConnectPolicy.shouldHealthCheck(
                TermuxEngineUiState.initial(paired = true).failed("TERMUX_ENGINE_UNAVAILABLE"),
            ),
        )
        assertFalse(
            TermuxEngineAutoConnectPolicy.shouldHealthCheck(
                TermuxEngineUiState.initial(paired = false),
            ),
        )
        assertFalse(
            TermuxEngineAutoConnectPolicy.shouldHealthCheck(
                TermuxEngineUiState.initial(paired = true).pairing(),
            ),
        )
    }

    @Test
    fun healthCheckIntervalIsBounded() {
        assertTrue(TermuxEngineAutoConnectPolicy.HEALTH_CHECK_INTERVAL_MS in 15_000L..60_000L)
    }
}
