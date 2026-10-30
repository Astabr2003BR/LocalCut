package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendModeSessionHistoryTest {
    @Test
    fun blendModeChangeIsOneUndoableRedoableSessionEdit() {
        val session = ProjectSession(
            initialProject = projectWithClip(),
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
        session.dispatch(EditorAction.SelectClip("clip-1"))
        assertEquals(BlendMode.NORMAL, session.state.project.findClip("clip-1")!!.compositing.blendMode)
        assertFalse(session.state.canUndo)

        session.dispatch(
            EditorAction.SetSelectedCompositing(
                CompositingSettings(blendMode = BlendMode.MULTIPLY),
            ),
        )

        assertEquals(BlendMode.MULTIPLY, session.state.project.findClip("clip-1")!!.compositing.blendMode)
        assertTrue(session.state.canUndo)

        session.dispatch(EditorAction.Undo)
        assertEquals(BlendMode.NORMAL, session.state.project.findClip("clip-1")!!.compositing.blendMode)
        assertTrue(session.state.canRedo)

        session.dispatch(EditorAction.Redo)
        assertEquals(BlendMode.MULTIPLY, session.state.project.findClip("clip-1")!!.compositing.blendMode)
    }

    private fun projectWithClip(): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "blend-history",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
        )
        return Project.create("Blend history", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
