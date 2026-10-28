package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.history.ProjectEditOperation
import com.localcut.editor.domain.history.ProjectItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditDrawingPayloadParserTest {
    @Test
    fun parsesDrawingDeleteAndPathPointPatch() {
        val operations = ProjectEditPayloadParser.parse(
            """{"operations":[{"op":"deleteByKind","kinds":["drawing"]},{"op":"patchDesign","elementId":"stroke-1","points":[{"x":0.2,"y":0.3},{"x":0.7,"y":0.8}],"closed":true}]}""",
        )

        assertEquals(
            setOf(ProjectItemKind.DRAWING),
            (operations[0] as ProjectEditOperation.DeleteByKind).kinds,
        )
        val patch = operations[1] as ProjectEditOperation.PatchDesign
        assertEquals(2, patch.points!!.size)
        assertEquals(0.2, patch.points!!.first().x, 0.0)
        assertTrue(patch.closed == true)
    }

    @Test
    fun pathPatchPointLimitStillFailsClosed() {
        val points = (1..257).joinToString(prefix = "[", postfix = "]") { "{\"x\":0.5,\"y\":0.5}" }
        assertTrue(
            runCatching {
                ProjectEditPayloadParser.parse(
                    "{\"operations\":[{\"op\":\"patchDesign\",\"elementId\":\"d\",\"points\":$points}]}",
                )
            }.isFailure,
        )
    }
}
