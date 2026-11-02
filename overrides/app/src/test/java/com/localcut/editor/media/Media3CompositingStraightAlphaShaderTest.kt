package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CompositingStraightAlphaShaderTest {
    @Test
    fun chromaAndMaskShaderKeepsRgbStraightAndOnlyScalesAlpha() {
        val shader = LocalCutCompositingShaderProgram.fragmentShaderForTest()

        assertTrue(shader.contains("gl_FragColor = vec4(outputRgb, source.a * finalAlpha);"))
        assertFalse(shader.contains("gl_FragColor = vec4(outputRgb * finalAlpha, source.a * finalAlpha);"))
    }
}
