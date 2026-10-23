package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
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
