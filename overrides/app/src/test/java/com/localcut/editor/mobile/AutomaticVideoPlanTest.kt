package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutomaticVideoPlanTest {
    @Test fun buildsOrderedVariableDurationScenes() {
        val plan = AutomaticVideoPlan.build("9:16", listOf(AutomaticScene(2, 5000), AutomaticScene(1, 2500), AutomaticScene(2, 1000)), 2)
        assertEquals(1080, plan.width)
        assertEquals(1920, plan.height)
        assertEquals(8_500_000L, plan.durationUs)
        assertEquals(listOf(0L, 5_000_000L, 7_500_000L), plan.scenes.map { it.startUs })
        assertEquals(listOf(1, 0, 1), plan.scenes.map { it.imageIndex })
    }

    @Test fun rejectsMissingInvalidAndOverflowingScenes() {
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("4:5", emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("9:16", listOf(AutomaticScene(0, 1000)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("9:16", listOf(AutomaticScene(2, 1000)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("9:16", listOf(AutomaticScene(1, 0)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("9:16", listOf(AutomaticScene(1, Long.MAX_VALUE)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("9:16", List(2) { AutomaticScene(1, Long.MAX_VALUE / 1000L) }, 1) }
    }

    @Test fun validatesSourceOffsetsAndOverflow() {
        val plan = AutomaticVideoPlan.build("16:9", listOf(AutomaticScene(1, 500, 200), AutomaticScene(1, 1000)), 1)
        assertEquals(listOf(200_000L, 0L), plan.scenes.map { it.sourceInUs })
        assertEquals(listOf(700_000L, 1_000_000L), plan.scenes.map { it.sourceOutUs })
        assertEquals(listOf(0L, 500_000L), plan.scenes.map { it.startUs })
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("16:9", listOf(AutomaticScene(1, 500, -1)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("16:9", listOf(AutomaticScene(1, 500, Long.MAX_VALUE)), 1) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticVideoPlan.build("16:9", listOf(AutomaticScene(1, 500, Long.MAX_VALUE / 1000L)), 1) }
    }

    @Test fun notSubjectToCarouselPageQuota() {
        assertEquals(40, AutomaticVideoPlan.build("4:5", List(40) { AutomaticScene(1, 1000) }, 1).scenes.size)
    }
}
