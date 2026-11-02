package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VectorLassoPersistentSelectionPolicyTest {
    @Test
    fun capturePreservesStableOrderAndExistingOnlyPrunesMissingIds() {
        val captured = VectorLassoPersistentSelectionPolicy.capture(listOf("path-b", "path-a"))
        assertEquals(listOf("path-b", "path-a"), captured)

        val existing = VectorLassoPersistentSelectionPolicy.existing(
            selectedIds = captured,
            elements = listOf(path("path-a"), path("path-c"), path("path-b")),
        )
        assertEquals(listOf("path-b", "path-a"), existing)

        val afterDelete = VectorLassoPersistentSelectionPolicy.existing(
            selectedIds = captured,
            elements = listOf(path("path-a"), path("path-c")),
        )
        assertEquals(listOf("path-a"), afterDelete)
    }

    @Test
    fun captureRejectsBlankOrDuplicateIdsAndEmptySelectionIsAllowed() {
        assertEquals(emptyList<String>(), VectorLassoPersistentSelectionPolicy.capture(emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            VectorLassoPersistentSelectionPolicy.capture(listOf("path-a", "path-a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VectorLassoPersistentSelectionPolicy.capture(listOf(" "))
        }
    }

    private fun path(id: String) = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = ShapeSpec(kind = ShapeKind.PATH, path = com.localcut.editor.domain.model.VectorPath()),
    )
}
