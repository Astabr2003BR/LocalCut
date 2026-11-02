package com.localcut.editor.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePairingHttpClientPolicyTest {
    @Test
    fun defaultClientToleratesFreeRelayColdStart() {
        val client = BridgePairingHttpClientPolicy.newClient()

        assertEquals(20_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        assertEquals(75_000, client.callTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }
}
