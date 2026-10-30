package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeAgentConnectionLifecyclePolicyTest {
    @Test
    fun connectionAttemptStartsForegroundBeforePairingOrCredentialRenewal() {
        val transition = BridgeAgentConnectionLifecyclePolicy.beforeConnectAttempt()

        assertEquals(BridgeConnectionService.ACTION_PREPARE_CONNECT, transition.action)
        assertEquals(true, transition.foreground)
    }

    @Test
    fun failedFreshPairingStopsPreparationForegroundService() {
        val transition = BridgeAgentConnectionLifecyclePolicy.afterConnectAttempt(
            sessionEnabled = false,
            credentialPresent = false,
        )

        assertEquals(BridgeConnectionService.ACTION_STOP_PREPARE_CONNECT, transition?.action)
        assertEquals(false, transition?.foreground)
    }

    @Test
    fun establishedOrRecoverableSessionKeepsServiceAlive() {
        assertNull(
            BridgeAgentConnectionLifecyclePolicy.afterConnectAttempt(
                sessionEnabled = true,
                credentialPresent = true,
            ),
        )
        assertNull(
            BridgeAgentConnectionLifecyclePolicy.afterConnectAttempt(
                sessionEnabled = true,
                credentialPresent = false,
            ),
        )
    }
}
