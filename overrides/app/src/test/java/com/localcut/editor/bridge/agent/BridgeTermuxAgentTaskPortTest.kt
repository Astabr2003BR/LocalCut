package com.localcut.editor.bridge.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTermuxAgentTaskPortTest {
    @Test
    fun submitReturnsQueuedImmediatelyAndPublishesResult() = runBlocking {
        var calls = 0
        val client = object : BridgeTermuxDeveloperClient {
            override suspend fun execute(
                request: BridgeTermuxAgentRequest,
                taskId: String,
            ): Result<String> {
                calls++
                return Result.success("{\"ok\":true,\"operation\":\"${request.operation}\"}")
            }
        }
        var now = 1_000L
        val port = AsyncBridgeTermuxAgentTaskPort(
            scope = this,
            client = client,
            dispatcher = Dispatchers.Unconfined,
            nowEpochMs = { ++now },
            taskIdFactory = { "task-12345678" },
        )

        val queued = port.submit(BridgeTermuxAgentRequest.GetWorkspaceStatus)
        val finished = requireNotNull(port.get(queued.taskId))

        assertEquals(BridgeTermuxAgentTaskState.QUEUED, queued.state)
        assertEquals(BridgeTermuxAgentTaskState.SUCCEEDED, finished.state)
        assertTrue(finished.resultJson.orEmpty().contains("GET_WORKSPACE_STATUS"))
        assertEquals(1, calls)
    }

    @Test
    fun clientFailureBecomesTypedFailedTask() = runBlocking {
        val client = object : BridgeTermuxDeveloperClient {
            override suspend fun execute(
                request: BridgeTermuxAgentRequest,
                taskId: String,
            ): Result<String> = Result.failure(IllegalStateException("TERMUX_ENGINE_NOT_PAIRED"))
        }
        val port = AsyncBridgeTermuxAgentTaskPort(
            scope = this,
            client = client,
            dispatcher = Dispatchers.Unconfined,
            taskIdFactory = { "task-87654321" },
        )

        val queued = port.submit(BridgeTermuxAgentRequest.GetWorkspaceStatus)
        val finished = requireNotNull(port.get(queued.taskId))

        assertEquals(BridgeTermuxAgentTaskState.FAILED, finished.state)
        assertEquals("TERMUX_ENGINE_NOT_PAIRED", finished.errorCode)
    }
}
