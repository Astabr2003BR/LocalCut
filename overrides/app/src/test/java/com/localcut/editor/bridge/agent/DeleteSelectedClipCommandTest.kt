package com.localcut.editor.bridge.agent

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSelectedClipCommandTest {
    @Test
    fun deleteClipCanResolveCurrentlySelectedClipLocally() {
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
        session.dispatch(EditorAction.SelectClip("clip-1"))
        val executor = BridgeCommandExecutor(
            session = session,
            playback = object : BridgePlaybackPort {
                override val positionUs: Long = 0L
                override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
            },
        )

        val result = executor.execute(
            BridgeCommandEnvelope(
                protocolVersion = 1,
                messageId = "delete-selected-1",
                kind = "command",
                deviceId = "device-1",
                projectId = project.id,
                expectedRevision = 5L,
                issuedAtEpochMs = 1_000L,
                expiresAtEpochMs = 31_000L,
                nonce = "nonce-delete-selected-1",
                capability = BridgeCapability.DELETE_CLIP,
                payloadJson = "{\"selectedClip\":true}",
            )
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        assertNull(session.state.project.findClip("clip-1"))
    }
}
