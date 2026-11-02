package com.localcut.editor.termux

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEnginePairingServiceTest {
    @Test
    fun pairingStopsAfterPersistingTokenAndDoesNotProbeNetwork() = runBlocking {
        var pingCalls = 0
        val token = "t".repeat(43)
        var persistedToken: String? = null
        val client = object : TermuxEngineClient {
            override suspend fun pair(pairingCode: String): Result<TermuxEnginePairing> =
                Result.success(TermuxEnginePairing(protocolVersion = 1, token = token))

            override suspend fun ping(): Result<TermuxEnginePing> {
                pingCalls += 1
                return Result.failure(IllegalStateException("NETWORK_DOWN_AFTER_PAIR"))
            }

            override suspend fun getStatus(): Result<TermuxEngineStatus> = error("not used")
            override suspend fun getCapabilities(): Result<TermuxEngineCapabilities> = error("not used")
        }
        val store = object : TermuxEngineTokenStore {
            override fun save(token: String) {
                persistedToken = token
            }

            override fun load(): String? = persistedToken
            override fun clear() {
                persistedToken = null
            }
        }

        val result = TermuxEnginePairingService(client, store).pair("2345-6789-ABCD")

        assertTrue(result.isSuccess)
        assertEquals(token, persistedToken)
        assertEquals(0, pingCalls)
    }
}
