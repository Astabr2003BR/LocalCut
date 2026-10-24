package com.localcut.editor.bridge.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityContractTest {
    @Test
    fun publicIdentityDerivesStableOpaqueDeviceIdFromPublicKey() {
        val publicKey = byteArrayOf(1, 2, 3, 4, 5, 6)

        val first = DevicePublicIdentity.fromPublicKey(publicKey)
        val second = DevicePublicIdentity.fromPublicKey(publicKey.copyOf())
        val other = DevicePublicIdentity.fromPublicKey(byteArrayOf(6, 5, 4, 3, 2, 1))

        assertEquals(first, second)
        assertNotEquals(first.deviceId, other.deviceId)
        assertTrue(first.deviceId.startsWith("lc_"))
        assertEquals("EC_P256", first.algorithm)
    }

    @Test
    fun storedCredentialToStringNeverContainsSecrets() {
        val credential = BridgeStoredCredential(
            accessToken = "access-secret-value",
            refreshToken = "refresh-secret-value",
            expiresAtEpochMs = 99_000L,
        )

        val rendered = credential.toString()
        assertFalse(rendered.contains("access-secret-value"))
        assertFalse(rendered.contains("refresh-secret-value"))
        assertTrue(rendered.contains("redacted"))
    }

    @Test
    fun inMemoryCredentialStoreRoundTripsAndClearsWithoutChangingValue() {
        val store: BridgeCredentialStore = InMemoryBridgeCredentialStore()
        val credential = BridgeStoredCredential("access", "refresh", 123_000L)

        store.save(credential)
        assertEquals(credential, store.load())

        store.clear()
        assertEquals(null, store.load())
    }
}
