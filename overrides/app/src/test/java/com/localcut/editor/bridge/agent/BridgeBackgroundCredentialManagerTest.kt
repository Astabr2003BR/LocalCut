package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.bridge.security.InMemoryBridgeCredentialStore
import com.localcut.editor.bridge.transport.BridgeMobilePairingClaim
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStart
import com.localcut.editor.bridge.transport.BridgePairingStatus
import com.localcut.editor.bridge.transport.BridgeSessionRecoveryProof
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeBackgroundCredentialManagerTest {
    @Test
    fun freshCredentialSkipsNetworkAndStaysUsable() {
        val current = credential("access-current", expiresAt = 10_000L)
        val store = InMemoryBridgeCredentialStore().apply { save(current) }
        val client = FakePairingClient()

        val result = BridgeBackgroundCredentialManager(
            credentialStore = store,
            client = client,
            identityStore = FakeIdentityStore(),
            nowEpochMs = { 2_000L },
        ).ensureFresh()

        assertTrue(result.getOrThrow())
        assertEquals(current, store.load())
        assertEquals(0, client.refreshCalls)
    }

    @Test
    fun expiredCredentialRefreshesAndPersistsBeforeReconnect() {
        val current = credential("access-old", expiresAt = 1_000L)
        val fresh = credential("access-new", expiresAt = 9_000L)
        val store = InMemoryBridgeCredentialStore().apply { save(current) }
        val client = FakePairingClient(refreshResult = Result.success(fresh))

        val result = BridgeBackgroundCredentialManager(
            credentialStore = store,
            client = client,
            identityStore = FakeIdentityStore(),
            nowEpochMs = { 2_000L },
        ).ensureFresh()

        assertTrue(result.getOrThrow())
        assertEquals(fresh, store.load())
        assertEquals(1, client.refreshCalls)
    }

    @Test
    fun transientRenewalFailurePreservesStoredCredential() {
        val current = credential("access-old", expiresAt = 1_000L)
        val store = InMemoryBridgeCredentialStore().apply { save(current) }
        val client = FakePairingClient(
            refreshResult = Result.failure(IOException("offline")),
        )

        val result = BridgeBackgroundCredentialManager(
            credentialStore = store,
            client = client,
            identityStore = FakeIdentityStore(),
            nowEpochMs = { 2_000L },
        ).ensureFresh()

        assertTrue(result.isFailure)
        assertEquals(current, store.load())
    }

    @Test
    fun terminalRenewalFailureClearsCredentialAndRequestsPairing() {
        val current = credential("access-old", expiresAt = 1_000L)
        val store = InMemoryBridgeCredentialStore().apply { save(current) }
        val client = FakePairingClient(
            refreshResult = Result.failure(IllegalStateException("REFRESH_TOKEN_INVALID")),
            recoveryResult = Result.failure(IllegalStateException("RECOVERY_TOKEN_INVALID")),
        )

        val result = BridgeBackgroundCredentialManager(
            credentialStore = store,
            client = client,
            identityStore = FakeIdentityStore(),
            nowEpochMs = { 2_000L },
        ).ensureFresh()

        assertFalse(result.getOrThrow())
        assertNull(store.load())
    }

    private fun credential(access: String, expiresAt: Long) = BridgeStoredCredential(
        accessToken = access,
        refreshToken = "refresh-token",
        expiresAtEpochMs = expiresAt,
        recoveryToken = "recovery-token",
    )

    private class FakeIdentityStore : DeviceIdentityStore {
        override fun getOrCreatePublicIdentity(): DevicePublicIdentity = DevicePublicIdentity(
            deviceId = "lc_test_device",
            algorithm = "EC_P256",
            publicKeySpkiBase64 = "AQID",
        )

        override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(1, 2, 3)
    }

    private class FakePairingClient(
        private val refreshResult: Result<BridgeStoredCredential> = Result.failure(UnsupportedOperationException()),
        private val recoveryResult: Result<BridgeStoredCredential> = Result.failure(UnsupportedOperationException()),
    ) : BridgePairingClient {
        var refreshCalls = 0

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.failure(UnsupportedOperationException())

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> =
            Result.failure(UnsupportedOperationException())

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> {
            refreshCalls += 1
            return refreshResult
        }

        override fun recover(proof: BridgeSessionRecoveryProof): Result<BridgeStoredCredential> = recoveryResult

        override fun mobileClaim(proof: com.localcut.editor.bridge.security.BridgeMobilePairingProof): Result<BridgeMobilePairingClaim> =
            Result.failure(UnsupportedOperationException())
    }
}
