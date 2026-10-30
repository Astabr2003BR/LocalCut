package com.localcut.editor.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeEndpointConfigTest {
    @Test
    fun httpsRelayBaseUrlNormalizesAndDerivesSecureWebSocketRoute() {
        val endpoint = BridgeEndpointConfig.resolve("  https://bridge.example.com/  ").getOrThrow()

        assertEquals("https://bridge.example.com", endpoint.httpsBaseUrl)
        assertEquals("wss://bridge.example.com/v1/device/connect", endpoint.wssUrl)
    }

    @Test
    fun explicitHttpsPortIsPreserved() {
        val endpoint = BridgeEndpointConfig.resolve("https://bridge.example.com:8443").getOrThrow()

        assertEquals("https://bridge.example.com:8443", endpoint.httpsBaseUrl)
        assertEquals("wss://bridge.example.com:8443/v1/device/connect", endpoint.wssUrl)
    }

    @Test
    fun insecureOrAmbiguousRelayUrlsAreRejected() {
        val invalid = listOf(
            "",
            "http://bridge.example.com",
            "ws://bridge.example.com",
            "wss://bridge.example.com",
            "https://user:pass@bridge.example.com",
            "https://bridge.example.com/api",
            "https://bridge.example.com?token=secret",
            "https://bridge.example.com/#fragment",
        )

        invalid.forEach { value ->
            assertTrue("expected invalid relay URL: $value", BridgeEndpointConfig.resolve(value).isFailure)
        }
    }
}
