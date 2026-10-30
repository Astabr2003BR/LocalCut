package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutMotionBlurShaderContractTest {
    @Test
    fun shaderUsesCurrentFramePlusSevenOwnedHistorySamplers() {
        val shader = LocalCutMotionBlurShaderContract.fragmentShader

        assertEquals(8, LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES)
        assertTrue(shader.contains("uniform sampler2D uCurrentSampler;"))
        (0..6).forEach { index ->
            assertTrue(shader.contains("uniform sampler2D uHistory$index;"))
        }
        (0..7).forEach { index ->
            assertTrue(shader.contains("uniform float uWeight$index;"))
        }
        assertTrue(shader.contains("uniform float uSampleCount;"))
    }

    @Test
    fun shaderAccumulatesOnlyAvailableSamplesAndDoesNotSpatiallyBlur() {
        val shader = LocalCutMotionBlurShaderContract.fragmentShader

        assertTrue(shader.contains("texture2D(uCurrentSampler, vTexSamplingCoord) * uWeight0"))
        (0..6).forEach { historyIndex ->
            val sampleNumber = historyIndex + 2
            assertTrue(shader.contains("uSampleCount > ${sampleNumber - 0.5}"))
            assertTrue(shader.contains("texture2D(uHistory$historyIndex, vTexSamplingCoord) * uWeight${historyIndex + 1}"))
        }
        assertFalse(shader.contains("texel"))
        assertFalse(shader.contains("Gaussian"))
        assertFalse(shader.contains("offset"))
    }

    @Test
    fun copyPassIsRepresentedByCurrentFrameOnlyWeights() {
        val weights = LocalCutMotionBlurShaderContract.currentFrameOnlyWeights()

        assertEquals(8, weights.size)
        assertEquals(1f, weights[0])
        assertTrue(weights.drop(1).all { it == 0f })
    }
}
