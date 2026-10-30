package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.DesignGroup
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.timeline.ProjectIssueCode
import com.localcut.editor.domain.timeline.TimelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DesignGroupCommandsTest {
    @Test
    fun groupingIsUndoableAndRedoable() {
        val initial = projectWithElements()
        val history = HistoryManager(initial)

        history.execute(GroupDesignElementsCommand("group-1", listOf("shape-1", "shape-2")))
        assertEquals(listOf(DesignGroup("group-1", listOf("shape-1", "shape-2"))), history.project.designGroups)

        assertTrue(history.undo())
        assertTrue(history.project.designGroups.isEmpty())

        assertTrue(history.redo())
        assertEquals("group-1", history.project.designGroups.single().id)
    }

    @Test
    fun ungroupRemovesOnlyMembershipAndUndoRestoresIt() {
        val group = DesignGroup("group-1", listOf("shape-1", "shape-2"))
        val initial = projectWithElements().copy(designGroups = listOf(group))
        val history = HistoryManager(initial)

        history.execute(UngroupDesignElementsCommand(group.id))

        assertTrue(history.project.designGroups.isEmpty())
        assertEquals(initial.designElements, history.project.designElements)
        assertTrue(history.undo())
        assertEquals(listOf(group), history.project.designGroups)
    }

    @Test
    fun validatorRejectsMissingMembersAndOverlappingGroups() {
        val project = projectWithElements().copy(
            designGroups = listOf(
                DesignGroup("group-1", listOf("shape-1", "shape-2")),
                DesignGroup("group-2", listOf("shape-1", "missing")),
            ),
        )

        val issues = TimelineValidator.validate(project)

        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_DESIGN_STATE && it.detail?.contains("missing") == true })
        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_DESIGN_STATE && it.detail?.contains("multiple groups") == true })
    }

    @Test
    fun groupingRejectsElementAlreadyInAnotherGroup() {
        val initial = projectWithElements().copy(
            designGroups = listOf(DesignGroup("existing", listOf("shape-1", "shape-2"))),
        )

        try {
            GroupDesignElementsCommand("other", listOf("shape-1", "shape-2")).apply(initial)
            fail("expected grouping to reject overlapping membership")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun projectWithElements(): Project = Project.create("Groups", 1080, 1920, 30).copy(
        designElements = listOf(element("shape-1"), element("shape-2")),
    )

    private fun element(id: String) = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = ShapeSpec(ShapeKind.RECTANGLE),
    )
}
