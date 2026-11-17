package com.localcut.editor.domain.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhubarbCueImportTest {
    @Test fun parsesRhubarbTimeAndShapeWithoutRescaling() {
        val cues = RhubarbCueImport.parse("0.00\tX\n0.05\tD\n0.27\tC\n0.31\tB\n0.43\tX\n0.47\tX", 1_000_000L)
        assertEquals(5, cues.size)
        assertEquals(VisemeCue(50_000L, 220_000L, VisemeShape.A), cues[1])
        assertEquals(470_000L, cues.last().endUs)
        assertEquals(VisemePose.REST, VisemeTimeline(cues, 1_000_000L).poseAt(600_000L))
    }

    @Test fun rejectsMalformedUnorderedAndOutOfBoundsData() {
        listOf("", "0\tX", "0\tX\n0\tA", "0\tX\n2\tX",
            "0\tX\n0.5\tD", "0\tX\n0.5\t?", "0\tX\nNaN\tX",
            "0.2\tX\n0.5\tX", "0\tX\n0.5\tA\n0.2\tX",
            "0 X\n0.5 X", "0\tX\n0.0000001\tX").forEach { raw ->
            assertTrue(raw, runCatching { RhubarbCueImport.parse(raw, 1_000_000L) }.isFailure)
        }
    }
}
