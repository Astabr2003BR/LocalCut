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

    @Test fun idleBodyOffsetsAreBoundedAndDefaultToZero() {
        val still = EyeBlinkMath.idleOffsets(1_000_000L, 0.0, 0.0)
        assertEquals(0.0, still[0].toDouble(), 0.0)
        assertEquals(0.0, still[1].toDouble(), 0.0)
        val moving = EyeBlinkMath.idleOffsets(1_000_000L, 1.0, 1.0)
        assertTrue(kotlin.math.abs(moving[0]) in 0.001f..0.0081f)
        assertTrue(kotlin.math.abs(moving[1]) in 0.001f..0.0101f)
        assertTrue(runCatching {
            EyeBlinkMath.idleOffsets(1_000L, Double.NaN, 0.3)
        }.isFailure)
        assertTrue(runCatching {
            EyeBlinkMath.idleOffsets(1_000L, 0.3, 1.2)
        }.isFailure)
        assertEquals(
            moving.toList(), EyeBlinkMath.idleOffsets(1_000_000L, 1.0, 1.0).toList(),
        )
    }

    @Test fun naturalBlinkAndHeadHairAreBoundedAndDeterministic() {
        val head = EyeBlinkMath.headOffsets(1_000_000L, 1.0)
        val hair = EyeBlinkMath.hairOffsets(1_000_000L, 1.0)
        assertTrue(kotlin.math.abs(head[0]) <= 0.0041f)
        assertTrue(kotlin.math.abs(head[1]) <= 0.0026f)
        assertTrue(kotlin.math.abs(hair[0]) <= 0.0051f)
        assertTrue(kotlin.math.abs(hair[1]) <= 0.0021f)
        assertEquals(listOf(0f, 0f), EyeBlinkMath.headOffsets(1_000_000L, 0.0).toList())
        assertEquals(listOf(0f, 0f), EyeBlinkMath.hairOffsets(1_000_000L, 0.0).toList())
        assertEquals(head.toList(), EyeBlinkMath.headOffsets(1_000_000L, 1.0).toList())
        assertTrue(runCatching { EyeBlinkMath.headOffsets(0L, Double.NaN) }.isFailure)
        assertTrue(runCatching { EyeBlinkMath.hairOffsets(0L, 1.5) }.isFailure)
        assertTrue(runCatching {
            EyeBlinkMath.naturalClosureAt(1_400_000L, 1.4, 3.2, 0.18, -0.1)
        }.isFailure)
        for (time in listOf(0L, 1_490_000L, 4_690_000L)) {
            assertEquals(EyeBlinkMath.closureAt(time, 1.4, 3.2, 0.18),
                EyeBlinkMath.naturalClosureAt(time, 1.4, 3.2, 0.18, 0.0), 0.0)
        }
        for (time in 0L..9_000_000L step 50_000L) {
            val closure = EyeBlinkMath.naturalClosureAt(time, 1.4, 3.2, 0.18, 0.35)
            assertTrue(closure.isFinite() && closure in 0.0..1.0)
            assertEquals(closure, EyeBlinkMath.naturalClosureAt(
                time, 1.4, 3.2, 0.18, 0.35), 0.0)
        }
    }

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
