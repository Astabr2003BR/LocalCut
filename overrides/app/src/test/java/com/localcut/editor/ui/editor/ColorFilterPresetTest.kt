package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.media.Media3EffectStackFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorFilterPresetTest {
    @Test
    fun allLocalCutFilterPresetsCompileToSupportedColorEffects() {
        ColorFilterPreset.entries.forEach { preset ->
            val effects = ColorFilterUiPolicy.applyPreset(emptyList(), preset)

            assertTrue("$preset must generate effects", effects.isNotEmpty())
            assertTrue(effects.all { it.typeId.startsWith("localcut.color.") })
            assertTrue(effects.all(Media3EffectStackFactory::canRender))
        }
    }

    @Test
    fun applyingFilterPreservesNonColorEffects() {
        val grain = EffectInstance(
            id = "grain",
            typeId = "localcut.grain",
            parameters = mapOf("amount" to EffectParameter.Number(0.2)),
        )

        val result = ColorFilterUiPolicy.applyPreset(listOf(grain), ColorFilterPreset.CINEMA)

        assertEquals(grain, result.single { it.typeId == "localcut.grain" })
        assertTrue(result.any { it.typeId.startsWith("localcut.color.") })
    }

    @Test
    fun neutralPresetRemovesOnlyColorEffects() {
        val source = listOf(
            EffectInstance("contrast", "localcut.color.contrast", parameters = mapOf("amount" to EffectParameter.Number(1.2))),
            EffectInstance("blur", "localcut.blur.gaussian", parameters = mapOf("radius" to EffectParameter.Number(4.0))),
        )

        val result = ColorFilterUiPolicy.applyPreset(source, ColorFilterPreset.NEUTRAL)

        assertTrue(result.none { it.typeId.startsWith("localcut.color.") })
        assertEquals(listOf("localcut.blur.gaussian"), result.map { it.typeId })
    }
}
