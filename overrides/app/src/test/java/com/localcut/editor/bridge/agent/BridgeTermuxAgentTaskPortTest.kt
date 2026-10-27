package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTermuxAgentTaskPortTest {
    @Test
    fun submitIsNonBlockingAndResultCanBeReadByTaskId() {
        val queued = mutableListOf<() -> Unit>()
        val client = object : BridgeTermuxAgentClient {
            override fun execute(taskId: String, request: BridgeTermuxAgentRequest): Result<String> =
                Result.success("{\"ok\":true,\"requestId\":\"$taskId\"}")
        }
        val port = AsyncBridgeTermuxAgentPort(
            client = client,
            launcher = BridgeBackgroundTaskLauncher { block -> queued += block },
        )

        val submitted = port.submit(BridgeTermuxAgentRequest.ReadWorkspaceFile("README.md"))

        assertEquals(BridgeTermuxAgentTaskState.QUEUED, submitted.state)
        assertEquals(1, queued.size)
        assertEquals(BridgeTermuxAgentTaskState.QUEUED, port.get(submitted.taskId)?.state)

        queued.single().invoke()
        val done = port.get(submitted.taskId)
        assertNotNull(done)
        assertEquals(BridgeTermuxAgentTaskState.SUCCEEDED, done!!.state)
        assertTrue(done.resultJson!!.contains("\"ok\":true"))
    }

    @Test
    fun arbitraryExecIsRejectedBeforeClientCanRun() {
        val result = runCatching { BridgeTermuxAgentRequest.RunAgentOperation("EXEC") }
        assertTrue(result.isFailure)
    }
}
