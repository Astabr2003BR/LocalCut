package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTaskRemovalPolicyTest {
    @Test
    fun preservesCurrentConnectionWithoutImmediateReconnectWhenEnabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = true,
            alwaysConnected = true,
        )

        assertTrue(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }

    @Test
    fun doesNotPreserveWhenSessionIsDisabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = false,
            alwaysConnected = true,
        )

        assertFalse(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }

    @Test
    fun doesNotPreserveWhenAlwaysConnectedIsDisabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = true,
            alwaysConnected = false,
        )

        assertFalse(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }

    @Test
    fun staleDisconnectCannotDisableAnEnabledSession() {
        val decision = BridgeSessionPersistencePolicy.disconnectDecision(
            sessionEnabled = true,
            explicitUserRequest = false,
        )

        assertFalse(decision.honorDisconnect)
        assertFalse(decision.disableSession)
    }

    @Test
    fun explicitUserDisconnectCanDisableAnEnabledSession() {
        val decision = BridgeSessionPersistencePolicy.disconnectDecision(
            sessionEnabled = true,
            explicitUserRequest = true,
        )

        assertTrue(decision.honorDisconnect)
        assertTrue(decision.disableSession)
    }

    @Test
    fun alreadyDisabledSessionCanFinishTransportShutdown() {
        val decision = BridgeSessionPersistencePolicy.disconnectDecision(
            sessionEnabled = false,
            explicitUserRequest = false,
        )

        assertTrue(decision.honorDisconnect)
        assertFalse(decision.disableSession)
    }

    @Test
    fun automaticCredentialLossNeverDisablesUserSessionIntent() {
        assertFalse(BridgeSessionPersistencePolicy.disableSessionOnAutomaticCredentialLoss())
    }
}
