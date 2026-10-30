package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.InMemoryBridgeCredentialStore
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStart
import com.localcut.editor.bridge.transport.BridgePairingStatus
import com.localcut.editor.bridge.security.DevicePublicIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeUnpairCoordinatorTest {
    @Test
    fun validAccessTokenIsUnpairedRemotelyBeforeLocalStateIsCleared() {
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(BridgeStoredCredential("access-1", "refresh-1", 10_000L))
        }
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        val client = FakePairingClient()
        val coordinator = BridgeUnpairCoordinator(client, credentials, settings, nowEpochMs = { 5_000L })

        val result = coordinator.unpair()

        assertTrue(result.isSuccess)
        assertEquals(listOf("access-1"), client.unpairTokens)
        assertNull(credentials.load())
        assertFalse(settings.sessionEnabled)
        assertFalse(settings.alwaysConnected)
    }

    @Test
    fun expiredAccessRefreshesBeforeUnpairing() {
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(BridgeStoredCredential("expired-access", "refresh-1", 4_000L))
        }
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        val client = FakePairingClient(
            refreshed = BridgeStoredCredential("access-2", "refresh-2", 20_000L),
        )
        val coordinator = BridgeUnpairCoordinator(client, credentials, settings, nowEpochMs = { 5_000L })

        val result = coordinator.unpair()

        assertTrue(result.isSuccess)
        assertEquals(listOf("refresh-1"), client.refreshTokens)
        assertEquals(listOf("access-2"), client.unpairTokens)
        assertNull(credentials.load())
    }

    @Test
    fun staleAccessRetriesThroughRefreshWhenRelayRejectsIt() {
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(BridgeStoredCredential("access-1", "refresh-1", 10_000L))
        }
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        val client = FakePairingClient(
            refreshed = BridgeStoredCredential("access-2", "refresh-2", 20_000L),
            firstUnpairError = "AUTH_REQUIRED",
        )
        val coordinator = BridgeUnpairCoordinator(client, credentials, settings, nowEpochMs = { 5_000L })

        val result = coordinator.unpair()

        assertTrue(result.isSuccess)
        assertEquals(listOf("access-1", "access-2"), client.unpairTokens)
        assertEquals(listOf("refresh-1"), client.refreshTokens)
        assertNull(credentials.load())
    }

    @Test
    fun transportFailureKeepsCredentialAndConnectionPreferencesForRetry() {
        val original = BridgeStoredCredential("access-1", "refresh-1", 10_000L)
        val credentials = InMemoryBridgeCredentialStore().apply { save(original) }
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        val client = FakePairingClient(firstUnpairError = "NETWORK_UNAVAILABLE")
        val coordinator = BridgeUnpairCoordinator(client, credentials, settings, nowEpochMs = { 5_000L })

        val result = coordinator.unpair()

        assertTrue(result.isFailure)
        assertEquals("NETWORK_UNAVAILABLE", result.exceptionOrNull()?.message)
        assertNotNull(credentials.load())
        assertEquals(original, credentials.load())
        assertTrue(settings.sessionEnabled)
        assertTrue(settings.alwaysConnected)
    }

    private class FakePairingClient(
        private val refreshed: BridgeStoredCredential? = null,
        private val firstUnpairError: String? = null,
    ) : BridgePairingClient {
        val refreshTokens = mutableListOf<String>()
        val unpairTokens = mutableListOf<String>()
        private var unpairCount = 0

        override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> =
            Result.failure(UnsupportedOperationException())

        override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> =
            Result.failure(UnsupportedOperationException())

        override fun refresh(refreshToken: String): Result<BridgeStoredCredential> {
            refreshTokens += refreshToken
            val value = refreshed ?: return Result.failure(IllegalStateException("AUTH_REQUIRED"))
            return Result.success(value)
        }

        override fun unpair(accessToken: String): Result<Unit> {
            unpairTokens += accessToken
            unpairCount += 1
            if (unpairCount == 1 && firstUnpairError != null) {
                return Result.failure(IllegalStateException(firstUnpairError))
            }
            return Result.success(Unit)
        }
    }
}
