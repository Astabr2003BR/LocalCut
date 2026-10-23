package com.localcut.editor.media

import androidx.media3.common.C
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CreativeSuiteBackendTest {
    @Test
    fun constantSpeedProviderUsesConfiguredSpeedAndHasNoNextChange() {
        val provider = ConstantSpeedProvider(2.0f)

        assertEquals(2.0f, provider.getSpeed(0L), 0f)
        assertEquals(2.0f, provider.getSpeed(5_000_000L), 0f)
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(0L))
    }

    @Test
    fun media3TimeMapSupportAcceptsConstantAndForwardRampOnly() {
        assertTrue(Media3TimeMapSupport.canRender(TimeMap(baseSpeed = 2.0)))
        assertTrue(
            Media3TimeMapSupport.canRender(
                TimeMap(speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(1_000_000L, 2.0))),
            ),
        )
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(reverse = true)))
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(freezeSegments = listOf(FreezeSegment(0L, 100L, 0L)))))
    }

    @Test
    fun nativeEffectFactorySupportsCoreStaticEffects() {
        val effects = listOf(
            EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.2))),
            EffectInstance("contrast", "localcut.color.contrast", parameters = mapOf("amount" to EffectParameter.Number(1.2))),
            EffectInstance("exposure", "localcut.color.exposure", parameters = mapOf("stops" to EffectParameter.Number(1.0))),
            EffectInstance("saturation", "localcut.color.saturation", parameters = mapOf("amount" to EffectParameter.Number(1.5))),
            EffectInstance("temperature", "localcut.color.temperature", parameters = mapOf("amount" to EffectParameter.Number(0.5))),
            EffectInstance("tint", "localcut.color.tint", parameters = mapOf("amount" to EffectParameter.Number(-0.5))),
            EffectInstance(
                "hsl",
                "localcut.color.hsl",
                parameters = mapOf(
                    "hue" to EffectParameter.Number(45.0),
                    "saturation" to EffectParameter.Number(20.0),
                    "lightness" to EffectParameter.Number(-10.0),
                ),
            ),
            EffectInstance("blur", "localcut.blur.gaussian", parameters = mapOf("radius" to EffectParameter.Number(10.0))),
        )

        assertTrue(effects.all(Media3EffectStackFactory::canRender))
    }

    @Test
    fun customShaderBackendSupportsStaticProfessionalEffects() {
        val effects = listOf(
            EffectInstance("sharpen", "localcut.sharpen", parameters = mapOf("amount" to EffectParameter.Number(1.0))),
            EffectInstance("vignette", "localcut.vignette", parameters = mapOf("amount" to EffectParameter.Number(0.5))),
            EffectInstance("grain", "localcut.grain", parameters = mapOf("amount" to EffectParameter.Number(0.2), "size" to EffectParameter.Number(1.0), "seed" to EffectParameter.Number(42.0))),
            EffectInstance("split", "localcut.rgb_split", parameters = mapOf("amount" to EffectParameter.Number(8.0))),
            EffectInstance("mosaic", "localcut.mosaic", parameters = mapOf("blockSize" to EffectParameter.Number(12.0))),
            EffectInstance("glow", "localcut.glow", parameters = mapOf("radius" to EffectParameter.Number(8.0), "intensity" to EffectParameter.Number(1.0), "color" to EffectParameter.Color(0xFFFFFFFF.toInt()))),
            EffectInstance("ripple", "localcut.ripple", parameters = mapOf("amplitude" to EffectParameter.Number(12.0), "frequency" to EffectParameter.Number(4.0), "center" to EffectParameter.Point(0.5, 0.5))),
            EffectInstance("displacement", "localcut.displacement", parameters = mapOf("amount" to EffectParameter.Number(10.0), "center" to EffectParameter.Point(0.5, 0.5))),
        )

        assertTrue(effects.all(Media3EffectStackFactory::canRender))
    }

    @Test
    fun animatedOrUnsupportedMotionBlurEffectsAreNotReportedAsNative() {
        val motionBlur = EffectInstance("motion", "localcut.motion_blur")
        val animated = EffectInstance(
            "brightness",
            "localcut.color.brightness",
            animations = listOf(
                com.localcut.editor.domain.model.PropertyAnimation(
                    "amount",
                    listOf(com.localcut.editor.domain.model.PropertyKeyframe("k", 0L, com.localcut.editor.domain.model.PropertyValue.Scalar(0.2))),
                ),
            ),
        )

        assertFalse(Media3EffectStackFactory.canRender(motionBlur))
        assertFalse(Media3EffectStackFactory.canRender(animated))
    }
}
