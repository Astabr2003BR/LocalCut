package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.media.Media3CompositingEffectFactory
import com.localcut.editor.media.Media3EffectStackFactory
import com.localcut.editor.media.Media3TimeMapSupport
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeToolsRendererContractTest {
    @Test
    fun exposedVisualEffectPresetsHaveEnabledMedia3Backends() {
        var effects = emptyList<EffectInstance>()
        val presets = listOf(
            Triple("localcut.color.brightness", "amount", 0.2),
            Triple("localcut.color.contrast", "amount", 1.2),
            Triple("localcut.color.saturation", "amount", 1.25),
            Triple("localcut.blur.gaussian", "radius", 8.0),
            Triple("localcut.sharpen", "amount", 1.0),
            Triple("localcut.vignette", "amount", 0.55),
            Triple("localcut.grain", "amount", 0.18),
            Triple("localcut.rgb_split", "amount", 8.0),
            Triple("localcut.mosaic", "blockSize", 12.0),
            Triple("localcut.glow", "radius", 10.0),
            Triple("localcut.ripple", "amplitude", 10.0),
            Triple("localcut.displacement", "amount", 12.0),
        )

        presets.forEach { (typeId, parameter, value) ->
            effects = CreativeToolsUiPolicy.setNumericEffect(effects, typeId, parameter, value)
        }

        assertTrue(effects.all(Media3EffectStackFactory::canRender))
    }

    @Test
    fun exposedCompositingPresetsHaveEnabledMedia3Backends() {
        listOf(MaskKind.RECTANGLE, MaskKind.ELLIPSE, MaskKind.FREEFORM).forEach { kind ->
            assertTrue(
                Media3CompositingEffectFactory.canRender(
                    CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), kind),
                ),
            )
        }

        assertTrue(
            Media3CompositingEffectFactory.canRender(
                CreativeToolsUiPolicy.greenScreen(CompositingSettings(), enabled = true),
            ),
        )

        listOf(CropMode.NONE, CropMode.FIT, CropMode.FILL, CropMode.STRETCH).forEach { mode ->
            assertTrue(
                Media3CompositingEffectFactory.canRender(
                    CreativeToolsUiPolicy.cropLayout(CompositingSettings(), mode),
                ),
            )
        }
    }

    @Test
    fun exposedTimingPresetsHaveEnabledMedia3Backends() {
        listOf(0.5, 1.0, 2.0).forEach { speed ->
            assertTrue(Media3TimeMapSupport.canRender(CreativeToolsUiPolicy.constantSpeed(TimeMap(), speed)))
        }
        assertTrue(Media3TimeMapSupport.canRender(CreativeToolsUiPolicy.accelerationRamp(3_000_000L)))
    }
}
