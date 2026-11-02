package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.bridge.transport.BridgeMobilePairingClaim
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStart
import com.localcut.editor.bridge.transport.BridgePairingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeOAuthCredentialBootstrapTest {
    @Test
    fun pendingPairingSelfClaimsWithDeviceProofAndWaitsForCredential() = kotlinx.coroutines.runBlocking {
        var now = 1_000L
        val store = FakeCredentialStore()
        val client = FakeClient()
        val bootstrap = BridgeOAuthCredentialBootstrap(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = store,
            pairingCodeProvider = { "188666" },
            startPairing = { error("pairing already started") },
            nowEpochMs = { now },
            timeoutMs = 5_000L,
            pollDelayMs = 100L,
            pollDelay = {
                now += it
                if (store.load() == null) {
                    store.save(BridgeStoredCredential("access", "refresh", 9_999L))
                }
            },
        )

        val credential = bootstrap.ensureCredential()

        assertEquals("access", credential.accessToken)
        assertEquals(listOf("188666"), client.mobileClaims)
    }

    @Test
    fun OAuthBootstrapStartsPairingWhenNoCodeExistsYet() = kotlinx.coroutines.runBlocking {
        var now = 1_000L
        var pairingCode: String? = null
        var startCalls = 0
        val store = FakeCredentialStore()
        val client = FakeClient()
        val bootstrap = BridgeOAuthCredentialBootstrap(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = store,
            pairingCodeProvider = { pairingCode },
            startPairing = {
                startCalls += 1
                pairingCode = "654321"
            },
            nowEpochMs = { now },
            timeoutMs = 5_000L,
            pollDelayMs = 100L,
            pollDelay = {
                now += it
                if (client.mobileClaims.isNotEmpty() && store.load() == null) {
                    store.save(BridgeStoredCredential("bootstrapped", "refresh", 9_999L))
                }
            },
        )

        val credential = bootstrap.ensureCredential()

        assertEquals(1, startCalls)
        assertEquals(listOf("654321"), client.mobileClaims)
        assertEquals("bootstrapped", credential.accessToken)
    }

    @Test
    fun existingCredentialSkipsPairingBootstrap() = kotlinx.coroutines.runBlocking {
        val store = FakeCredentialStore().apply {
            save(BridgeStoredCredential("existing", "refresh", 9_999L))
        }
        var startCalls = 0
        val client = FakeClient()
        val bootstrap = BridgeOAuthCredentialBootstrap(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = store,
            pairingCodeProvider = { null },
            startPairing = { startCalls += 1 },
            nowEpochMs = { 1_000L },
            pollDelay = { },
        )

        val credential = bootstrap.ensureCredential()

        assertEquals("existing", credential.accessToken)
        assertEquals(0, startCalls)
        assertTrue(client.mobileClaims.isEmpty())
    }

    @Test
    fun alreadyClaimedPairingIsRecoverableWhileCredentialCompletes() = kotlinx.coroutines.runBlocking {
        var now = 1_000L
        val store = FakeCredentialStore()
        val client = FakeClient(claimFailure = IllegalStateException("PAIRING_ALREADY_CLAIMED"))
        val bootstrap = BridgeOAuthCredentialBootstrap(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = store,
            pairingCodeProvider = { "188666" },
            startPairing = { },
            nowEpochMs = { now },
            timeoutMs = 5_000L,
            pollDelayMs = 100L,
            pollDelay = {
                now += it
                store.save(BridgeStoredCredential("access-after-claim", "refresh", 9_999L))
            },
        )

        val result = runCatching { bootstrap.ensureCredential() }

        assertTrue(result.isSuccess)
        assertFalse(client.mobileClaims.isEmpty())
    }

    private class FakeIdentityStore : DeviceIdentityStore {
        override fun getOrCreatePublicIdentity() = DevicePublicIdentity("lc_device", "EC_P256", "AQID")
        override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(9, 8, 7)
    }

    private class FakeCredentialStore : BridgeCredentialStore {
        private var value: BridgeStoredCredential? = null
        override fun save(credential: BridgeStoredCredential) { value = credential }
        override fun load(): BridgeStoredCredential? = value
        override fun clear() { value = null }
    }

    private class FakeClient(
        private val claimFailure: Throwable? = null,
    ) : BridgePairingClient {
        val mobileClaims = mutableListOf<String>()

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.failure(IllegalStateException("unused"))

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> =
            Result.failure(IllegalStateException("unused"))

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> =
            Result.failure(IllegalStateException("unused"))

        override fun mobileClaim(proof: com.localcut.editor.bridge.security.BridgeMobilePairingProof): Result<BridgeMobilePairingClaim> {
            mobileClaims += proof.code
            claimFailure?.let { return Result.failure(it) }
            return Result.success(
                BridgeMobilePairingClaim(
                    deviceId = "lc_device",
                    challengeBase64 = "AQID",
                    expiresAtEpochMs = 60_000L,
                ),
            )
        }
    }
}
