package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendRenderTargetPlannerTest {
    @Test
    fun oneBlendPassWritesDirectlyToFinalOutput() {
        assertEquals(
            listOf(BlendRenderTarget.OUTPUT),
            BlendRenderTargetPlanner.targetsFor(passCount = 1),
        )
    }

    @Test
    fun multiplePassesPingPongScratchTexturesAndFinishOnOutput() {
        assertEquals(
            listOf(BlendRenderTarget.SCRATCH_A, BlendRenderTarget.OUTPUT),
            BlendRenderTargetPlanner.targetsFor(2),
        )
        assertEquals(
            listOf(BlendRenderTarget.SCRATCH_A, BlendRenderTarget.SCRATCH_B, BlendRenderTarget.OUTPUT),
            BlendRenderTargetPlanner.targetsFor(3),
        )
        assertEquals(
            listOf(
                BlendRenderTarget.SCRATCH_A,
                BlendRenderTarget.SCRATCH_B,
                BlendRenderTarget.SCRATCH_A,
                BlendRenderTarget.OUTPUT,
            ),
            BlendRenderTargetPlanner.targetsFor(4),
        )
    }

    @Test
    fun noBlendPassNeedsNoIntermediateTargetAndNegativeFailsClosed() {
        assertTrue(BlendRenderTargetPlanner.targetsFor(0).isEmpty())
        assertTrue(runCatching { BlendRenderTargetPlanner.targetsFor(-1) }.isFailure)
    }
}
