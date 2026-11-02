package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.bridge.transport.BridgeMobilePairingClaim
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStart
import com.localcut.editor.bridge.transport.BridgePairingStatus
import com.localcut.editor.bridge.transport.BridgeSessionRecoveryProof
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCredentialRecoveryTest {
    @Test
    fun authRefreshFailureUsesSignedRecoveryAndReturnsFreshCredential() {
        val current = BridgeStoredCredential(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochMs = 1_000L,
            recoveryToken = "recovery-token-1",
        )
        val fresh = BridgeStoredCredential(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            expiresAtEpochMs = 9_000L,
            recoveryToken = "recovery-token-2",
        )
        val client = FakePairingClient(
            refreshResult = Result.failure(IllegalStateException("AUTH_REQUIRED")),
            recoveryResult = Result.success(fresh),
        )
        val identity = CapturingIdentityStore()

        val result = BridgeCredentialRenewal(
            client = client,
            identityStore = identity,
            nowEpochMs = { 2_000L },
            nonceGenerator = { ByteArray(32) { 9 } },
        ).renew(current)

        assertEquals(fresh, result.getOrThrow())
        val proof = requireNotNull(client.lastRecoveryProof)
        assertEquals("lc_test_device", proof.deviceId)
        assertEquals("recovery-token-1", proof.recoveryToken)
        assertEquals(2_000L, proof.issuedAtEpochMs)
        assertTrue(identity.lastSignedPayload?.toString(Charsets.UTF_8)?.startsWith("LOCALCUT_SESSION_RECOVERY_V1\nlc_test_device\n2000\n") == true)
        assertTrue(identity.lastSignedPayload?.toString(Charsets.UTF_8)?.endsWith("\nrecovery-token-1") == true)
    }

    @Test
    fun transientRefreshFailureDoesNotDiscardCredentialOrStartRecovery() {
        val current = BridgeStoredCredential(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochMs = 1_000L,
            recoveryToken = "recovery-token-1",
        )
        val client = FakePairingClient(
            refreshResult = Result.failure(IOException("network timeout")),
            recoveryResult = Result.failure(IllegalStateException("must not be called")),
        )

        val result = BridgeCredentialRenewal(
            client = client,
            identityStore = CapturingIdentityStore(),
            nowEpochMs = { 2_000L },
            nonceGenerator = { ByteArray(32) { 7 } },
        ).renew(current)

        assertTrue(result.isFailure)
        assertNull(client.lastRecoveryProof)
    }

    @Test
    fun invalidRecoveryTokenFallsBackToManualPairingInsteadOfLoopingForever() {
        val current = BridgeStoredCredential(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochMs = 1_000L,
            recoveryToken = "recovery-token-1",
        )
        val client = FakePairingClient(
            refreshResult = Result.failure(IllegalStateException("AUTH_REQUIRED")),
            recoveryResult = Result.failure(IllegalStateException("RECOVERY_TOKEN_INVALID")),
        )

        val result = BridgeCredentialRenewal(
            client = client,
            identityStore = CapturingIdentityStore(),
            nowEpochMs = { 2_000L },
            nonceGenerator = { ByteArray(32) { 5 } },
        ).renew(current)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    private class CapturingIdentityStore : DeviceIdentityStore {
        var lastSignedPayload: ByteArray? = null

        override fun getOrCreatePublicIdentity(): DevicePublicIdentity = DevicePublicIdentity(
            deviceId = "lc_test_device",
            algorithm = "EC_P256",
            publicKeySpkiBase64 = "AQID",
        )

        override fun signChallenge(challenge: ByteArray): ByteArray {
            lastSignedPayload = challenge.copyOf()
            return byteArrayOf(1, 2, 3, 4)
        }
    }

    private class FakePairingClient(
        private val refreshResult: Result<BridgeStoredCredential>,
        private val recoveryResult: Result<BridgeStoredCredential>,
    ) : BridgePairingClient {
        var lastRecoveryProof: BridgeSessionRecoveryProof? = null

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.failure(UnsupportedOperationException())

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> =
            Result.failure(UnsupportedOperationException())

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> = refreshResult

        override fun recover(proof: BridgeSessionRecoveryProof): Result<BridgeStoredCredential> {
            lastRecoveryProof = proof
            return recoveryResult
        }

        override fun mobileClaim(proof: com.localcut.editor.bridge.security.BridgeMobilePairingProof): Result<BridgeMobilePairingClaim> =
            Result.failure(UnsupportedOperationException())
    }
}
