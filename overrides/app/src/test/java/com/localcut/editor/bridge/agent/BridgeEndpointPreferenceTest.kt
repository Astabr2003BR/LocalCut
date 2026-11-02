package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.InMemoryBridgeCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeEndpointPreferenceTest {
    @Test
    fun unpairedDeviceCanPersistNormalizedHttpsRelay() {
        val settings = InMemoryBridgeSettingsStore()
        val credentials = InMemoryBridgeCredentialStore()
        val preference = BridgeEndpointPreference(settings, credentials)

        val endpoint = preference.save("  https://Bridge.Example.com/ ").getOrThrow()

        assertEquals("https://bridge.example.com", endpoint.httpsBaseUrl)
        assertEquals("https://bridge.example.com", settings.relayBaseUrl)
    }

    @Test
    fun invalidRelayDoesNotOverwriteExistingPreference() {
        val settings = InMemoryBridgeSettingsStore(relayBaseUrl = "https://bridge.example.com")
        val preference = BridgeEndpointPreference(settings, InMemoryBridgeCredentialStore())

        val result = preference.save("http://insecure.example.com")

        assertTrue(result.isFailure)
        assertEquals("https://bridge.example.com", settings.relayBaseUrl)
    }

    @Test
    fun pairedDeviceCannotSwitchRelayAndLeakCredential() {
        val settings = InMemoryBridgeSettingsStore(relayBaseUrl = "https://bridge.example.com")
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(
                BridgeStoredCredential(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresAtEpochMs = 10_000L,
                ),
            )
        }
        val preference = BridgeEndpointPreference(settings, credentials)

        val result = preference.save("https://other.example.com")

        assertTrue(result.isFailure)
        assertEquals("https://bridge.example.com", settings.relayBaseUrl)
    }

    @Test
    fun pairedDeviceCanResaveSameNormalizedRelayAsNoOp() {
        val settings = InMemoryBridgeSettingsStore(relayBaseUrl = "https://bridge.example.com")
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(
                BridgeStoredCredential(
                    accessToken = "access-token",
                    refreshToken = null,
                    expiresAtEpochMs = 10_000L,
                ),
            )
        }
        val preference = BridgeEndpointPreference(settings, credentials)

        val endpoint = preference.save("https://BRIDGE.example.com/").getOrThrow()

        assertEquals("https://bridge.example.com", endpoint.httpsBaseUrl)
        assertEquals("https://bridge.example.com", settings.relayBaseUrl)
    }
}
