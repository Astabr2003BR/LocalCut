package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOverlayImportSessionTest {
    @Test
    fun overlayImportSelectsCreatedClipAndTrackAndIsUndoable() {
        val session = newSession()
        val asset = imageAsset("asset")
        val clip = imageClip("overlay", asset.id, 1_000_000L, 2_000_000L)

        session.dispatch(EditorAction.AddImportedOverlayClip(asset, clip))

        val overlayTrack = session.state.project.tracks.single {
            it.type == TrackType.VISUAL && it.collisionPolicy == TrackCollisionPolicy.OVERLAY
        }
        assertEquals(clip.id, session.state.selectedClipId)
        assertEquals(overlayTrack.id, session.state.selectedTrackId)
        assertNotNull(session.state.project.findClip(clip.id))

        session.dispatch(EditorAction.Undo)
        assertNull(session.state.project.findClip(clip.id))
        assertNull(session.state.selectedClipId)
        assertNull(session.state.selectedTrackId)

        session.dispatch(EditorAction.Redo)
        assertNotNull(session.state.project.findClip(clip.id))
    }

    @Test
    fun audioOverlayImportDoesNotMutateProject() {
        val session = newSession()
        val before = session.state.project
        val asset = Asset(
            id = "audio",
            type = AssetType.AUDIO,
            uri = "content://audio",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "audio.m4a",
            mimeType = "audio/mp4",
            fingerprint = "audio",
        )
        val clip = imageClip("audio-clip", asset.id, 0L, 2_000_000L)

        session.dispatch(EditorAction.AddImportedOverlayClip(asset, clip))

        assertEquals(before, session.state.project)
        assertNotNull(session.state.lastError)
        assertTrue(!session.state.canUndo)
    }

    private fun newSession(): ProjectSession = ProjectSession(
        initialProject = Project.create("Overlay", 1080, 1920, 30),
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
    )

    private fun imageAsset(id: String): Asset = Asset(
        id = id,
        type = AssetType.IMAGE,
        uri = "content://$id",
        sourceDurationUs = null,
        sizeBytes = 1L,
        displayName = "$id.png",
        mimeType = "image/png",
        fingerprint = id,
    )

    private fun imageClip(id: String, assetId: String, startUs: Long, durationUs: Long): Clip = Clip(
        id = id,
        assetId = assetId,
        startUs = startUs,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )
}
