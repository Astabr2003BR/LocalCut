package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightTermuxDeveloperAgentClientTest {
    @Test
    fun executesAgentOperationOnceAfterSuccessfulPreflight() = runBlocking {
        var executions = 0
        val delegate = object : BridgeTermuxDeveloperClient {
            override suspend fun execute(request: BridgeTermuxAgentRequest, taskId: String): Result<String> {
                executions += 1
                return Result.success("{\"ok\":true}")
            }
        }
        val engine = FakeEngineClient(Result.success(TermuxEnginePing(1, "READY")))
        val client = PreflightTermuxDeveloperAgentClient(delegate, engine)

        val result = client.execute(BridgeTermuxAgentRequest.GetWorkspaceStatus, "task-12345678")

        assertTrue(result.isSuccess)
        assertEquals(1, engine.pingCalls)
        assertEquals(1, executions)
    }

    @Test
    fun doesNotSendAgentOperationWhenPreflightFails() = runBlocking {
        var executions = 0
        val delegate = object : BridgeTermuxDeveloperClient {
            override suspend fun execute(request: BridgeTermuxAgentRequest, taskId: String): Result<String> {
                executions += 1
                return Result.success("{}")
            }
        }
        val engine = FakeEngineClient(Result.failure(IllegalStateException("TERMUX_ENGINE_UNAVAILABLE")))
        val client = PreflightTermuxDeveloperAgentClient(delegate, engine)

        val result = client.execute(BridgeTermuxAgentRequest.GetWorkspaceStatus, "task-12345678")

        assertTrue(result.isFailure)
        assertEquals(1, engine.pingCalls)
        assertEquals(0, executions)
    }

    private class FakeEngineClient(
        private val pingResult: Result<TermuxEnginePing>,
    ) : TermuxEngineClient {
        var pingCalls = 0
        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping(): Result<TermuxEnginePing> {
            pingCalls += 1
            return pingResult
        }
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")
    }
}
