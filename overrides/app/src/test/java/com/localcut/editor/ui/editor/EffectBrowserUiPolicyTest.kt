package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import com.localcut.editor.media.Media3EffectStackFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                EffectBrowserCategory.MOTION,
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
            val range = requireNotNull(EffectBrowserUiPolicy.adjustableRange(preset))
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
    fun advancedControlsExposeUsefulNumericParametersButHideTechnicalSeed() {
        val glow = EffectBrowserUiPolicy.presets(EffectBrowserCategory.LIGHT).single { it.typeId == "localcut.glow" }
        assertEquals(
            listOf("radius", "intensity"),
            EffectBrowserUiPolicy.adjustableParameters(emptyList(), glow).map { it.name },
        )

        val grain = EffectBrowserUiPolicy.presets(EffectBrowserCategory.TEXTURE).single()
        assertEquals(
            listOf("amount", "size"),
            EffectBrowserUiPolicy.adjustableParameters(emptyList(), grain).map { it.name },
        )

        val ripple = EffectBrowserUiPolicy.presets(EffectBrowserCategory.DISTORTION).single { it.typeId == "localcut.ripple" }
        assertEquals(
            listOf("amplitude", "frequency"),
            EffectBrowserUiPolicy.adjustableParameters(emptyList(), ripple).map { it.name },
        )

        val motionBlur = EffectBrowserUiPolicy.presets(EffectBrowserCategory.MOTION).single()
        assertEquals("localcut.motion_blur", motionBlur.typeId)
        assertEquals("Motion Blur", motionBlur.label)
        assertEquals(
            listOf("shutterAngle", "samples"),
            EffectBrowserUiPolicy.adjustableParameters(emptyList(), motionBlur).map { it.name },
        )
        assertEquals(
            listOf("Obturador", "Amostras"),
            EffectBrowserUiPolicy.adjustableParameters(emptyList(), motionBlur).map { it.label },
        )
    }

    @Test
    fun secondaryParameterUpdateUsesRegistryBoundsAndRemainsRenderable() {
        val glow = EffectBrowserUiPolicy.presets(EffectBrowserCategory.LIGHT).single { it.typeId == "localcut.glow" }
        val withRadius = EffectBrowserUiPolicy.setValue(emptyList(), glow, 18.0)
        val result = EffectBrowserUiPolicy.setParameter(withRadius, glow, "intensity", 99.0)
        val effect = result.single { it.typeId == glow.typeId }

        assertEquals(
            5.0,
            (effect.parameters.getValue("intensity") as EffectParameter.Number).value,
            0.0,
        )
        assertTrue(Media3EffectStackFactory.canRender(effect))
    }

    @Test
    fun motionBlurSamplesClampToRegistryBoundsAndStayRenderable() {
        val motionBlur = EffectBrowserUiPolicy.presets(EffectBrowserCategory.MOTION).single()
        val source = EffectBrowserUiPolicy.setValue(emptyList(), motionBlur, 180.0)
        val result = EffectBrowserUiPolicy.setParameter(source, motionBlur, "samples", 99.0)
        val effect = result.single()

        assertEquals(
            64.0,
            (effect.parameters.getValue("samples") as EffectParameter.Number).value,
            0.0,
        )
        assertTrue(Media3EffectStackFactory.canRender(effect))
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
    fun parameterlessFlipEffectsToggleWithoutTouchingOtherEffectsAndRemainRenderable() {
        val grain = CreativeToolsUiPolicy.setNumericEffect(
            emptyList(),
            "localcut.grain",
            "amount",
            0.2,
        ).single()
        val horizontal = EffectBrowserUiPolicy.toggleParameterlessEffect(
            listOf(grain),
            EffectBrowserUiPolicy.FLIP_HORIZONTAL_TYPE_ID,
        )

        assertTrue(EffectBrowserUiPolicy.isToggleEffectEnabled(horizontal, EffectBrowserUiPolicy.FLIP_HORIZONTAL_TYPE_ID))
        assertTrue(horizontal.any { it.id == grain.id })
        assertTrue(
            Media3EffectStackFactory.canRender(
                horizontal.single { it.typeId == EffectBrowserUiPolicy.FLIP_HORIZONTAL_TYPE_ID },
            ),
        )

        val cleared = EffectBrowserUiPolicy.toggleParameterlessEffect(
            horizontal,
            EffectBrowserUiPolicy.FLIP_HORIZONTAL_TYPE_ID,
        )
        assertFalse(EffectBrowserUiPolicy.isToggleEffectEnabled(cleared, EffectBrowserUiPolicy.FLIP_HORIZONTAL_TYPE_ID))
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
