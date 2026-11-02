package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EasingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPresetLibraryTest {
    @Test
    fun presetsExpandIntoExpectedMotionOperations() {
        val fade = MotionPresetLibrary.build(MotionPreset.FADE_IN, startOffsetUs = 0L, durationUs = 300_000L)
        val slide = MotionPresetLibrary.build(MotionPreset.SLIDE_UP, startOffsetUs = 100_000L, durationUs = 400_000L)
        val pop = MotionPresetLibrary.build(MotionPreset.POP_IN, startOffsetUs = 0L, durationUs = 250_000L)
        val spin = MotionPresetLibrary.build(MotionPreset.SPIN_IN, startOffsetUs = 0L, durationUs = 500_000L)

        assertEquals(listOf(MotionOperationKind.OPACITY), fade.map { it.kind })
        assertEquals(listOf(MotionOperationKind.MOVE_Y, MotionOperationKind.OPACITY), slide.map { it.kind })
        assertEquals(listOf(MotionOperationKind.SCALE, MotionOperationKind.OPACITY), pop.map { it.kind })
        assertEquals(
            listOf(MotionOperationKind.ROTATE, MotionOperationKind.SCALE, MotionOperationKind.OPACITY),
            spin.map { it.kind },
        )
        assertEquals(100_000L, slide.first().startOffsetUs)
        assertEquals(400_000L, slide.first().durationUs)
        assertTrue(slide.all { it.easing.kind == EasingKind.EASE_OUT })
    }

    @Test
    fun everyPresetProducesAnimationsAcceptedByCorePlanner() {
        MotionPreset.values().forEach { preset ->
            val specs = MotionPresetLibrary.build(preset, startOffsetUs = 50_000L, durationUs = 350_000L)
            assertTrue(specs.isNotEmpty())
            specs.forEach { spec -> MotionOperationPlanner.toAnimation(spec) }
        }
    }

    @Test
    fun invalidTimingFailsClosed() {
        assertTrue(runCatching { MotionPresetLibrary.build(MotionPreset.FADE_IN, -1L, 300_000L) }.isFailure)
        assertTrue(runCatching { MotionPresetLibrary.build(MotionPreset.FADE_IN, 0L, 0L) }.isFailure)
    }
}
