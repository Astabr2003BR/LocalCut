package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.media.Media3CompositingEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskControlsUiPolicyTest {
    @Test
    fun maskRefinementUpdatesOnlyPrimaryUiMaskAndStaysRenderable() {
        val other = MaskSpec(id = "other", kind = MaskKind.RECTANGLE)
        val source = CreativeToolsUiPolicy.setMaskPreset(
            CompositingSettings(masks = listOf(other)),
            MaskKind.ELLIPSE,
        )

        val feathered = CreativeToolsUiPolicy.setPrimaryMaskFeather(source, 24.0)
        val expanded = CreativeToolsUiPolicy.setPrimaryMaskExpansion(feathered, -12.0)
        val inverted = CreativeToolsUiPolicy.setPrimaryMaskInverted(expanded, true)

        val primary = inverted.masks.single { it.id == CreativeToolsUiPolicy.UI_MASK_ID }
        assertEquals(24.0, primary.featherPx, 0.0)
        assertEquals(-12.0, primary.expansionPx, 0.0)
        assertTrue(primary.invert)
        assertEquals(other, inverted.masks.single { it.id == "other" })
        assertTrue(inverted.validationIssues().isEmpty())
        assertTrue(Media3CompositingEffectFactory.canRender(inverted))
    }

    @Test
    fun maskRefinementClampsMobileControlRange() {
        val source = CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.RECTANGLE)

        val feathered = CreativeToolsUiPolicy.setPrimaryMaskFeather(source, 999.0)
        val expandedHigh = CreativeToolsUiPolicy.setPrimaryMaskExpansion(feathered, 999.0)
        val expandedLow = CreativeToolsUiPolicy.setPrimaryMaskExpansion(expandedHigh, -999.0)

        assertEquals(
            200.0,
            feathered.masks.single { it.id == CreativeToolsUiPolicy.UI_MASK_ID }.featherPx,
            0.0,
        )
        assertEquals(
            -200.0,
            expandedLow.masks.single { it.id == CreativeToolsUiPolicy.UI_MASK_ID }.expansionPx,
            0.0,
        )
    }

    @Test
    fun maskRefinementIsNoOpWhenPrimaryUiMaskDoesNotExist() {
        val source = CompositingSettings(masks = listOf(MaskSpec(id = "other", kind = MaskKind.ELLIPSE)))

        assertEquals(source, CreativeToolsUiPolicy.setPrimaryMaskFeather(source, 16.0))
        assertEquals(source, CreativeToolsUiPolicy.setPrimaryMaskExpansion(source, 10.0))
        assertEquals(source, CreativeToolsUiPolicy.setPrimaryMaskInverted(source, true))
        assertFalse(source.masks.single().invert)
    }
}
