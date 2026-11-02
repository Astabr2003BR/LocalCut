package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCodec
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeActiveContextProbeTest {
    @Test
    fun activeProjectProbeReturnsCurrentRoutingWithoutMutation() {
        val fixture = fixture()

        val encoded = fixture.processor.process(
            BridgeCodec.encodeCommand(
                command(
                    capability = BridgeCapability.GET_DEVICE_STATUS,
                    projectId = ACTIVE_PROJECT_SENTINEL,
                ),
            ),
            nowEpochMs = 1_000L,
        )
        val result = BridgeCodec.decodeResult(requireNotNull(encoded))

        assertTrue(result.success)
        assertEquals(5L, result.projectRevision)
        assertTrue(result.resultJson.contains("\"state\":\"READY\""))
        assertTrue(result.resultJson.contains("\"projectId\":\"project-1\""))
        assertTrue(result.resultJson.contains("\"revision\":5"))
        assertEquals(5L, fixture.session.state.revision)
    }

    @Test
    fun activeProjectSentinelCannotBypassProjectBindingForOtherCapabilities() {
        val fixture = fixture()

        val encoded = fixture.processor.process(
            BridgeCodec.encodeCommand(
                command(
                    capability = BridgeCapability.GET_PROJECT_STATE,
                    projectId = ACTIVE_PROJECT_SENTINEL,
                ),
            ),
            nowEpochMs = 1_000L,
        )
        val result = BridgeCodec.decodeResult(requireNotNull(encoded))

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.PROJECT_NOT_FOUND, result.code)
        assertEquals(5L, fixture.session.state.revision)
    }

    private data class Fixture(
        val session: ProjectSession,
        val processor: BridgeMessageProcessor,
    )

    private fun fixture(): Fixture {
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
        val executor = BridgeCommandExecutor(session, playback)
        val processor = BridgeMessageProcessor(
            deviceId = "device-1",
            executor = executor,
            activeProjectContextProvider = {
                val current = session.state
                BridgeActiveProjectContext(current.project.id, current.revision)
            },
        )
        return Fixture(session, processor)
    }

    private fun command(
        capability: BridgeCapability,
        projectId: String,
    ): BridgeCommandEnvelope = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "probe-${capability.wireName}",
        kind = "command",
        deviceId = "device-1",
        projectId = projectId,
        expectedRevision = null,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-${capability.wireName}",
        capability = capability,
        payloadJson = "{}",
    )

    private companion object {
        const val ACTIVE_PROJECT_SENTINEL = "__localcut_active_project__"
    }
}
