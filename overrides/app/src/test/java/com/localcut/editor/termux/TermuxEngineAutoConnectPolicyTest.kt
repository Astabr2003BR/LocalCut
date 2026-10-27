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
}
