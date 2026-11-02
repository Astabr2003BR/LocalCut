package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.CanvasGuide
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSessionTest {
    @Test
    fun designElementCanvasAndReusableComponentFlowIsUndoable() {
        val session = newSession()
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 2_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
        )

        session.dispatch(EditorAction.AddDesignElement(element))
        assertEquals(element, session.state.project.designElements.single())

        val canvas = DesignCanvasSettings(
            guides = listOf(CanvasGuide("center-x", GuideAxis.VERTICAL, 0.5)),
            snapToleranceNormalized = 0.02,
        )
        session.dispatch(EditorAction.SetDesignCanvas(canvas))
        assertEquals(canvas, session.state.project.designCanvas)

        val component = ReusableComponent(
            id = "template-1",
            name = "Shape template",
            elementIds = listOf(element.id),
        )
        session.dispatch(EditorAction.UpsertReusableComponent(component))
        assertEquals(component, session.state.project.reusableComponents.single())

        session.dispatch(EditorAction.Undo)
        assertTrue(session.state.project.reusableComponents.isEmpty())
        session.dispatch(EditorAction.Undo)
        assertEquals(DesignCanvasSettings(), session.state.project.designCanvas)
        session.dispatch(EditorAction.Undo)
        assertTrue(session.state.project.designElements.isEmpty())

        session.dispatch(EditorAction.Redo)
        assertEquals(element, session.state.project.designElements.single())
    }

    @Test
    fun deleteDesignElementIsUndoable() {
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.ELLIPSE),
        )
        val session = newSession(Project.create("Design", 1080, 1920, 30).copy(designElements = listOf(element)))

        session.dispatch(EditorAction.DeleteDesignElement(element.id))
        assertTrue(session.state.project.designElements.isEmpty())

        session.dispatch(EditorAction.Undo)
        assertEquals(element, session.state.project.designElements.single())
    }

    private fun newSession(
        project: Project = Project.create("Design", 1080, 1920, 30),
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
