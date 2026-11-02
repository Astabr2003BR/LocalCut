package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import kotlin.math.max
import kotlin.math.min

/** Normalized straight-alpha RGBA color used as the reference for future GPU blend shaders. */
data class BlendRgba(
    val r: Double,
    val g: Double,
    val b: Double,
    val a: Double,
) {
    init {
        listOf(r, g, b, a).forEach { component ->
            require(component.isFinite() && component in 0.0..1.0) {
                "blend color components must be finite and in 0..1"
            }
        }
    }
}

/**
 * Canonical LocalCut blend equations.
 *
 * [source] is the upper/input layer and [backdrop] is the already-composited content below it.
 * Color blending is applied first, then Porter-Duff source-over alpha composition.
 * SUBTRACT follows FFmpeg top-minus-bottom semantics: max(0, source - backdrop).
 */
object BlendModeMath {
    fun blend(backdrop: BlendRgba, source: BlendRgba, mode: BlendMode): BlendRgba {
        val alphaSource = source.a
        val alphaBackdrop = backdrop.a
        val outputAlpha = alphaSource + alphaBackdrop * (1.0 - alphaSource)
        if (outputAlpha == 0.0) return BlendRgba(0.0, 0.0, 0.0, 0.0)

        fun component(backdropValue: Double, sourceValue: Double): Double {
            val mixed = blendChannel(backdropValue, sourceValue, mode)
            // W3C source-over blending, expressed in premultiplied output color then
            // converted back to straight alpha for the reference value.
            val premultiplied =
                alphaSource * (1.0 - alphaBackdrop) * sourceValue +
                    alphaSource * alphaBackdrop * mixed +
                    (1.0 - alphaSource) * alphaBackdrop * backdropValue
            return clamp01(premultiplied / outputAlpha)
        }

        return BlendRgba(
            r = component(backdrop.r, source.r),
            g = component(backdrop.g, source.g),
            b = component(backdrop.b, source.b),
            a = clamp01(outputAlpha),
        )
    }

    fun blendChannel(backdrop: Double, source: Double, mode: BlendMode): Double {
        require(backdrop.isFinite() && backdrop in 0.0..1.0) { "backdrop channel must be in 0..1" }
        require(source.isFinite() && source in 0.0..1.0) { "source channel must be in 0..1" }
        return clamp01(
            when (mode) {
                BlendMode.NORMAL -> source
                BlendMode.MULTIPLY -> backdrop * source
                BlendMode.SCREEN -> backdrop + source - backdrop * source
                BlendMode.OVERLAY -> if (backdrop <= 0.5) {
                    2.0 * backdrop * source
                } else {
                    1.0 - 2.0 * (1.0 - backdrop) * (1.0 - source)
                }
                BlendMode.DARKEN -> min(backdrop, source)
                BlendMode.LIGHTEN -> max(backdrop, source)
                BlendMode.ADD -> backdrop + source
                BlendMode.SUBTRACT -> source - backdrop
            },
        )
    }

    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)
}
