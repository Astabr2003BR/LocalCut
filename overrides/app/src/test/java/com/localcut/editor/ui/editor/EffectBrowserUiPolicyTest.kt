package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import com.localcut.editor.media.Media3EffectStackFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectBrowserUiPolicyTest {
    @Test
    fun categoriesAreStableAndEveryCategoryHasAtLeastOnePreset() {
        assertEquals(
            listOf(
                EffectBrowserCategory.COLOR,
                EffectBrowserCategory.LIGHT,
                EffectBrowserCategory.DETAIL,
                EffectBrowserCategory.BLUR,
                EffectBrowserCategory.TEXTURE,
                EffectBrowserCategory.DISTORTION,
            ),
            EffectBrowserUiPolicy.categories(),
        )
        assertTrue(EffectBrowserUiPolicy.categories().all { EffectBrowserUiPolicy.presets(it).isNotEmpty() })
    }

    @Test
    fun browserPresetsProduceEffectsAcceptedByTheMedia3Renderer() {
        EffectBrowserUiPolicy.categories().flatMap(EffectBrowserUiPolicy::presets).forEach { preset ->
            val effects = CreativeToolsUiPolicy.setNumericEffect(
                effects = emptyList(),
                typeId = preset.typeId,
                parameterName = preset.parameterName,
                value = preset.value,
            )
            val effect: EffectInstance = effects.single()
            assertTrue("${preset.label} must be registered", EffectRegistry.definition(effect.typeId) != null)
            assertTrue("${preset.label} must be renderable", Media3EffectStackFactory.canRender(effect))
        }
    }

    @Test
    fun adjustableRangeComesFromRegistryAndEndpointsRemainRenderable() {
        EffectBrowserUiPolicy.categories().flatMap(EffectBrowserUiPolicy::presets).forEach { preset ->
            val range = assertNotNull(EffectBrowserUiPolicy.adjustableRange(preset))
            assertTrue(preset.value in range)

            listOf(range.start, range.endInclusive).forEach { value ->
                val effect = CreativeToolsUiPolicy.setNumericEffect(
                    effects = emptyList(),
                    typeId = preset.typeId,
                    parameterName = preset.parameterName,
                    value = value,
                ).single()
                assertTrue("${preset.label} endpoint $value must render", Media3EffectStackFactory.canRender(effect))
            }
        }
    }

    @Test
    fun currentValueReadsExplicitParameterAndFallsBackToPresetDefault() {
        val preset = EffectBrowserUiPolicy.presets(EffectBrowserCategory.BLUR).single()
        assertEquals(preset.value, EffectBrowserUiPolicy.currentValue(emptyList(), preset), 0.0)

        val effects = CreativeToolsUiPolicy.setNumericEffect(
            emptyList(),
            preset.typeId,
            preset.parameterName,
            42.0,
        )
        assertEquals(42.0, EffectBrowserUiPolicy.currentValue(effects, preset), 0.0)
    }

    @Test
    fun setValueClampsToRegistryBoundsAndPreservesOtherEffects() {
        val preset = EffectBrowserUiPolicy.presets(EffectBrowserCategory.BLUR).single()
        val grain = CreativeToolsUiPolicy.setNumericEffect(
            emptyList(),
            "localcut.grain",
            "amount",
            0.2,
        ).single()
        val result = EffectBrowserUiPolicy.setValue(listOf(grain), preset, 999.0)

        val blur = result.single { it.typeId == preset.typeId }
        assertEquals(
            100.0,
            (blur.parameters.getValue(preset.parameterName) as EffectParameter.Number).value,
            0.0,
        )
        assertTrue(result.any { it.id == grain.id })
        assertTrue(Media3EffectStackFactory.canRender(blur))
    }

    @Test
    fun clearingOneCategoryPreservesEffectsFromOtherCategories() {
        val color = CreativeToolsUiPolicy.setNumericEffect(
            emptyList(),
            "localcut.color.brightness",
            "amount",
            0.2,
        ).single()
        val grain = CreativeToolsUiPolicy.setNumericEffect(
            emptyList(),
            "localcut.grain",
            "amount",
            0.2,
        ).single()
        val source = listOf(color, grain)

        assertTrue(EffectBrowserUiPolicy.hasEffectsInCategory(source, EffectBrowserCategory.COLOR))
        val cleared = EffectBrowserUiPolicy.clearCategory(source, EffectBrowserCategory.COLOR)

        assertFalse(EffectBrowserUiPolicy.hasEffectsInCategory(cleared, EffectBrowserCategory.COLOR))
        assertEquals(listOf(grain), cleared)
    }

    @Test
    fun labelsAreShortMobileFriendlyPortugueseNames() {
        EffectBrowserUiPolicy.categories().forEach { category ->
            val label = EffectBrowserUiPolicy.label(category)
            assertTrue(label.isNotBlank())
            assertTrue(label.length <= 12)
        }
    }
}
