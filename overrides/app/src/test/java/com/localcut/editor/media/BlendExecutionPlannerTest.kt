package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendExecutionPlannerTest {
    @Test
    fun threeInputsStartFromTransparentSoBottomAlphaIsPreserved() {
        val plan = BlendExecutionPlanner.plan(
            listOf(BlendMode.OVERLAY, BlendMode.MULTIPLY, BlendMode.NORMAL),
        )

        assertEquals(2, plan.bottomInputId)
        assertEquals(
            listOf(
                BlendExecutionStep(
                    sourceInputId = 2,
                    mode = BlendMode.NORMAL,
                    backdrop = BlendBackdropRef.Transparent,
                    target = BlendRenderTarget.SCRATCH_A,
                ),
                BlendExecutionStep(
                    sourceInputId = 1,
                    mode = BlendMode.MULTIPLY,
                    backdrop = BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_A),
                    target = BlendRenderTarget.SCRATCH_B,
                ),
                BlendExecutionStep(
                    sourceInputId = 0,
                    mode = BlendMode.OVERLAY,
                    backdrop = BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_B),
                    target = BlendRenderTarget.OUTPUT,
                ),
            ),
            plan.steps,
        )
    }

    @Test
    fun fourInputsAlternateScratchWithoutReadWriteAlias() {
        val plan = BlendExecutionPlanner.plan(
            listOf(BlendMode.SCREEN, BlendMode.LIGHTEN, BlendMode.MULTIPLY, BlendMode.NORMAL),
        )

        assertEquals(
            listOf(
                BlendRenderTarget.SCRATCH_A,
                BlendRenderTarget.SCRATCH_B,
                BlendRenderTarget.SCRATCH_A,
                BlendRenderTarget.OUTPUT,
            ),
            plan.steps.map { it.target },
        )
        assertEquals(
            listOf(
                BlendBackdropRef.Transparent,
                BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_A),
                BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_B),
                BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_A),
            ),
            plan.steps.map { it.backdrop },
        )
    }

    @Test
    fun singleInputStillRunsOneSourceOverTransparentPass() {
        val plan = BlendExecutionPlanner.plan(listOf(BlendMode.MULTIPLY))

        assertEquals(0, plan.bottomInputId)
        assertEquals(
            listOf(
                BlendExecutionStep(
                    sourceInputId = 0,
                    mode = BlendMode.MULTIPLY,
                    backdrop = BlendBackdropRef.Transparent,
                    target = BlendRenderTarget.OUTPUT,
                ),
            ),
            plan.steps,
        )
    }
}
