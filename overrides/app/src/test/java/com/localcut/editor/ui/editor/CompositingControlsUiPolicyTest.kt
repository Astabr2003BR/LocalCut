package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.media.Media3CompositingEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositingControlsUiPolicyTest {
    @Test
    fun primaryMaskOpacityClampsAndStaysRenderable() {
        val source = CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.ELLIPSE)

        val low = CreativeToolsUiPolicy.setPrimaryMaskOpacity(source, -1.0)
        val high = CreativeToolsUiPolicy.setPrimaryMaskOpacity(source, 2.0)
        val middle = CreativeToolsUiPolicy.setPrimaryMaskOpacity(source, 0.65)

        assertEquals(0.0, low.masks.single().opacity, 0.0)
        assertEquals(1.0, high.masks.single().opacity, 0.0)
        assertEquals(0.65, middle.masks.single().opacity, 0.0)
        assertTrue(Media3CompositingEffectFactory.canRender(middle))
    }

    @Test
    fun chromaRefinementClampsSupportedShaderParameters() {
        val source = CreativeToolsUiPolicy.greenScreen(CompositingSettings(), enabled = true)
        val threshold = CreativeToolsUiPolicy.setChromaThreshold(source, 2.0)
        val softness = CreativeToolsUiPolicy.setChromaSoftness(threshold, -1.0)
        val spill = CreativeToolsUiPolicy.setChromaSpillSuppression(softness, 0.72)

        assertEquals(1.0, spill.chromaKey.threshold, 0.0)
        assertEquals(0.0, spill.chromaKey.softness, 0.0)
        assertEquals(0.72, spill.chromaKey.spillSuppression, 0.0)
        assertEquals(0.0, spill.chromaKey.edgeFeatherPx, 0.0)
        assertTrue(spill.validationIssues().isEmpty())
        assertTrue(Media3CompositingEffectFactory.canRender(spill))
    }

    @Test
    fun blendModeSelectionPreservesOtherCompositingAndAllowsEveryImplementedMode() {
        val source = CreativeToolsUiPolicy.greenScreen(
            CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.RECTANGLE),
            enabled = true,
        )

        BlendMode.entries.forEach { mode ->
            val updated = CreativeToolsUiPolicy.setBlendMode(source, mode)

            assertEquals(mode, updated.blendMode)
            assertEquals(source.masks, updated.masks)
            assertEquals(source.chromaKey, updated.chromaKey)
            assertEquals(source.crop, updated.crop)
            assertTrue(updated.validationIssues().isEmpty())
            assertTrue(Media3CompositingEffectFactory.canRender(updated))
        }
    }
}
