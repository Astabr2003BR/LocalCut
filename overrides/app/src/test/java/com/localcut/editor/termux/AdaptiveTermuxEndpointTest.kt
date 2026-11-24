package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTermuxEndpointTest {
    @Test
    fun agentFallsBackToAlternativeLoopbackWithoutChangingMediaEndpoints() {
        val media = TermuxEngineEndpointResolver.candidates()
        val agent = TermuxEngineEndpointResolver.agentCandidates()
        assertEquals(media.first(), agent.first())
        assertEquals(TermuxEngineProtocol.DEFAULT_HOST, agent[1].host)
        assertEquals(TermuxEngineProtocol.DEFAULT_PORT, agent[1].mediaPort)
        assertEquals(43129, agent[1].agentPort)
        assertEquals(media.drop(1), agent.drop(2))
        assertTrue(media.none { it.agentPort == 43129 })
    }

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
