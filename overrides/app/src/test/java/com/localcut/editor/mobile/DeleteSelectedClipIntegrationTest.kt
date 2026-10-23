package com.localcut.editor.mobile

import com.localcut.editor.bridge.agent.BridgeCommandExecutor
import com.localcut.editor.bridge.agent.BridgePlaybackPort
import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
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
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSelectedClipIntegrationTest {
    @Test
    fun selectedVisualDeleteKeepsAudioAndUndoRestoresVisual() {
        val fixture = fixture()
        fixture.session.dispatch(EditorAction.SelectClip("visual-clip"))
        val handoff = MobileHandoffCommand(
            capability = BridgeCapability.DELETE_CLIP,
            payloadJson = "{\"selectedClip\":true}",
            description = "Excluir o clipe selecionado",
            resolveSelectedClip = true,
        )
        val payload = SelectedClipDeleteResolver
            .resolvePayload(handoff, fixture.session.state.selectedClipId)
            .getOrThrow()

        val deleted = fixture.executor.execute(
            command(
                capability = BridgeCapability.DELETE_CLIP,
                payload = payload,
                expectedRevision = 5L,
            ),
        )

        assertTrue(deleted.success)
        assertEquals(6L, fixture.session.state.revision)
        assertNull(fixture.session.state.project.findClip("visual-clip"))
        assertNotNull(fixture.session.state.project.findClip("audio-clip"))

        val undone = fixture.executor.execute(
            command(
                capability = BridgeCapability.UNDO,
                messageId = "undo-1",
                expectedRevision = 6L,
            ),
        )

        assertTrue(undone.success)
        assertEquals(7L, fixture.session.state.revision)
        assertNotNull(fixture.session.state.project.findClip("visual-clip"))
        assertNotNull(fixture.session.state.project.findClip("audio-clip"))
    }

    @Test
    fun missingSelectionFailsBeforeMutationAndKeepsRevision() {
        val fixture = fixture()
        val before = fixture.session.state.project
        val handoff = MobileHandoffCommand(
            capability = BridgeCapability.DELETE_CLIP,
            payloadJson = "{\"selectedClip\":true}",
            description = "Excluir o clipe selecionado",
            resolveSelectedClip = true,
        )

        val result = SelectedClipDeleteResolver.resolvePayload(handoff, fixture.session.state.selectedClipId)

        assertTrue(result.isFailure)
        assertEquals(5L, fixture.session.state.revision)
        assertEquals(before, fixture.session.state.project)
    }

    private data class Fixture(
        val session: ProjectSession,
        val executor: BridgeCommandExecutor,
    )

    private fun fixture(): Fixture {
        val videoAsset = Asset(
            id = "video-asset",
            type = AssetType.VIDEO,
            uri = "content://private/video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video-fp",
        )
        val audioAsset = Asset(
            id = "audio-asset",
            type = AssetType.AUDIO,
            uri = "content://private/audio",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 50L,
            displayName = "audio.m4a",
            mimeType = "audio/mp4",
            fingerprint = "audio-fp",
        )
        val project = Project.create("Delete selected", 1080, 1920, 30).copy(
            id = "project-delete-selected",
            assets = listOf(videoAsset, audioAsset),
            tracks = listOf(
                Track(
                    id = "visual-track",
                    type = TrackType.VISUAL,
                    order = 0,
                    clips = listOf(Clip("visual-clip", videoAsset.id, 0L, 4_000_000L, 0L, 4_000_000L)),
                ),
                Track(
                    id = "audio-track",
                    type = TrackType.AUDIO,
                    order = 1,
                    clips = listOf(Clip("audio-clip", audioAsset.id, 0L, 4_000_000L, 0L, 4_000_000L)),
                ),
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
        return Fixture(
            session = session,
            executor = BridgeCommandExecutor(session, FakePlayback()),
        )
    }

    private class FakePlayback : BridgePlaybackPort {
        override val positionUs: Long = 0L
        override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
    }

    private fun command(
        capability: BridgeCapability,
        payload: String = "{}",
        messageId: String = "delete-1",
        expectedRevision: Long,
    ): BridgeCommandEnvelope = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = messageId,
        kind = "command",
        deviceId = "local-android-handoff",
        projectId = "project-delete-selected",
        expectedRevision = if (capability.requiresRevision) expectedRevision else null,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 61_000L,
        nonce = "nonce-$messageId",
        capability = capability,
        payloadJson = payload,
    )
}
