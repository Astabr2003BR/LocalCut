package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.media.Media3EffectStackFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipToolsUiPolicyTest {
    @Test
    fun horizontalAndVerticalFlipToggleIndependentlyAndPreserveOtherEffects() {
        val grain = EffectInstance("grain", "localcut.grain")
        val horizontal = FlipToolsUiPolicy.toggleHorizontal(listOf(grain))
        assertTrue(FlipToolsUiPolicy.isHorizontalEnabled(horizontal))
        assertFalse(FlipToolsUiPolicy.isVerticalEnabled(horizontal))
        assertTrue(horizontal.any { it.id == grain.id })

        val both = FlipToolsUiPolicy.toggleVertical(horizontal)
        assertTrue(FlipToolsUiPolicy.isHorizontalEnabled(both))
        assertTrue(FlipToolsUiPolicy.isVerticalEnabled(both))
        assertTrue(both.all(Media3EffectStackFactory::canRender))

        val verticalOnly = FlipToolsUiPolicy.toggleHorizontal(both)
        assertFalse(FlipToolsUiPolicy.isHorizontalEnabled(verticalOnly))
        assertTrue(FlipToolsUiPolicy.isVerticalEnabled(verticalOnly))
        assertTrue(verticalOnly.any { it.id == grain.id })
    }
}
