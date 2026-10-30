package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutBlendShaderContractTest {
    @Test
    fun everyBlendModeHasStableDistinctGpuCode() {
        val codes = BlendMode.entries.map(LocalCutBlendShaderContract::modeCode)

        assertEquals((0..7).toList(), codes)
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun fragmentShaderSamplesBackdropAndSourceAndPerformsExplicitSourceOver() {
        val shader = LocalCutBlendShaderContract.fragmentShader

        assertTrue(shader.contains("uniform sampler2D uBackdropSampler;"))
        assertTrue(shader.contains("uniform sampler2D uSourceSampler;"))
        assertTrue(shader.contains("uniform float uBackdropAlphaScale;"))
        assertTrue(shader.contains("uniform float uSourceAlphaScale;"))
        assertTrue(shader.contains("backdrop.a *= uBackdropAlphaScale;"))
        assertTrue(shader.contains("source.a *= uSourceAlphaScale;"))
        assertTrue(shader.contains("float outputAlpha = source.a + backdrop.a * (1.0 - source.a);"))
        assertTrue(shader.contains("vec3 outputRgb = outputAlpha > 0.0 ? premultiplied / outputAlpha : vec3(0.0);"))
        assertFalse(shader.contains("glBlendFunc"))
    }

    @Test
    fun alphaScaleValidationFailsClosed() {
        assertEquals(0.0f, LocalCutBlendShaderContract.validateAlphaScale(0.0f))
        assertEquals(1.0f, LocalCutBlendShaderContract.validateAlphaScale(1.0f))
        assertTrue(runCatching { LocalCutBlendShaderContract.validateAlphaScale(Float.NaN) }.isFailure)
        assertTrue(runCatching { LocalCutBlendShaderContract.validateAlphaScale(-0.01f) }.isFailure)
        assertTrue(runCatching { LocalCutBlendShaderContract.validateAlphaScale(1.01f) }.isFailure)
    }
}
