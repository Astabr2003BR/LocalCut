package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitStudioBatchTest {
    @Test fun combinedApplyPreservesOtherEffectsAndIsIdempotent() {
        val prior = EffectInstance(id = "talk", typeId = VisemeMouthDemo.TYPE_ID)
        val existing = listOf(prior)
        val motion = PortraitMotionPresets.preset(PortraitMotionPreset.NATURAL)
        val brow = BrowExpressionSettings(emotion = BrowEmotion.SURPRISED, intensity = 0.5)
        val combined = PortraitStudioBatch.apply("clip", 5_000_000L, existing, motion, brow)
        assertEquals(prior, combined.first())
        assertEquals(3, combined.size)
        assertEquals(combined, PortraitStudioBatch.apply("clip", 5_000_000L, combined, motion, brow))
        assertEquals(listOf(prior), existing)
    }

    @Test fun invalidGeometryOrGuidanceCannotPartiallyApply() {
        val existing = emptyList<EffectInstance>()
        val motion = PortraitMotionPresets.preset(PortraitMotionPreset.NATURAL)
        val brow = BrowExpressionSettings(emotion = BrowEmotion.WORRIED, intensity = 0.5)
        assertTrue(runCatching {
            PortraitStudioBatch.apply("clip", 5_000_000L, existing, motion,
                brow.copy(radiusX = Double.NaN))
        }.isFailure)
        assertTrue(runCatching {
            PortraitStudioBatch.apply("clip", 5_000_000L, existing, motion, brow, "Oi")
        }.isFailure)
        assertTrue(existing.isEmpty())
    }
}
