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
}
