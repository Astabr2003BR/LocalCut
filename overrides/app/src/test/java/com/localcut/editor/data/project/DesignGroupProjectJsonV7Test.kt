package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.DesignGroup
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignGroupProjectJsonV7Test {
    @Test
    fun schemaV7RoundTripsDesignGroups() {
        val first = element("shape-1")
        val second = element("shape-2")
        val group = DesignGroup("group-1", listOf(first.id, second.id))
        val project = Project.create("Groups", 1080, 1920, 30).copy(
            designElements = listOf(first, second),
            designGroups = listOf(group),
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertEquals(7, BuildContract.PROJECT_SCHEMA_VERSION)
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        assertEquals(listOf(group), decoded.designGroups)
    }

    @Test
    fun schemaV6MigratesDesignGroupsToEmptyList() {
        val encodedV7 = ProjectJson.encode(Project.create("Legacy", 1080, 1920, 30))
        val v6 = encodedV7
            .replace("\"schemaVersion\":7", "\"schemaVersion\":6")
            .replace(",\"designGroups\":[]", "")

        val decoded = ProjectJson.decode(v6)

        assertEquals(7, decoded.schemaVersion)
        assertTrue(decoded.designGroups.isEmpty())
    }

    private fun element(id: String) = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = ShapeSpec(ShapeKind.RECTANGLE),
    )
}
