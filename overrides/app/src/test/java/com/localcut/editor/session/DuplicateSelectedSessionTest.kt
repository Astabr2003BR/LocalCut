package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSelectedSessionTest {
    private class ImmediatePreview : ProjectPreviewLoader {
        override fun load(project: Project): Result<Unit> = Result.success(Unit)
    }

    private class NoopStore : ProjectStore {
        override fun save(project: Project): Result<Unit> = Result.success(Unit)
        override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
    }

    private class NoopScheduler : DelayScheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
    }

    private fun project(): Project {
        val asset = Asset("a1", AssetType.VIDEO, "content://a1", 10_000_000, 1, "video.mp4", "video/mp4", "fp")
        return Project.create("Teste", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    "visual-1",
                    TrackType.VISUAL,
                    0,
                    clips = listOf(
                        Clip("c1", "a1", 0, 2_000_000, 0, 2_000_000),
                        Clip("c2", "a1", 3_000_000, 1_000_000, 3_000_000, 4_000_000),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun session(): ProjectSession {
        val store = NoopStore()
        return ProjectSession(
            initialProject = project(),
            preview = ImmediatePreview(),
            autosave = DebouncedProjectSaver(store, NoopScheduler(), 300),
        )
    }

    @Test
    fun duplicateSelectedCreatesRippleCopyAndSelectsIt() {
        val session = session()
        session.dispatch(EditorAction.SelectClip("c1"))

        session.dispatch(EditorAction.DuplicateSelected)

        assertEquals("c1-copy", session.state.selectedClipId)
        assertEquals(2_000_000L, session.state.project.findClip("c1-copy")!!.startUs)
        assertEquals(5_000_000L, session.state.project.findClip("c2")!!.startUs)
        assertTrue(session.state.canUndo)
        assertFalse(session.state.canRedo)
        assertNull(session.state.lastError)
    }

    @Test
    fun duplicateSelectedUndoRedoRestoresProjectWithoutStaleSelection() {
        val session = session()
        session.dispatch(EditorAction.SelectClip("c1"))
        session.dispatch(EditorAction.DuplicateSelected)

        session.dispatch(EditorAction.Undo)
        assertNull(session.state.project.findClip("c1-copy"))
        assertEquals(3_000_000L, session.state.project.findClip("c2")!!.startUs)
        assertNull(session.state.selectedClipId)
        assertTrue(session.state.canRedo)

        session.dispatch(EditorAction.Redo)
        assertEquals(2_000_000L, session.state.project.findClip("c1-copy")!!.startUs)
        assertEquals(5_000_000L, session.state.project.findClip("c2")!!.startUs)
        assertNull(session.state.selectedClipId)
    }
}
