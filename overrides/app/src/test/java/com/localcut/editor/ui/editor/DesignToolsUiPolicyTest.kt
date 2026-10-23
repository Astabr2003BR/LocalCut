package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignToolsUiPolicyTest {
    @Test
    fun mobileDesignSectionsExposeElementsCanvasAndComponentsInStableOrder() {
        assertEquals(
            listOf(
                DesignToolsSection.ELEMENTS,
                DesignToolsSection.CANVAS,
                DesignToolsSection.COMPONENTS,
            ),
            DesignToolsUiPolicy.mobileSections(),
        )
    }

    @Test
    fun createsTimedShapeAtPlayheadWithUniqueIdAndTopZIndex() {
        val first = DesignToolsUiPolicy.newShape(
            project = Project.create("Design", 1080, 1920, 30),
            kind = ShapeKind.RECTANGLE,
            playheadUs = 2_000_000L,
        )
        val project = Project.create("Design", 1080, 1920, 30).copy(designElements = listOf(first.copy(zIndex = 4)))

        val second = DesignToolsUiPolicy.newShape(project, ShapeKind.ELLIPSE, 3_000_000L)

        assertEquals("shape-2", second.id)
        assertEquals(3_000_000L, second.startUs)
        assertEquals(3_000_000L, second.durationUs)
        assertEquals(5, second.zIndex)
        assertEquals(ShapeKind.ELLIPSE, second.shape!!.kind)
    }

    @Test
    fun freeformPresetCreatesClosedPathAndCenterGuidesAreToggleable() {
        val project = Project.create("Design", 1080, 1920, 30)
        val path = DesignToolsUiPolicy.newShape(project, ShapeKind.PATH, 0L)
        assertTrue(path.shape!!.path!!.closed)
        assertEquals(3, path.shape!!.path!!.points.size)

        val withGuides = DesignToolsUiPolicy.toggleCenterGuides(project.designCanvas)
        assertEquals(setOf(GuideAxis.HORIZONTAL, GuideAxis.VERTICAL), withGuides.guides.map { it.axis }.toSet())
        val withoutGuides = DesignToolsUiPolicy.toggleCenterGuides(withGuides)
        assertTrue(withoutGuides.guides.isEmpty())
    }

    @Test
    fun vectorStyleControlsPreserveElementAndClampRenderableGeometry() {
        val source = element("shape", 7).copy(startUs = 250_000L, durationUs = 2_500_000L)

        val filled = DesignToolsUiPolicy.setShapeFill(source, 0xFFFF3B30.toInt())
        val outlined = DesignToolsUiPolicy.setShapeStrokeColor(filled, 0xFF111111.toInt())
        val wide = DesignToolsUiPolicy.setShapeStrokeWidth(outlined, 999.0)
        val rounded = DesignToolsUiPolicy.setShapeCornerRadius(wide, 999.0)

        assertEquals(source.id, rounded.id)
        assertEquals(source.startUs, rounded.startUs)
        assertEquals(source.durationUs, rounded.durationUs)
        assertEquals(source.transform, rounded.transform)
        assertEquals(source.zIndex, rounded.zIndex)
        assertEquals(0xFFFF3B30.toInt(), rounded.shape!!.fillArgb)
        assertEquals(0xFF111111.toInt(), rounded.shape!!.strokeArgb)
        assertEquals(200.0, rounded.shape!!.strokeWidthPx, 0.0)
        assertEquals(300.0, rounded.shape!!.cornerRadiusPx, 0.0)
        assertTrue(rounded.validationIssues().isEmpty())
    }

    @Test
    fun fillAndStrokeCanBeRemovedWithoutChangingShapeKind() {
        val source = element("shape", 0).copy(
            shape = ShapeSpec(
                kind = ShapeKind.RECTANGLE,
                fillArgb = 0xFFFFFFFF.toInt(),
                strokeArgb = 0xFF000000.toInt(),
                strokeWidthPx = 12.0,
            ),
        )

        val noFill = DesignToolsUiPolicy.setShapeFill(source, null)
        val noStroke = DesignToolsUiPolicy.setShapeStrokeColor(noFill, null)

        assertEquals(ShapeKind.RECTANGLE, noStroke.shape!!.kind)
        assertNull(noStroke.shape!!.fillArgb)
        assertNull(noStroke.shape!!.strokeArgb)
        assertTrue(noStroke.validationIssues().isEmpty())
    }

    @Test
    fun arrangeFrontAndBackUseDeterministicExtremes() {
        val back = element("back", 2)
        val active = element("active", 5)
        val front = element("front", 9)
        val project = Project.create("Design", 1080, 1920, 30).copy(
            designElements = listOf(back, active, front),
        )

        assertEquals(10, DesignToolsUiPolicy.bringToFront(project, active).zIndex)
        assertEquals(1, DesignToolsUiPolicy.sendToBack(project, active).zIndex)
    }

    @Test
    fun singleElementArrangeRemainsStable() {
        val only = element("only", 0)
        val project = Project.create("Design", 1080, 1920, 30).copy(designElements = listOf(only))

        assertEquals(0, DesignToolsUiPolicy.bringToFront(project, only).zIndex)
        assertEquals(0, DesignToolsUiPolicy.sendToBack(project, only).zIndex)
    }

    private fun element(id: String, z: Int): DesignElement = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = ShapeSpec(ShapeKind.RECTANGLE),
        zIndex = z,
    )
}
