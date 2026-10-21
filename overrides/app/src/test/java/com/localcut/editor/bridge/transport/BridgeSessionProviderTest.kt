package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class BridgeSessionProviderTest {
    private val identity = DevicePublicIdentity(
        deviceId = "lc_device",
        algorithm = "EC_P256",
        publicKeySpkiBase64 = "AQID",
    )

    @Test
    fun validStoredCredentialCreatesWssSessionWithoutExposingTokenInToString() {
        val provider = StoredBridgeSessionProvider(
            relayUrl = "wss://bridge.example/ws",
            identityStore = FakeIdentityStore(identity),
            credentialStore = FakeCredentialStore(BridgeStoredCredential("secret-token", "refresh", 20_000L)),
            nowEpochMs = { 10_000L },
        )

        val session = provider.currentSession()

        assertEquals("lc_device", session!!.deviceId)
        assertEquals("secret-token", session.accessToken)
        assertFalse(session.toString().contains("secret-token"))
    }

    @Test
    fun missingOrExpiredCredentialDoesNotCreateSession() {
        val missing = StoredBridgeSessionProvider(
            relayUrl = "wss://bridge.example/ws",
            identityStore = FakeIdentityStore(identity),
            credentialStore = FakeCredentialStore(null),
            nowEpochMs = { 10_000L },
        )
        val expired = StoredBridgeSessionProvider(
            relayUrl = "wss://bridge.example/ws",
            identityStore = FakeIdentityStore(identity),
            credentialStore = FakeCredentialStore(BridgeStoredCredential("token", null, 9_999L)),
            nowEpochMs = { 10_000L },
        )

        assertNull(missing.currentSession())
        assertNull(expired.currentSession())
    }

    private class FakeIdentityStore(private val identity: DevicePublicIdentity) : DeviceIdentityStore {
        override fun getOrCreatePublicIdentity(): DevicePublicIdentity = identity
        override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(1)
    }

    private class FakeCredentialStore(private val value: BridgeStoredCredential?) : BridgeCredentialStore {
        override fun save(credential: BridgeStoredCredential) = error("unused")
        override fun load(): BridgeStoredCredential? = value
        override fun clear() = Unit
    }
}
