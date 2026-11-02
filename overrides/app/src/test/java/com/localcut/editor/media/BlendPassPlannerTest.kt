package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendPassPlannerTest {
    @Test
    fun registeredInputsAreCompositedFromBottomToTop() {
        val plan = BlendPassPlanner.plan(
            modesByInputId = listOf(
                BlendMode.OVERLAY,
                BlendMode.MULTIPLY,
                BlendMode.NORMAL,
            ),
        )

        assertEquals(2, plan.bottomInputId)
        assertEquals(
            listOf(
                BlendPass(sourceInputId = 1, mode = BlendMode.MULTIPLY),
                BlendPass(sourceInputId = 0, mode = BlendMode.OVERLAY),
            ),
            plan.passes,
        )
    }

    @Test
    fun bottomBlendModeIsIgnoredBecauseThereIsNoBackdropBelowIt() {
        val plan = BlendPassPlanner.plan(listOf(BlendMode.NORMAL, BlendMode.SCREEN))

        assertEquals(1, plan.bottomInputId)
        assertEquals(listOf(BlendPass(0, BlendMode.NORMAL)), plan.passes)
    }

    @Test
    fun singleInputNeedsNoBlendPassAndEmptyInputFailsClosed() {
        val single = BlendPassPlanner.plan(listOf(BlendMode.MULTIPLY))
        assertEquals(0, single.bottomInputId)
        assertTrue(single.passes.isEmpty())

        assertTrue(runCatching { BlendPassPlanner.plan(emptyList()) }.isFailure)
    }
}
