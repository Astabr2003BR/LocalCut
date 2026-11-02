package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteDesignElementsCommandTest {
    @Test
    fun deletesMultipleElementsAtomicallyAndUndoRestoresExactOrder() {
        val project = projectWith("a", "b", "c", "d")

        val deleted = DeleteDesignElementsCommand(listOf("b", "d")).apply(project)
        assertEquals(listOf("a", "c"), deleted.project.designElements.map { it.id })

        val restored = deleted.inverse.apply(deleted.project)
        assertEquals(listOf("a", "b", "c", "d"), restored.project.designElements.map { it.id })

        val redone = restored.inverse.apply(restored.project)
        assertEquals(listOf("a", "c"), redone.project.designElements.map { it.id })
    }

    @Test
    fun missingDuplicateBlankOrReusableComponentReferenceFailsBeforeMutation() {
        val base = projectWith("a", "b", "c")
        assertTrue(runCatching { DeleteDesignElementsCommand(emptyList()).apply(base) }.isFailure)
        assertTrue(runCatching { DeleteDesignElementsCommand(listOf("a", "a")).apply(base) }.isFailure)
        assertTrue(runCatching { DeleteDesignElementsCommand(listOf(" ")).apply(base) }.isFailure)
        assertTrue(runCatching { DeleteDesignElementsCommand(listOf("missing")).apply(base) }.isFailure)

        val protected = base.copy(
            reusableComponents = listOf(
                ReusableComponent(
                    id = "component",
                    name = "Grupo",
                    elementIds = listOf("b"),
                ),
            ),
        )
        val result = runCatching { DeleteDesignElementsCommand(listOf("a", "b")).apply(protected) }
        assertTrue(result.isFailure)
        assertEquals(listOf("a", "b", "c"), protected.designElements.map { it.id })
    }

    private fun projectWith(vararg ids: String): Project {
        val base = Project.create("design", 1080, 1920, 30)
        return base.copy(
            designElements = ids.mapIndexed { index, id ->
                DesignElement(
                    id = id,
                    startUs = 0L,
                    durationUs = 1_000_000L,
                    shape = ShapeSpec(kind = ShapeKind.RECTANGLE),
                    zIndex = index,
                )
            },
        )
    }
}
