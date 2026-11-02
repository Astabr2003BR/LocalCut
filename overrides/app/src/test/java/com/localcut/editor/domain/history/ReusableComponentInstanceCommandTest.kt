package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReusableComponentInstanceCommandTest {
    @Test
    fun instantiatePreservesRelativeTimingAndIsUndoable() {
        val first = DesignElement("a", 1_000_000L, 2_000_000L, shape = ShapeSpec(ShapeKind.RECTANGLE), zIndex = 2)
        val second = DesignElement("b", 1_500_000L, 1_000_000L, shape = ShapeSpec(ShapeKind.ELLIPSE), zIndex = 3)
        val component = ReusableComponent("card", "Card", listOf("a", "b"))
        val initial = Project.create("Templates", 1080, 1920, 30).copy(
            designElements = listOf(first, second),
            reusableComponents = listOf(component),
        )
        val history = HistoryManager(initial)

        history.execute(
            InstantiateReusableComponentCommand(
                componentId = component.id,
                instancePrefix = "card-instance-1",
                startUs = 5_000_000L,
            ),
        )

        val created = history.project.designElements.drop(2)
        assertEquals(listOf("card-instance-1-a", "card-instance-1-b"), created.map { it.id })
        assertEquals(listOf(5_000_000L, 5_500_000L), created.map { it.startUs })
        assertEquals(listOf(4, 5), created.map { it.zIndex })

        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.redo())
        assertEquals(4, history.project.designElements.size)
    }
}
