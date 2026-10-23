package com.localcut.editor.effects

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectRegistryTest {
    @Test
    fun registryExposesCoreProfessionalEffects() {
        val ids = EffectRegistry.definitions.map { it.typeId }.toSet()

        assertTrue("localcut.color.brightness" in ids)
        assertTrue("localcut.color.contrast" in ids)
        assertTrue("localcut.color.exposure" in ids)
        assertTrue("localcut.color.saturation" in ids)
        assertTrue("localcut.color.temperature" in ids)
        assertTrue("localcut.color.tint" in ids)
        assertTrue("localcut.blur.gaussian" in ids)
        assertTrue("localcut.sharpen" in ids)
        assertTrue("localcut.glow" in ids)
        assertTrue("localcut.vignette" in ids)
        assertTrue("localcut.grain" in ids)
        assertTrue("localcut.rgb_split" in ids)
        assertTrue("localcut.mosaic" in ids)
        assertTrue("localcut.ripple" in ids)
        assertTrue("localcut.displacement" in ids)
        assertTrue("localcut.motion_blur" in ids)
    }

    @Test
    fun numericBoundsAreValidated() {
        val effect = EffectInstance(
            id = "fx",
            typeId = "localcut.color.brightness",
            parameters = mapOf("amount" to EffectParameter.Number(2.0)),
        )

        assertTrue(EffectRegistry.validationIssues(effect).any { "amount" in it && "range" in it })
    }

    @Test
    fun animatedParameterOverridesStaticValueAtRequestedTime() {
        val effect = EffectInstance(
            id = "fx",
            typeId = "localcut.color.brightness",
            parameters = mapOf("amount" to EffectParameter.Number(0.0)),
            animations = listOf(
                PropertyAnimation(
                    "amount",
                    listOf(
                        PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(1.0)),
                    ),
                ),
            ),
        )

        val evaluated = EffectRegistry.evaluate(effect, 500_000L)

        assertEquals(0.5, (evaluated.parameters.getValue("amount") as EffectParameter.Number).value, 1e-9)
    }

    @Test
    fun unknownEffectTypeSurvivesAsOpaqueState() {
        val effect = EffectInstance(
            id = "future",
            typeId = "thirdparty.future.effect",
            parameters = mapOf("amount" to EffectParameter.Number(0.5)),
        )

        assertNull(EffectRegistry.definition(effect.typeId))
        assertTrue(EffectRegistry.validationIssues(effect).isEmpty())
    }

    @Test
    fun wrongAnimatedValueTypeIsRejected() {
        val effect = EffectInstance(
            id = "fx",
            typeId = "localcut.color.brightness",
            animations = listOf(
                PropertyAnimation(
                    "amount",
                    listOf(PropertyKeyframe("a", 0L, PropertyValue.Point(0.0, 0.0))),
                ),
            ),
        )

        assertTrue(EffectRegistry.validationIssues(effect).any { "type" in it })
    }
}
