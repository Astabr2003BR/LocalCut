package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.history.ProjectEditOperation
import com.localcut.editor.domain.history.ProjectItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditPayloadParserTest {
    @Test
    fun parsesTypedBatchOperations() {
        val operations = ProjectEditPayloadParser.parse(
            """{"operations":[{"op":"deleteByKind","kinds":["audio","text"]},{"op":"patchClip","clipId":"clip-1","startUs":2000000,"volume":0.5,"scale":1.25},{"op":"addText","id":"t2","text":"Olá","startUs":0,"durationUs":1000000,"fontSizePx":70},{"op":"patchText","overlayId":"t2","text":"Olá mundo","opacity":0.8}]}""",
        )

        assertEquals(4, operations.size)
        assertEquals(
            setOf(ProjectItemKind.AUDIO, ProjectItemKind.TEXT),
            (operations[0] as ProjectEditOperation.DeleteByKind).kinds,
        )
        val clip = operations[1] as ProjectEditOperation.PatchClip
        assertEquals("clip-1", clip.clipId)
        assertEquals(2_000_000L, clip.startUs)
        assertEquals(0.5, clip.volume!!, 0.0)
        assertEquals(1.25, clip.scale!!, 0.0)
        val added = operations[2] as ProjectEditOperation.AddText
        assertEquals("Olá", added.overlay.text)
        assertEquals(70.0, added.overlay.style.fontSizePx, 0.0)
        val text = operations[3] as ProjectEditOperation.PatchText
        assertEquals("Olá mundo", text.text)
        assertEquals(0.8, text.opacity!!, 0.0)
    }

    @Test
    fun parsesAddClipFromExistingAsset() {
        val operations = ProjectEditPayloadParser.parse(
            """{"operations":[{"op":"addClip","clipId":"c2","assetId":"image-1","trackId":"visual-1","startUs":2000000,"durationUs":1000000,"scale":0.75,"opacity":0.8}]}""",
        )

        val clip = operations.single() as ProjectEditOperation.AddClip
        assertEquals("c2", clip.clipId)
        assertEquals("image-1", clip.assetId)
        assertEquals("visual-1", clip.trackId)
        assertEquals(2_000_000L, clip.startUs)
        assertEquals(1_000_000L, clip.durationUs)
        assertEquals(0.75, clip.scale, 0.0)
        assertEquals(0.8, clip.opacity, 0.0)
    }

    @Test
    fun unknownFieldsFailClosed() {
        val result = runCatching {
            ProjectEditPayloadParser.parse(
                """{"operations":[{"op":"patchClip","clipId":"x","mystery":1}]}""",
            )
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun moreThanThirtyTwoOperationsIsRejected() {
        val payload = (1..33).joinToString(prefix = "{\"operations\":[", postfix = "]}") {
            "{\"op\":\"deleteClip\",\"clipId\":\"c$it\"}"
        }
        assertTrue(runCatching { ProjectEditPayloadParser.parse(payload) }.isFailure)
    }

    @Test
    fun invalidRootAndOversizePayloadAreRejected() {
        assertTrue(runCatching { ProjectEditPayloadParser.parse("{\"operations\":[],\"extra\":true}") }.isFailure)
        val oversized = "x".repeat(16_385)
        assertTrue(runCatching { ProjectEditPayloadParser.parse(oversized) }.isFailure)
    }
    @Test
    fun parsesDesignAddPatchDeleteAndPathPoints() {
        val operations = ProjectEditPayloadParser.parse(
            """{"operations":[{"op":"addDesign","id":"stroke-1","startUs":0,"durationUs":2000000,"kind":"path","points":[{"x":0.1,"y":0.2},{"x":0.8,"y":0.7}],"closed":false,"strokeArgb":4294901760,"strokeWidthPx":14,"x":20,"y":-30,"scale":1.2},{"op":"patchDesign","elementId":"stroke-1","opacity":0.5,"zIndex":9},{"op":"deleteDesign","elementId":"stroke-1"}]}""",
        )

        assertEquals(3, operations.size)
        val add = operations[0] as ProjectEditOperation.AddDesign
        assertEquals("stroke-1", add.element.id)
        assertEquals(2, add.element.shape!!.path!!.points.size)
        assertTrue(!add.element.shape!!.path!!.closed)
        assertEquals(0xFFFF0000.toInt(), add.element.shape!!.strokeArgb)
        assertEquals(14.0, add.element.shape!!.strokeWidthPx, 0.0)
        val patch = operations[1] as ProjectEditOperation.PatchDesign
        assertEquals(0.5, patch.opacity!!, 0.0)
        assertEquals(9, patch.zIndex)
        assertEquals("stroke-1", (operations[2] as ProjectEditOperation.DeleteDesign).elementId)
    }

    @Test
    fun designPathPointLimitAndUnknownDesignFieldsFailClosed() {
        val points = (1..257).joinToString(prefix = "[", postfix = "]") { "{\"x\":0.5,\"y\":0.5}" }
        assertTrue(
            runCatching {
                ProjectEditPayloadParser.parse(
                    "{\"operations\":[{\"op\":\"addDesign\",\"id\":\"d\",\"startUs\":0,\"durationUs\":1000,\"kind\":\"path\",\"points\":$points}]}",
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                ProjectEditPayloadParser.parse(
                    """{"operations":[{"op":"patchDesign","elementId":"d","unknown":1}]}""",
                )
            }.isFailure,
        )
    }

}
