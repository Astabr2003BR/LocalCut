package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCodec
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessageProcessorTermuxTest {
    @Test
    fun reservedTermuxEnvelopeIsInterceptedBeforeTimelineExecutor() {
        val project = Project.create("Bridge", 1080, 1920, 30).copy(id = "project-1")
        val session = ProjectSession(
            initialProject = project,
            preview = object : ProjectPreviewLoader {
                override fun load(project: Project): Result<Unit> = Result.success(Unit)
            },
            autosave = DebouncedProjectSaver(
                store = object : ProjectStore {
                    override fun save(project: Project): Result<Unit> = Result.success(Unit)
                    override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
                },
                scheduler = object : DelayScheduler {
                    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
                },
                delayMs = 300L,
            ),
            revisionClock = InMemoryProjectRevisionClock(mapOf(project.id to 5L)),
        )
        val playback = object : BridgePlaybackPort {
            override val positionUs: Long = 0L
            override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
        }
        var submitted = 0
        val port = object : BridgeTermuxAgentTaskPort {
            override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot {
                submitted++
                return BridgeTermuxAgentTaskSnapshot(
                    taskId = "task-12345678",
                    operation = request.operation,
                    state = BridgeTermuxAgentTaskState.QUEUED,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                )
            }
            override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
        }
        val processor = BridgeMessageProcessor(
            deviceId = "device-1",
            executor = BridgeCommandExecutor(session, playback),
            termuxAgentPort = port,
            activeProjectContextProvider = { BridgeActiveProjectContext("project-1", session.state.revision) },
        )
        val command = BridgeCommandEnvelope(
            protocolVersion = 1,
            messageId = "message-12345678",
            kind = "command",
            deviceId = "device-1",
            projectId = "project-1",
            expectedRevision = null,
            issuedAtEpochMs = 1_000L,
            expiresAtEpochMs = 31_000L,
            nonce = "nonce-12345678",
            capability = BridgeCapability.GET_DEVICE_STATUS,
            payloadJson = """{"localcutTermux":{"version":1,"operation":"GET_WORKSPACE_STATUS"}}""",
        )

        val encoded = requireNotNull(processor.process(BridgeCodec.encodeCommand(command), nowEpochMs = 2_000L))
        val result = BridgeCodec.decodeResult(encoded)

        assertTrue(result.success)
        assertTrue(result.resultJson.contains("QUEUED"))
        assertEquals(1, submitted)
        assertEquals(5L, session.state.revision)
    }
}
