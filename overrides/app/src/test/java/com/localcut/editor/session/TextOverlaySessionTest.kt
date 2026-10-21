package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlaySessionTest {
    @Test
    fun addTextOverlayIsUndoableAndRedoable() {
        val session = newSession()
        val overlay = TextOverlay(
            id = "title-1",
            startUs = 500_000L,
            durationUs = 2_000_000L,
            text = "LocalCut",
        )

        session.dispatch(EditorAction.AddTextOverlay(overlay))

        assertEquals(overlay, session.state.project.textOverlays.single())
        assertTrue(session.state.canUndo)

        session.dispatch(EditorAction.Undo)
        assertTrue(session.state.project.textOverlays.isEmpty())
        assertTrue(session.state.canRedo)

        session.dispatch(EditorAction.Redo)
        assertEquals(overlay, session.state.project.textOverlays.single())
    }

    @Test
    fun updateTextOverlayIsUndoable() {
        val original = TextOverlay("title-1", 0L, 2_000_000L, "Antes")
        val session = newSession(Project.create("Text", 1080, 1920, 30).copy(textOverlays = listOf(original)))
        val updated = original.copy(text = "Depois", durationUs = 3_000_000L)

        session.dispatch(EditorAction.UpdateTextOverlay(updated))

        assertEquals(updated, session.state.project.textOverlays.single())
        session.dispatch(EditorAction.Undo)
        assertEquals(original, session.state.project.textOverlays.single())
    }

    @Test
    fun deleteTextOverlayIsUndoable() {
        val overlay = TextOverlay("title-1", 0L, 2_000_000L, "Excluir")
        val session = newSession(Project.create("Text", 1080, 1920, 30).copy(textOverlays = listOf(overlay)))

        session.dispatch(EditorAction.DeleteTextOverlay(overlay.id))

        assertTrue(session.state.project.textOverlays.isEmpty())
        session.dispatch(EditorAction.Undo)
        assertEquals(overlay, session.state.project.textOverlays.single())
    }

    @Test
    fun invalidTextOverlayDoesNotMutateProject() {
        val session = newSession()
        val before = session.state.project

        session.dispatch(
            EditorAction.AddTextOverlay(
                TextOverlay("invalid", 0L, 1_000_000L, "   "),
            ),
        )

        assertEquals(before, session.state.project)
        assertNotNull(session.state.lastError)
        assertFalse(session.state.canUndo)
    }

    private fun newSession(
        project: Project = Project.create("Text", 1080, 1920, 30),
    ): ProjectSession = ProjectSession(
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
    )
}
