package com.localcut.editor.domain.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitFaceGeometryTest {
    @Test fun frontalPortraitUsesSourceAspectAndPreservesMotionAndEmotion() {
        val motion = PortraitMotionSettings(headMotionStrength = 0.7)
        val brows = BrowExpressionSettings(emotion = BrowEmotion.WORRIED, intensity = 0.6)
        val result = PortraitFaceGeometry.suggest(1080, 1920, 0.63, 0.36, 0.37, 0.36, motion, brows)
        assertEquals(0.37, result.motion.leftX, 1e-9)
        assertEquals(0.63, result.motion.rightX, 1e-9)
        assertEquals(0.7, result.motion.headMotionStrength, 1e-9)
        assertEquals(BrowEmotion.WORRIED, result.brows.emotion)
        assertEquals(0.6, result.brows.intensity, 1e-9)
        assertEquals(0.5, result.mouth.centerX, 1e-9)
        assertTrue(result.brows.browY < result.motion.eyeY)
        assertTrue(result.mouth.centerY > result.motion.eyeY)
        result.motion.validate()
        result.brows.validate()
    }

    @Test fun invalidGeometryFailsWithoutChangingSettings() {
        listOf(
            listOf(0.4, 0.3, 0.4, 0.3),
            listOf(0.3, 0.2, 0.7, 0.8),
            listOf(Double.NaN, 0.3, 0.7, 0.3),
            listOf(0.3, 0.95, 0.7, 0.95),
            listOf(-0.1, 0.3, 0.7, 0.3),
        ).forEach { p ->
            assertTrue(runCatching {
                PortraitFaceGeometry.suggest(1080, 1920, p[0], p[1], p[2], p[3])
            }.isFailure)
        }
    }
}
