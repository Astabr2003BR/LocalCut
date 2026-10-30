package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCutFrameSnapPolicyTest {
    @Test
    fun snapsToNearestProjectFrame() {
        assertEquals(1_000_000L, CameraCutFrameSnapPolicy.snap(1_009_000L, 30))
        assertEquals(1_033_333L, CameraCutFrameSnapPolicy.snap(1_020_000L, 30))
        assertEquals(0L, CameraCutFrameSnapPolicy.snap(2_000L, 24))
    }

    @Test
    fun preservesExactFrameAndRejectsInvalidInputs() {
        assertEquals(500_000L, CameraCutFrameSnapPolicy.snap(500_000L, 30))
        assertTrue(runCatching { CameraCutFrameSnapPolicy.snap(-1L, 30) }.isFailure)
        assertTrue(runCatching { CameraCutFrameSnapPolicy.snap(0L, 0) }.isFailure)
        assertTrue(runCatching { CameraCutFrameSnapPolicy.snap(0L, 241) }.isFailure)
    }
}
