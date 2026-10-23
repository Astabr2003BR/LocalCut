package com.localcut.editor.data.project

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionGraphProjectJsonTest {
    @Test
    fun latestSchemaRoundTripPreservesHierarchyAndCameraCuts() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "null-1",
                    kind = CompositionNodeKind.NULL,
                    transform = LayerTransform3D(x = 10.0, y = 20.0, z = 30.0),
                ),
                CompositionNode(
                    id = "camera-1",
                    kind = CompositionNodeKind.CAMERA,
                    transform = LayerTransform3D(z = -100.0),
                    camera = CameraSettings(focalLength = 1200.0),
                ),
            ),
            cameraCuts = listOf(CameraCut(0L, "camera-1")),
        )
        val project = Project.create("Graph", 1080, 1920, 30).copy(compositionGraph = graph)

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertTrue(encoded.contains("\"compositionGraph\""))
        assertEquals(graph, decoded.compositionGraph)
    }

    @Test
    fun interimSchemaV4WithoutCompositionGraphMigratesToEmptyGraph() {
        val legacyV4 = ProjectJson.encode(Project.create("Legacy", 1080, 1920, 30))
            .replace("\"schemaVersion\":5", "\"schemaVersion\":4")
            .replace(Regex(",\"compositionGraph\":\\{.*}$"), "}")

        val decoded = ProjectJson.decode(legacyV4)

        assertEquals(CompositionGraph(), decoded.compositionGraph)
    }
}
