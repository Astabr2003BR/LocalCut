package com.localcut.editor.media

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.effects.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutMouthWarpEffectTest {
    private fun animatedMouth(): EffectInstance = EffectInstance(
        id = "mouth",
        typeId = "localcut.character.mouth_warp",
        parameters = mapOf(
            "center" to EffectParameter.Point(0.5, 0.65),
            "radiusX" to EffectParameter.Number(0.10),
            "radiusY" to EffectParameter.Number(0.05),
            "openness" to EffectParameter.Number(0.0),
        ),
        animations = listOf(
            PropertyAnimation(
                propertyId = "openness",
                keyframes = listOf(
                    PropertyKeyframe("closed", 0L, PropertyValue.Scalar(0.0)),
                    PropertyKeyframe("open", 500_000L, PropertyValue.Scalar(1.0)),
                    PropertyKeyframe("closed-again", 1_000_000L, PropertyValue.Scalar(0.0)),
                ),
            ),
        ),
    )

    @Test
    fun topOriginMouthYMapsToGlBottomOrigin() {
        val (x, y) = LocalCutMouthWarpMath.glCenter(0.51, 0.68)
        assertEquals(0.51, x, 1e-9)
        assertEquals(0.32, y, 1e-9)
        assertEquals(0.5, LocalCutMouthWarpMath.glCenter(0.5, 0.5).second, 1e-9)
        assertTrue(runCatching { LocalCutMouthWarpMath.glCenter(0.5, Double.NaN) }.isFailure)
    }

    @Test
    fun registryDefinesValidatedMouthWarpParametersAndNormalizedCenter() {
        val valid = animatedMouth()
        assertTrue(EffectRegistry.definition(valid.typeId) != null)
        assertTrue(EffectRegistry.validationIssues(valid).isEmpty())

        val invalidCenter = valid.copy(
            parameters = valid.parameters + ("center" to EffectParameter.Point(1.1, 0.65)),
        )
        val invalidRadius = valid.copy(
            parameters = valid.parameters + ("radiusX" to EffectParameter.Number(0.0)),
        )
        assertTrue(EffectRegistry.validationIssues(invalidCenter).any { "center" in it && "range" in it })
        assertTrue(EffectRegistry.validationIssues(invalidRadius).any { "radiusX" in it && "range" in it })
    }

    @Test
    fun media3AcceptsAnimatedMouthWarpButStillRejectsAnimatedLegacyEffect() {
        assertTrue(Media3EffectStackFactory.canRender(animatedMouth()))

        val animatedBrightness = EffectInstance(
            id = "brightness",
            typeId = "localcut.color.brightness",
            parameters = mapOf("amount" to EffectParameter.Number(0.0)),
            animations = listOf(
                PropertyAnimation(
                    propertyId = "amount",
                    keyframes = listOf(
                        PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(1.0)),
                    ),
                ),
            ),
        )
        assertFalse(Media3EffectStackFactory.canRender(animatedBrightness))
    }

    @Test
    fun mouthWarpMathIsIdentityWhenClosedAndOutsideEllipseAndAlwaysClamped() {
        val closed = LocalCutMouthWarpMath.sampleUv(
            u = 0.5,
            v = 0.65,
            centerX = 0.5,
            centerY = 0.65,
            radiusX = 0.1,
            radiusY = 0.05,
            openness = 0.0,
        )
        assertEquals(0.5, closed.first, 1e-9)
        assertEquals(0.65, closed.second, 1e-9)

        val outside = LocalCutMouthWarpMath.sampleUv(
            u = 0.1,
            v = 0.1,
            centerX = 0.5,
            centerY = 0.65,
            radiusX = 0.1,
            radiusY = 0.05,
            openness = 1.0,
        )
        assertEquals(0.1, outside.first, 1e-9)
        assertEquals(0.1, outside.second, 1e-9)

        val inside = LocalCutMouthWarpMath.sampleUv(
            u = 0.5,
            v = 0.67,
            centerX = 0.5,
            centerY = 0.65,
            radiusX = 0.1,
            radiusY = 0.05,
            openness = 1.0,
        )
        assertTrue(kotlin.math.abs(inside.second - 0.67) > 1e-6)
        assertTrue(inside.first in 0.001..0.999)
        assertTrue(inside.second in 0.001..0.999)

        val edge = LocalCutMouthWarpMath.sampleUv(
            u = 0.9999,
            v = 0.9999,
            centerX = 0.99,
            centerY = 0.99,
            radiusX = 0.5,
            radiusY = 0.3,
            openness = 1.0,
        )
        assertTrue(edge.first in 0.001..0.999)
        assertTrue(edge.second in 0.001..0.999)
    }

    @Test
    fun visibleMouthMaskIsClosedAtSilenceAndOpensInsideUserMarkedRegion() {
        val cx = 0.51
        val cy = 0.49
        val rx = 0.07
        val ry = 0.025
        fun mask(x: Double, y: Double, openness: Double) =
            LocalCutMouthWarpMath.openingMask(x, y, cx, cy, rx, ry, openness)

        assertEquals(0.0, mask(cx, cy, 0.0), 0.0)
        assertEquals(0.0, mask(cx, cy, -1.0), 0.0)
        assertTrue("Strong speech must create visible mouth interior",
            mask(cx, cy, 1.0) > 0.95)
        assertTrue("Quiet speech must not render a full-sized cavity",
            mask(cx, cy, 0.10) < mask(cx, cy, 0.65))
        assertEquals(0.0, mask(cx + rx, cy, 1.0), 0.0)
        assertEquals(0.0, mask(cx, cy + ry, 1.0), 0.0)
        assertEquals(0.0, mask(0.12, 0.12, 1.0), 0.0)
        assertEquals(0.0, mask(cx, cy, 0.0), 0.0)
    }

    @Test
    fun mouthMaskRejectsInvalidOrNonFiniteGeometry() {
        assertTrue(runCatching { LocalCutMouthWarpMath.openingMask(
            Double.NaN, 0.5, 0.5, 0.5, 0.07, 0.025, 0.5) }.isFailure)
        assertTrue(runCatching { LocalCutMouthWarpMath.openingMask(
            0.5, 0.5, 0.5, 0.5, 0.0, 0.025, 0.5) }.isFailure)
        assertTrue(runCatching { LocalCutMouthWarpMath.openingMask(
            0.5, 0.5, 0.5, 0.5, 0.07, 0.025, Double.POSITIVE_INFINITY) }.isFailure)
    }

    @Test
    fun animatedOpennessEvaluatesAtPresentationTime() {
        val effect = animatedMouth()
        val atPeak = EffectRegistry.evaluate(effect, 500_000L)
        val openness = atPeak.parameters.getValue("openness") as EffectParameter.Number
        assertEquals(1.0, openness.value, 1e-9)
    }
}
