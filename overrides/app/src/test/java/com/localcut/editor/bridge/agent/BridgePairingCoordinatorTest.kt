package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStart
import com.localcut.editor.bridge.transport.BridgePairingStatus
import java.io.IOException
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePairingCoordinatorTest {
    @Test
    fun pendingThenClaimedSignsChallengeStoresCredentialAndEnablesSession() {
        val credentialStore = FakeCredentialStore()
        val settings = InMemoryBridgeSettingsStore()
        val client = FakeClient(
            mutableListOf(
                BridgePairingStatus("PENDING"),
                BridgePairingStatus("CLAIMED", challengeBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))),
            ),
        )
        val events = mutableListOf<BridgeUiEvent>()
        val coordinator = BridgePairingCoordinator(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = credentialStore,
            settingsStore = settings,
            nowEpochMs = { 1_000L },
            pollDelay = { },
        )

        val result = kotlinx.coroutines.runBlocking { coordinator.pair(events::add) }

        assertTrue(result.isSuccess)
        assertEquals("123456", (events.first() as BridgeUiEvent.Pairing).code)
        assertTrue(events.last() is BridgeUiEvent.Connecting)
        assertTrue(settings.sessionEnabled)
        assertNotNull(credentialStore.value)
        assertEquals(1, client.signedStatusCalls)
    }

    @Test
    fun expiredPairingReturnsErrorWithoutSavingCredential() {
        val credentialStore = FakeCredentialStore()
        val events = mutableListOf<BridgeUiEvent>()
        val coordinator = BridgePairingCoordinator(
            client = FakeClient(mutableListOf(BridgePairingStatus("EXPIRED"))),
            identityStore = FakeIdentityStore(),
            credentialStore = credentialStore,
            settingsStore = InMemoryBridgeSettingsStore(),
            nowEpochMs = { 1_000L },
            pollDelay = { },
        )

        val result = kotlinx.coroutines.runBlocking { coordinator.pair(events::add) }

        assertTrue(result.isFailure)
        assertTrue(events.last() is BridgeUiEvent.Error)
        assertEquals(null, credentialStore.value)
    }

    @Test
    fun transientStatusNetworkFailureKeepsSamePairingCodeAndRecovers() {
        val credentialStore = FakeCredentialStore()
        val settings = InMemoryBridgeSettingsStore()
        val client = TransientNetworkClient()
        val events = mutableListOf<BridgeUiEvent>()
        val coordinator = BridgePairingCoordinator(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = credentialStore,
            settingsStore = settings,
            nowEpochMs = { 1_000L },
            pollDelay = { },
        )

        val result = kotlinx.coroutines.runBlocking { coordinator.pair(events::add) }

        assertTrue("temporary DNS/network failure must not abort pending pairing", result.isSuccess)
        assertEquals(1, client.startCalls)
        assertEquals(listOf("123456", "123456"), client.statusCodes)
        assertEquals(1, events.filterIsInstance<BridgeUiEvent.Pairing>().size)
        assertEquals("123456", events.filterIsInstance<BridgeUiEvent.Pairing>().single().code)
        assertNotNull(credentialStore.value)
        assertTrue(settings.sessionEnabled)
    }

    @Test
    fun semanticStatusFailureStillStopsPairing() {
        val credentialStore = FakeCredentialStore()
        val client = SemanticFailureClient()
        val events = mutableListOf<BridgeUiEvent>()
        val coordinator = BridgePairingCoordinator(
            client = client,
            identityStore = FakeIdentityStore(),
            credentialStore = credentialStore,
            settingsStore = InMemoryBridgeSettingsStore(),
            nowEpochMs = { 1_000L },
            pollDelay = { },
        )

        val result = kotlinx.coroutines.runBlocking { coordinator.pair(events::add) }

        assertTrue(result.isFailure)
        assertEquals(1, client.statusCalls)
        assertTrue(events.last() is BridgeUiEvent.Error)
        assertEquals(null, credentialStore.value)
    }

    private class FakeIdentityStore : DeviceIdentityStore {
        override fun getOrCreatePublicIdentity() = DevicePublicIdentity("lc_device", "EC_P256", "AQID")
        override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(9, 8, 7)
    }

    private class FakeCredentialStore : BridgeCredentialStore {
        var value: BridgeStoredCredential? = null
        override fun save(credential: BridgeStoredCredential) { value = credential }
        override fun load(): BridgeStoredCredential? = value
        override fun clear() { value = null }
    }

    private class FakeClient(
        private val statuses: MutableList<BridgePairingStatus>,
    ) : BridgePairingClient {
        var signedStatusCalls = 0

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.success(BridgePairingStart("123456", 601_000L))

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> {
            if (signatureBase64 != null) {
                signedStatusCalls += 1
                return Result.success(
                    BridgePairingStatus(
                        state = "PAIRED",
                        credential = BridgeStoredCredential("access-token", "refresh-token", 3_601_000L),
                    ),
                )
            }
            return Result.success(statuses.removeAt(0))
        }

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> =
            Result.failure(IllegalStateException("unused"))
    }

    private class TransientNetworkClient : BridgePairingClient {
        var startCalls = 0
        val statusCodes = mutableListOf<String>()
        private var statusCalls = 0

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> {
            startCalls += 1
            return Result.success(BridgePairingStart("123456", 601_000L))
        }

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> {
            statusCodes += code
            statusCalls += 1
            if (statusCalls == 1) {
                return Result.failure(IOException("Unable to resolve host"))
            }
            return Result.success(
                BridgePairingStatus(
                    state = "PAIRED",
                    credential = BridgeStoredCredential("access-after-dns", "refresh", 3_601_000L),
                ),
            )
        }

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> =
            Result.failure(IllegalStateException("unused"))
    }

    private class SemanticFailureClient : BridgePairingClient {
        var statusCalls = 0

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.success(BridgePairingStart("123456", 601_000L))

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> {
            statusCalls += 1
            return Result.failure(IllegalStateException("BRIDGE_HTTP_400"))
        }

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> =
            Result.failure(IllegalStateException("unused"))
    }
}
