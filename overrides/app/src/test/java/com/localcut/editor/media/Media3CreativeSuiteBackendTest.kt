package com.localcut.editor.media

import androidx.media3.common.C
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
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
    fun media3TimeMapSupportAcceptsConstantForwardSpeedOnly() {
        assertTrue(Media3TimeMapSupport.canRender(TimeMap(baseSpeed = 2.0)))
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(reverse = true)))
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(speedPoints = listOf(com.localcut.editor.domain.model.SpeedPoint(0L, 1.0)))))
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(freezeSegments = listOf(com.localcut.editor.domain.model.FreezeSegment(0L, 100L, 0L)))))
    }

    @Test
    fun nativeEffectFactoryReportsCoreStaticEffectsAsRenderable() {
        val effects = listOf(
            EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.2))),
            EffectInstance("contrast", "localcut.color.contrast", parameters = mapOf("amount" to EffectParameter.Number(1.2))),
            EffectInstance("exposure", "localcut.color.exposure", parameters = mapOf("stops" to EffectParameter.Number(1.0))),
            EffectInstance("saturation", "localcut.color.saturation", parameters = mapOf("amount" to EffectParameter.Number(1.5))),
            EffectInstance("temperature", "localcut.color.temperature", parameters = mapOf("amount" to EffectParameter.Number(0.5))),
            EffectInstance("tint", "localcut.color.tint", parameters = mapOf("amount" to EffectParameter.Number(-0.5))),
            EffectInstance("blur", "localcut.blur.gaussian", parameters = mapOf("radius" to EffectParameter.Number(10.0))),
        )

        assertTrue(effects.all(Media3EffectStackFactory::canRender))
    }

    @Test
    fun animatedOrCustomShaderEffectsAreNotReportedAsNative() {
        val custom = EffectInstance("glow", "localcut.glow")
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

        assertFalse(Media3EffectStackFactory.canRender(custom))
        assertFalse(Media3EffectStackFactory.canRender(animated))
    }
}
