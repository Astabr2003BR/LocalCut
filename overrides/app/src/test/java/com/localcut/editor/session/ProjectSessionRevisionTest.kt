package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectSessionRevisionTest {
    @Test
    fun successfulEditUndoAndRedoEachAdvanceRevisionExactlyOnce() {
        val project = Project.create("Revision", 1080, 1920, 30)
        val clock = InMemoryProjectRevisionClock(mapOf(project.id to 5L))
        val session = newSession(project, clock)
        val overlay = TextOverlay("title-1", 0L, 2_000_000L, "LocalCut")

        assertEquals(5L, session.state.revision)

        session.dispatch(EditorAction.AddTextOverlay(overlay))
        assertEquals(6L, session.state.revision)

        session.dispatch(EditorAction.Undo)
        assertEquals(7L, session.state.revision)

        session.dispatch(EditorAction.Redo)
        assertEquals(8L, session.state.revision)
    }

    @Test
    fun rejectedEditAndSelectionDoNotAdvanceRevision() {
        val project = Project.create("Revision", 1080, 1920, 30)
        val clock = InMemoryProjectRevisionClock(mapOf(project.id to 11L))
        val session = newSession(project, clock)

        session.dispatch(EditorAction.SelectTrack("visual-1"))
        assertEquals(11L, session.state.revision)

        session.dispatch(EditorAction.AddTextOverlay(TextOverlay("bad", 0L, 1_000_000L, "   ")))
        assertEquals(11L, session.state.revision)
        assertNotNull(session.state.lastError)
    }

    private fun newSession(project: Project, clock: ProjectRevisionClock): ProjectSession = ProjectSession(
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
        revisionClock = clock,
    )
}
