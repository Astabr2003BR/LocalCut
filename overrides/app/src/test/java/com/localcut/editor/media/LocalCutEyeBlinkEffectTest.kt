package com.localcut.editor.media

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutEyeBlinkEffectTest {
    private fun effect(): EffectInstance = EffectInstance(
        id = "eye-blink",
        typeId = LocalCutEyeBlinkEffect.TYPE_ID,
        parameters = mapOf(
            "leftEye" to EffectParameter.Point(0.37, 0.36),
            "rightEye" to EffectParameter.Point(0.63, 0.36),
            "radiusX" to EffectParameter.Number(0.085),
            "radiusY" to EffectParameter.Number(0.042),
            "firstBlinkSeconds" to EffectParameter.Number(1.4),
            "intervalSeconds" to EffectParameter.Number(3.2),
            "durationSeconds" to EffectParameter.Number(0.18),
        ),
    )

    @Test fun correctTopLeftToGlYAndPreserveX() {
        val v = EyeBlinkMath.glPoint(EffectParameter.Point(0.37, 0.36))
        assertEquals(0.37, v[0].toDouble(), 1e-5)
        assertEquals(0.64, v[1].toDouble(), 1e-5)
        assertTrue(runCatching { EyeBlinkMath.glPoint(
            EffectParameter.Point(0.37, Double.NaN)) }.isFailure)
    }

    @Test fun blinkIsClosedOnlyAtScheduledPeakAndRepeats() {
        assertEquals(0.0, EyeBlinkMath.closureAt(0L, 1.4, 3.2, 0.18), 0.0)
        assertEquals(0.0, EyeBlinkMath.closureAt(1_390_000L, 1.4, 3.2, 0.18), 0.0)
        assertEquals(1.0, EyeBlinkMath.closureAt(1_490_000L, 1.4, 3.2, 0.18), 0.002)
        assertEquals(0.0, EyeBlinkMath.closureAt(1_600_000L, 1.4, 3.2, 0.18), 0.0)
        assertEquals(1.0, EyeBlinkMath.closureAt(4_690_000L, 1.4, 3.2, 0.18), 0.002)
        assertEquals(0.0, EyeBlinkMath.closureAt(4_900_000L, 1.4, 3.2, 0.18), 0.0)
        assertEquals(0.0, EyeBlinkMath.closureAt(-1L, 1.4, 3.2, 0.18), 0.0)
        assertTrue(runCatching { EyeBlinkMath.closureAt(1L, 1.4, Double.NaN, 0.18) }.isFailure)
    }

    @Test fun renderFactoryAcceptsBlinkButRejectsInvalidEyeParameters() {
        val valid = effect()
        assertTrue(EffectRegistry.validationIssues(valid).isEmpty())
        assertTrue(Media3EffectStackFactory.canRender(valid))
        assertFalse(LocalCutEyeBlinkEffect(valid).isNoOp(1080, 1920))

        val overlap = valid.copy(parameters = valid.parameters +
            ("rightEye" to EffectParameter.Point(0.40, 0.36)))
        assertTrue(EffectRegistry.validationIssues(overlap).isNotEmpty())
        assertFalse(Media3EffectStackFactory.canRender(overlap))

        val offCanvas = valid.copy(parameters = valid.parameters +
            ("leftEye" to EffectParameter.Point(-0.4, 0.36)))
        assertTrue(EffectRegistry.validationIssues(offCanvas).isNotEmpty())
        assertFalse(Media3EffectStackFactory.canRender(offCanvas))
    }
}
