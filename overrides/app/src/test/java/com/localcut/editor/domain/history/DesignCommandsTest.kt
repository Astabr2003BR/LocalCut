package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignCommandsTest {
    @Test
    fun addDesignElementIsUndoableAndRedoable() {
        val initial = Project.create("Design", 1080, 1920, 30)
        val history = HistoryManager(initial)
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
        )

        history.execute(AddDesignElementCommand(element))
        assertEquals(listOf(element), history.project.designElements)

        assertTrue(history.undo())
        assertEquals(initial, history.project)

        assertTrue(history.redo())
        assertEquals(listOf(element), history.project.designElements)
    }

    @Test
    fun setCanvasSettingsRestoresPreviousSettingsOnUndo() {
        val initial = Project.create("Canvas", 1080, 1920, 30)
        val history = HistoryManager(initial)
        val settings = DesignCanvasSettings(
            guides = listOf(CanvasGuide("center-x", GuideAxis.VERTICAL, 0.5)),
            snapToleranceNormalized = 0.02,
        )

        history.execute(SetDesignCanvasCommand(settings))
        assertEquals(settings, history.project.designCanvas)

        assertTrue(history.undo())
        assertEquals(DesignCanvasSettings(), history.project.designCanvas)
    }

    @Test
    fun setProjectCanvasSizeIsUndoableAndPreservesTimelineContent() {
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
        )
        val initial = Project.create("Canvas", 1080, 1920, 30).copy(designElements = listOf(element))
        val history = HistoryManager(initial)

        history.execute(SetProjectCanvasSizeCommand(width = 1920, height = 1080))

        assertEquals(1920, history.project.width)
        assertEquals(1080, history.project.height)
        assertEquals(initial.designElements, history.project.designElements)
        assertEquals(initial.tracks, history.project.tracks)

        assertTrue(history.undo())
        assertEquals(1080, history.project.width)
        assertEquals(1920, history.project.height)
        assertEquals(initial.designElements, history.project.designElements)
    }

    @Test
    fun reusableComponentUpsertIsUndoable() {
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
        )
        val initial = Project.create("Components", 1080, 1920, 30).copy(designElements = listOf(element))
        val history = HistoryManager(initial)
        val component = ReusableComponent("card", "Card", listOf(element.id), setOf("shape-1.fill"))

        history.execute(UpsertReusableComponentCommand(component))
        assertEquals(component, history.project.reusableComponents.single())

        assertTrue(history.undo())
        assertTrue(history.project.reusableComponents.isEmpty())
    }

    @Test
    fun captionSplitIsUndoableAndPreservesMetadata() {
        val original = TextOverlay(
            id = "caption-1",
            startUs = 1_000_000L,
            durationUs = 2_000_000L,
            text = "hello world",
            style = TextStyle(letterSpacingEm = 0.05),
            caption = CaptionMetadata(languageTag = "pt-BR", speaker = "A"),
        )
        val initial = Project.create("Captions", 1080, 1920, 30).copy(textOverlays = listOf(original))
        val history = HistoryManager(initial)

        history.execute(
            SplitCaptionCommand(
                overlayId = original.id,
                leftDurationUs = 750_000L,
                rightId = "caption-2",
                leftText = "hello",
                rightText = "world",
            ),
        )

        assertEquals(listOf("caption-1", "caption-2"), history.project.textOverlays.map { it.id })
        assertEquals(original.style, history.project.textOverlays[1].style)
        assertEquals(original.caption, history.project.textOverlays[1].caption)

        assertTrue(history.undo())
        assertEquals(initial, history.project)
    }
}
