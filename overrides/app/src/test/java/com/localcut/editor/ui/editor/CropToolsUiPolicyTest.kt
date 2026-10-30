package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.media.Media3CompositingEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropToolsUiPolicyTest {
    @Test
    fun manualCropUpdatesEachEdgeAndRemainsRenderable() {
        val source = CompositingSettings(crop = CropSettings(mode = CropMode.FILL))
        val edited = source
            .let { CropToolsUiPolicy.setLeft(it, 0.10) }
            .let { CropToolsUiPolicy.setTop(it, 0.20) }
            .let { CropToolsUiPolicy.setRight(it, 0.85) }
            .let { CropToolsUiPolicy.setBottom(it, 0.90) }

        assertEquals(0.10, edited.crop.left, 0.0)
        assertEquals(0.20, edited.crop.top, 0.0)
        assertEquals(0.85, edited.crop.right, 0.0)
        assertEquals(0.90, edited.crop.bottom, 0.0)
        assertTrue(edited.validationIssues().isEmpty())
        assertTrue(Media3CompositingEffectFactory.canRender(edited))
    }

    @Test
    fun edgeEditsClampBeforeTheyCanInvertCrop() {
        val source = CompositingSettings(
            crop = CropSettings(left = 0.20, top = 0.25, right = 0.80, bottom = 0.75),
        )
        val left = CropToolsUiPolicy.setLeft(source, 1.0)
        val right = CropToolsUiPolicy.setRight(source, 0.0)
        val top = CropToolsUiPolicy.setTop(source, 1.0)
        val bottom = CropToolsUiPolicy.setBottom(source, 0.0)

        listOf(left, right, top, bottom).forEach { result ->
            assertTrue(result.crop.right > result.crop.left)
            assertTrue(result.crop.bottom > result.crop.top)
            assertTrue(Media3CompositingEffectFactory.canRender(result))
        }
    }

    @Test
    fun resetPreservesLayoutModeAndRestoresFullFrame() {
        val source = CompositingSettings(
            crop = CropSettings(
                mode = CropMode.STRETCH,
                left = 0.15,
                top = 0.12,
                right = 0.82,
                bottom = 0.88,
            ),
        )
        val reset = CropToolsUiPolicy.resetBounds(source)

        assertEquals(CropMode.STRETCH, reset.crop.mode)
        assertEquals(0.0, reset.crop.left, 0.0)
        assertEquals(0.0, reset.crop.top, 0.0)
        assertEquals(1.0, reset.crop.right, 0.0)
        assertEquals(1.0, reset.crop.bottom, 0.0)
        assertTrue(Media3CompositingEffectFactory.canRender(reset))
    }
}
