package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendExecutionPlannerTest {
    @Test
    fun threeInputsUseBottomThenScratchThenOutput() {
        val plan = BlendExecutionPlanner.plan(
            listOf(BlendMode.OVERLAY, BlendMode.MULTIPLY, BlendMode.NORMAL),
        )

        assertEquals(2, plan.bottomInputId)
        assertEquals(
            listOf(
                BlendExecutionStep(
                    sourceInputId = 1,
                    mode = BlendMode.MULTIPLY,
                    backdrop = BlendBackdropRef.Input(2),
                    target = BlendRenderTarget.SCRATCH_A,
                ),
                BlendExecutionStep(
                    sourceInputId = 0,
                    mode = BlendMode.OVERLAY,
                    backdrop = BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_A),
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
                BlendRenderTarget.OUTPUT,
            ),
            plan.steps.map { it.target },
        )
        assertEquals(
            listOf(
                BlendBackdropRef.Input(3),
                BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_A),
                BlendBackdropRef.Target(BlendRenderTarget.SCRATCH_B),
            ),
            plan.steps.map { it.backdrop },
        )
    }
}
