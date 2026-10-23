package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorToolsUiPolicyTest {
    @Test
    fun settingHslParameterPreservesOtherHslParameters() {
        val first = ColorToolsUiPolicy.setNumber(emptyList(), "localcut.color.hsl", "hue", 30.0)
        val second = ColorToolsUiPolicy.setNumber(first, "localcut.color.hsl", "saturation", 15.0)
        val hsl = second.single { it.typeId == "localcut.color.hsl" }

        assertEquals(EffectParameter.Number(30.0), hsl.parameters["hue"])
        assertEquals(EffectParameter.Number(15.0), hsl.parameters["saturation"])
    }

    @Test
    fun resetColorKeepsNonColorEffects() {
        val source = listOf(
            EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.2))),
            EffectInstance("hsl", "localcut.color.hsl", parameters = mapOf("hue" to EffectParameter.Number(30.0))),
            EffectInstance("grain", "localcut.grain", parameters = mapOf("amount" to EffectParameter.Number(0.2))),
        )

        val reset = ColorToolsUiPolicy.resetColorEffects(source)

        assertTrue(reset.none { it.typeId.startsWith("localcut.color.") })
        assertEquals(listOf("localcut.grain"), reset.map { it.typeId })
    }

    @Test
    fun valueReadsExplicitParameterOrDefault() {
        val effects = listOf(
            EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.25))),
        )

        assertEquals(0.25, ColorToolsUiPolicy.number(effects, "localcut.color.brightness", "amount", 0.0), 0.0)
        assertEquals(1.0, ColorToolsUiPolicy.number(effects, "localcut.color.contrast", "amount", 1.0), 0.0)
    }
}
