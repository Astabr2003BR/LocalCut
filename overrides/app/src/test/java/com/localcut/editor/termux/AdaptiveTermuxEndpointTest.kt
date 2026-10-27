package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTermuxEndpointTest {
    @Test
    fun resolverAlwaysStartsWithLoopbackAndNeverReturnsPublicHosts() {
        val endpoints = TermuxEngineEndpointResolver.candidates()
        assertTrue(endpoints.isNotEmpty())
        assertEquals(TermuxEngineProtocol.DEFAULT_HOST, endpoints.first().host)
        assertEquals(TermuxEngineProtocol.DEFAULT_PORT, endpoints.first().mediaPort)
        endpoints.forEach { endpoint ->
            assertTrue(TermuxEngineEndpointPolicy.isAllowedHost(endpoint.host))
            if (endpoint.host != TermuxEngineProtocol.DEFAULT_HOST) {
                assertEquals(43227, endpoint.mediaPort)
                assertEquals(43228, endpoint.agentPort)
            }
        }
    }
}
