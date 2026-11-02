package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
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
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePreviewFrameCommandTest {
    @Test
    fun previewFrameReturnsBoundedImageMetadataWithoutMutatingProject() {
        val frameBytes = byteArrayOf(1, 2, 3, 4)
        val fixture = fixture(
            preview = BridgePreviewFramePort {
                Result.success(
                    BridgePreviewFrame(
                        mimeType = "image/jpeg",
                        bytes = frameBytes,
                        width = 640,
                        height = 360,
                        positionUs = 2_500_000L,
                    ),
                )
            },
        )

        val result = fixture.executor.execute(command())

        assertTrue(result.success)
        assertEquals(5L, result.projectRevision)
        assertTrue(result.resultJson.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(result.resultJson.contains("\"base64\":\"${Base64.getEncoder().encodeToString(frameBytes)}\""))
        assertTrue(result.resultJson.contains("\"width\":640"))
        assertTrue(result.resultJson.contains("\"height\":360"))
        assertTrue(result.resultJson.contains("\"positionUs\":2500000"))
        assertEquals(5L, fixture.session.state.revision)
    }

    @Test
    fun previewFrameFailureIsControlledAndRetryable() {
        val fixture = fixture(
            preview = BridgePreviewFramePort {
                Result.failure(IllegalStateException("preview surface unavailable"))
            },
        )

        val result = fixture.executor.execute(command())

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.PREVIEW_FAILED, result.code)
        assertTrue(result.retryable)
        assertEquals(5L, fixture.session.state.revision)
    }

    private data class Fixture(
        val session: ProjectSession,
        val executor: BridgeCommandExecutor,
    )

    private fun fixture(preview: BridgePreviewFramePort): Fixture {
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
        return Fixture(
            session = session,
            executor = BridgeCommandExecutor(
                session = session,
                playback = playback,
                previewFramePort = preview,
            ),
        )
    }

    private fun command(): BridgeCommandEnvelope = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "preview-1",
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = null,
        issuedAtEpochMs = 1L,
        expiresAtEpochMs = 10_000L,
        nonce = "nonce-1",
        capability = BridgeCapability.GET_PREVIEW_FRAME,
        payloadJson = "{}",
    )
}
