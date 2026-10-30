package com.localcut.editor.media

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CropBackendTest {
    @Test
    fun validCropBoundsAndLayoutModesAreSupported() {
        listOf(CropMode.NONE, CropMode.FIT, CropMode.FILL, CropMode.STRETCH).forEach { mode ->
            val settings = CompositingSettings(
                crop = CropSettings(
                    mode = mode,
                    left = 0.1,
                    top = 0.2,
                    right = 0.9,
                    bottom = 0.8,
                ),
            )
            assertTrue("expected $mode crop to be renderable", Media3CompositingEffectFactory.canRender(settings))
        }
    }

    @Test
    fun layoutOnlyWithoutExplicitCropIsSupported() {
        listOf(CropMode.FIT, CropMode.FILL, CropMode.STRETCH).forEach { mode ->
            assertTrue(
                Media3CompositingEffectFactory.canRender(
                    CompositingSettings(crop = CropSettings(mode = mode)),
                ),
            )
        }
    }

    @Test
    fun zeroAreaCropIsRejectedByBackend() {
        val settings = CompositingSettings(
            crop = CropSettings(left = 0.5, top = 0.0, right = 0.5, bottom = 1.0),
        )
        assertFalse(Media3CompositingEffectFactory.canRender(settings))
    }
}
