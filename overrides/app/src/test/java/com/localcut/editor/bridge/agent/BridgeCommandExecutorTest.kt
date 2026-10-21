package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
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

class BridgeCommandExecutorTest {
    @Test
    fun projectReadIsBoundedAndDoesNotExposeAssetUri() {
        val fixture = fixture()
        val result = fixture.executor.execute(command(BridgeCapability.GET_PROJECT_STATE, expectedRevision = null))

        assertTrue(result.success)
        assertEquals(5L, result.projectRevision)
        assertTrue(result.resultJson.contains("\"revision\":5"))
        assertTrue(result.resultJson.contains("\"projectId\":\"project-1\""))
        assertFalse(result.resultJson.contains("content://private/video"))
    }

    @Test
    fun matchingRevisionMoveExecutesThroughProjectSessionAndAdvancesRevision() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.MOVE_CLIP,
                payload = "{\"clipId\":\"clip-1\",\"newStartUs\":1000000}",
                expectedRevision = 5L,
            )
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        assertEquals(1_000_000L, fixture.session.state.project.findClip("clip-1")!!.startUs)
    }

    @Test
    fun staleRevisionIsRejectedWithoutMutation() {
        val fixture = fixture()
        fixture.executor.execute(
            command(
                BridgeCapability.MOVE_CLIP,
                payload = "{\"clipId\":\"clip-1\",\"newStartUs\":1000000}",
                expectedRevision = 5L,
            )
        )
        val before = fixture.session.state.project

        val conflict = fixture.executor.execute(
            command(
                BridgeCapability.MOVE_CLIP,
                messageId = "msg-2",
                payload = "{\"clipId\":\"clip-1\",\"newStartUs\":2000000}",
                expectedRevision = 5L,
            )
        )

        assertFalse(conflict.success)
        assertEquals(BridgeErrorCode.REVISION_CONFLICT, conflict.code)
        assertEquals(6L, conflict.projectRevision)
        assertEquals(before, fixture.session.state.project)
    }

    @Test
    fun missingClipReturnsTypedErrorWithoutRevisionChange() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.DELETE_CLIP,
                payload = "{\"clipId\":\"missing\"}",
                expectedRevision = 5L,
            )
        )

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.CLIP_NOT_FOUND, result.code)
        assertEquals(5L, fixture.session.state.revision)
    }

    @Test
    fun invalidMoveArgumentDoesNotMutateProject() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.MOVE_CLIP,
                payload = "{\"clipId\":\"clip-1\",\"newStartUs\":-1}",
                expectedRevision = 5L,
            )
        )

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.INVALID_ARGUMENT, result.code)
        assertEquals(0L, fixture.session.state.project.findClip("clip-1")!!.startUs)
        assertEquals(5L, fixture.session.state.revision)
    }

    @Test
    fun seekChangesPlaybackStateButNotProjectRevision() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.SEEK,
                payload = "{\"positionUs\":2000000}",
                expectedRevision = null,
            )
        )

        assertTrue(result.success)
        assertEquals(2_000_000L, fixture.playback.positionUs)
        assertEquals(5L, fixture.session.state.revision)
    }

    @Test
    fun importWithoutPreviouslyAuthorizedReferenceRequiresLocalInteraction() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.IMPORT_MEDIA,
                payload = "{\"reference\":\"file:///sdcard/secret.mp4\"}",
                expectedRevision = 5L,
            )
        )

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.LOCAL_INTERACTION_REQUIRED, result.code)
        assertEquals(5L, fixture.session.state.revision)
    }

    private data class Fixture(
        val session: ProjectSession,
        val playback: FakePlayback,
        val executor: BridgeCommandExecutor,
    )

    private fun fixture(): Fixture {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://private/video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp-1",
        )
        val clip = Clip("clip-1", asset.id, 0L, 4_000_000L, 0L, 4_000_000L)
        val project = Project.create("Bridge", 1080, 1920, 30).copy(
            id = "project-1",
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
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
        val playback = FakePlayback()
        return Fixture(session, playback, BridgeCommandExecutor(session, playback))
    }

    private class FakePlayback : BridgePlaybackPort {
        override var positionUs: Long = 0L
            private set

        override fun seekToUs(positionUs: Long): Result<Unit> = runCatching {
            require(positionUs >= 0L) { "positionUs must be non-negative" }
            this.positionUs = positionUs
        }
    }

    private fun command(
        capability: BridgeCapability,
        messageId: String = "msg-1",
        payload: String = "{}",
        expectedRevision: Long? = if (capability.requiresRevision) 5L else null,
    ): BridgeCommandEnvelope = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = messageId,
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = expectedRevision,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-$messageId",
        capability = capability,
        payloadJson = payload,
    )
}
