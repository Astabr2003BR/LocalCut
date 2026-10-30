package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTermuxCommandRouterTest {
    @Test
    fun readEnvelopeIsQueuedWithoutProjectMutationContract() {
        var submitted: BridgeTermuxAgentRequest? = null
        val router = BridgeTermuxCommandRouter(
            port = object : BridgeTermuxAgentTaskPort {
                override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot {
                    submitted = request
                    return snapshot(request.operation)
                }
                override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
            },
            activeProjectContextProvider = { BridgeActiveProjectContext("project-1", 9L) },
        )
        val command = command(
            BridgeCapability.GET_DEVICE_STATUS,
            """{"localcutTermux":{"version":1,"operation":"READ_WORKSPACE_FILE","path":"README.md"}}""",
        )

        val result = router.route(command)

        assertTrue(result.success)
        assertEquals(9L, result.projectRevision)
        assertTrue(result.resultJson.contains("QUEUED"))
        assertEquals("README.md", (submitted as BridgeTermuxAgentRequest.ReadWorkspaceFile).path)
    }

    @Test
    fun writeOperationRequiresWriteCarrierAndActiveProject() {
        val router = BridgeTermuxCommandRouter(
            port = object : BridgeTermuxAgentTaskPort {
                override fun submit(request: BridgeTermuxAgentRequest) = snapshot(request.operation)
                override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
            },
            activeProjectContextProvider = { BridgeActiveProjectContext("project-1", 9L) },
        )
        val payload = """{"localcutTermux":{"version":1,"operation":"APPLY_TEXT_PATCH","path":"README.md","expectedSha256":"${"a".repeat(64)}","oldText":"a","newText":"b"}}"""

        val wrongCarrier = router.route(command(BridgeCapability.GET_DEVICE_STATUS, payload))
        val foreign = router.route(command(BridgeCapability.CANCEL_EXPORT, payload).copy(projectId = "other"))

        assertFalse(wrongCarrier.success)
        assertEquals(BridgeErrorCode.INVALID_ARGUMENT, wrongCarrier.code)
        assertEquals(BridgeErrorCode.PROJECT_NOT_FOUND, foreign.code)
    }

    @Test
    fun commitAndPushAreDedicatedWriteRequests() {
        val submitted = mutableListOf<BridgeTermuxAgentRequest>()
        val router = BridgeTermuxCommandRouter(
            port = object : BridgeTermuxAgentTaskPort {
                override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot {
                    submitted += request
                    return snapshot(request.operation)
                }
                override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
            },
            activeProjectContextProvider = { BridgeActiveProjectContext("project-1", 9L) },
        )
        val branch = "feature/localcut-bridge-v1"
        val commit = router.route(command(
            BridgeCapability.CANCEL_EXPORT,
            """{"localcutTermux":{"version":1,"operation":"COMMIT_WORKSPACE","expectedBranch":"$branch","message":"feat: auto","paths":["README.md"]}}""",
        ))
        val push = router.route(command(
            BridgeCapability.CANCEL_EXPORT,
            """{"localcutTermux":{"version":1,"operation":"PUSH_WORKSPACE","expectedBranch":"$branch"}}""",
        ))

        assertTrue(commit.success)
        assertTrue(push.success)
        val commitRequest = submitted[0] as BridgeTermuxAgentRequest.CommitWorkspace
        assertEquals(branch, commitRequest.expectedBranch)
        assertEquals(listOf("README.md"), commitRequest.paths)
        val pushRequest = submitted[1] as BridgeTermuxAgentRequest.PushWorkspace
        assertEquals(branch, pushRequest.expectedBranch)
    }

    private fun snapshot(operation: String) = BridgeTermuxAgentTaskSnapshot(
        taskId = "task-12345678",
        operation = operation,
        state = BridgeTermuxAgentTaskState.QUEUED,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun command(capability: BridgeCapability, payload: String) = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "message-12345678",
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = null,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-12345678",
        capability = capability,
        payloadJson = payload,
    )
}
