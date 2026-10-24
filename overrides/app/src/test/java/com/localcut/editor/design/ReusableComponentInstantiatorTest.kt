package com.localcut.editor.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReusableComponentInstantiatorTest {
    @Test
    fun componentInstanceClonesElementsWithStableIdsAndRelativeTiming() {
        val elements = listOf(
            DesignElement("title-bg", 0L, 2_000_000L, shape = ShapeSpec(ShapeKind.RECTANGLE), transform = TransformValue(x = 10.0, y = 20.0)),
            DesignElement("accent", 250_000L, 1_500_000L, shape = ShapeSpec(ShapeKind.ELLIPSE), transform = TransformValue(x = 30.0, y = 40.0)),
        )
        val component = ReusableComponent("title-card", "Title card", listOf("title-bg", "accent"))

        val instance = ReusableComponentInstantiator.instantiate(
            component = component,
            sourceElements = elements,
            instanceId = "intro",
            startUs = 5_000_000L,
            offsetX = 100.0,
            offsetY = -50.0,
        )

        assertEquals(listOf("intro:title-bg", "intro:accent"), instance.map { it.id })
        assertEquals(5_000_000L, instance.first().startUs)
        assertEquals(5_250_000L, instance.last().startUs)
        assertEquals(110.0, instance.first().transform.x, 0.0)
        assertEquals(-30.0, instance.first().transform.y, 0.0)
        assertTrue(instance.all { it.validationIssues().isEmpty() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingComponentElementIsRejected() {
        val component = ReusableComponent("bad", "Bad", listOf("missing"))
        ReusableComponentInstantiator.instantiate(component, emptyList(), "x", 0L)
    }
}
