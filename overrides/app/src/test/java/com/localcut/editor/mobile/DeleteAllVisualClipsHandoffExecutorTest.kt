package com.localcut.editor.mobile

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAllVisualClipsHandoffExecutorTest {
    @Test
    fun clearsRemovedSelectionPreservesAudioAndUndoRestoresVisualProject() {
        val session = session(withVisual = true)
        session.dispatch(EditorAction.SelectClip("visual-clip"))

        assertTrue(DeleteAllVisualClipsHandoffExecutor.execute(session).getOrThrow())
        assertNull(session.state.selectedClipId)
        assertNull(session.state.project.findClip("visual-clip"))
        assertTrue(session.state.project.findClip("audio-clip") != null)

        session.dispatch(EditorAction.Undo)
        assertTrue(session.state.project.findClip("visual-clip") != null)
        assertTrue(session.state.project.findClip("audio-clip") != null)
    }

    @Test
    fun audioOnlyProjectIsNoOpWithoutRevisionChange() {
        val session = session(withVisual = false)
        val revision = session.state.revision

        assertFalse(DeleteAllVisualClipsHandoffExecutor.execute(session).getOrThrow())
        assertEquals(revision, session.state.revision)
        assertTrue(session.state.project.findClip("audio-clip") != null)
    }

    private fun session(withVisual: Boolean): ProjectSession {
        val video = Asset("video", AssetType.VIDEO, "content://video", 8_000_000L, 1L, "v.mp4", "video/mp4", "v")
        val audio = Asset("audio", AssetType.AUDIO, "content://audio", 8_000_000L, 1L, "a.m4a", "audio/mp4", "a")
        val visualClips = if (withVisual) listOf(Clip("visual-clip", "video", 0L, 2_000_000L, 0L, 2_000_000L)) else emptyList()
        val project = Project.create("handoff", 1080, 1920, 30).copy(
            id = "project-bulk-delete",
            assets = listOf(video, audio),
            tracks = listOf(
                Track("visual", TrackType.VISUAL, 0, clips = visualClips),
                Track("audio", TrackType.AUDIO, 1, clips = listOf(Clip("audio-clip", "audio", 0L, 2_000_000L, 0L, 2_000_000L))),
            ),
        )
        return ProjectSession(
            initialProject = project,
            preview = object : ProjectPreviewLoader { override fun load(project: Project): Result<Unit> = Result.success(Unit) },
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
            revisionClock = InMemoryProjectRevisionClock(mapOf(project.id to 10L)),
        )
    }
}
