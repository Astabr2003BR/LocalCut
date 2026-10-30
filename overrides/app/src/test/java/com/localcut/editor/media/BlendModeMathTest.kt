package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlendModeMathTest {
    private val backdrop = BlendRgba(0.2, 0.4, 0.8, 1.0)
    private val source = BlendRgba(0.7, 0.5, 0.25, 1.0)

    @Test
    fun opaqueBlendEquationsMatchReferenceValues() {
        assertColor(BlendRgba(0.7, 0.5, 0.25, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.NORMAL))
        assertColor(BlendRgba(0.14, 0.20, 0.20, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.MULTIPLY))
        assertColor(BlendRgba(0.76, 0.70, 0.85, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.SCREEN))
        assertColor(BlendRgba(0.28, 0.40, 0.70, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.OVERLAY))
        assertColor(BlendRgba(0.20, 0.40, 0.25, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.DARKEN))
        assertColor(BlendRgba(0.70, 0.50, 0.80, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.LIGHTEN))
        assertColor(BlendRgba(0.90, 0.90, 1.00, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.ADD))
        assertColor(BlendRgba(0.50, 0.10, 0.00, 1.0), BlendModeMath.blend(backdrop, source, BlendMode.SUBTRACT))
    }

    @Test
    fun halfTransparentSourceUsesSourceOverWithBlendFunction() {
        val halfSource = source.copy(a = 0.5)

        assertColor(
            BlendRgba(0.45, 0.45, 0.525, 1.0),
            BlendModeMath.blend(backdrop, halfSource, BlendMode.NORMAL),
        )
        assertColor(
            BlendRgba(0.17, 0.30, 0.50, 1.0),
            BlendModeMath.blend(backdrop, halfSource, BlendMode.MULTIPLY),
        )
    }

    @Test
    fun transparentSourceLeavesBackdropAndTransparentBackdropPreservesSourceColor() {
        assertColor(
            backdrop,
            BlendModeMath.blend(backdrop, source.copy(a = 0.0), BlendMode.SCREEN),
        )
        assertColor(
            source.copy(a = 0.4),
            BlendModeMath.blend(BlendRgba(0.1, 0.9, 0.3, 0.0), source.copy(a = 0.4), BlendMode.OVERLAY),
        )
    }

    @Test
    fun alphaCompositionIsPorterDuffSourceOver() {
        val result = BlendModeMath.blend(
            backdrop = BlendRgba(0.2, 0.4, 0.8, 0.25),
            source = BlendRgba(0.7, 0.5, 0.25, 0.5),
            mode = BlendMode.NORMAL,
        )

        assertEquals(0.625, result.a, 1e-12)
        assertColor(BlendRgba(0.6, 0.48, 0.36, 0.625), result)
    }

    @Test
    fun invalidColorComponentsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            BlendModeMath.blend(backdrop, BlendRgba(Double.NaN, 0.5, 0.5, 1.0), BlendMode.NORMAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlendModeMath.blend(backdrop, BlendRgba(1.1, 0.5, 0.5, 1.0), BlendMode.NORMAL)
        }
    }

    private fun assertColor(expected: BlendRgba, actual: BlendRgba) {
        assertEquals(expected.r, actual.r, 1e-9)
        assertEquals(expected.g, actual.g, 1e-9)
        assertEquals(expected.b, actual.b, 1e-9)
        assertEquals(expected.a, actual.a, 1e-9)
    }
}
