package com.localcut.editor.mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class StudioMediaCopyTest {
    @Test fun exactLimitPreservesBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        val out = ByteArrayOutputStream()
        assertEquals(3L, copyStudioMedia(ByteArrayInputStream(bytes), out, availableBytes = { 3 }, limit = 3, reserve = 0))
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test fun rejectsEmptyOversizeLowSpaceAndCancellation() {
        for (mode in listOf("empty", "limit", "space", "cancel")) {
            val out = ByteArrayOutputStream()
            val result = runCatching {
                copyStudioMedia(ByteArrayInputStream(if (mode == "empty") byteArrayOf() else byteArrayOf(1, 2, 3)), out,
                    checkCancelled = { if (mode == "cancel") error("cancelled") },
                    availableBytes = { if (mode == "space") 2 else 3 },
                    limit = if (mode == "limit") 2 else 3, reserve = 0)
            }
            assertTrue(mode, result.isFailure)
            assertEquals(mode, 0, out.size())
        }
    }
}
