package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignGroupSelectionPolicyTest {
    private val group = DesignGroup("group-1", listOf("shape-1", "shape-2"))

    @Test
    fun selectingOneMemberExpandsToWholeGroupInProjectOrder() {
        val expanded = DesignGroupSelectionPolicy.expand(
            selectedIds = listOf("shape-2"),
            groups = listOf(group),
            existingElementIds = listOf("shape-1", "shape-2", "shape-3"),
        )

        assertEquals(listOf("shape-1", "shape-2"), expanded)
    }

    @Test
    fun exactGroupMatchesRegardlessOfSelectionOrder() {
        val matched = DesignGroupSelectionPolicy.exactGroup(
            selectedIds = listOf("shape-2", "shape-1"),
            groups = listOf(group),
        )

        assertEquals(group, matched)
    }

    @Test
    fun createGroupRejectsSelectionsThatOverlapExistingGroups() {
        assertFalse(DesignGroupSelectionPolicy.canCreateGroup(listOf("shape-1", "shape-3"), listOf(group)))
        assertFalse(DesignGroupSelectionPolicy.canCreateGroup(listOf("shape-3"), listOf(group)))
        assertTrue(DesignGroupSelectionPolicy.canCreateGroup(listOf("shape-3", "shape-4"), listOf(group)))
    }
}
