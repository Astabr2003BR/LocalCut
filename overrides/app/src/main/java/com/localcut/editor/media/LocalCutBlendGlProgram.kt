package com.localcut.editor.media

import android.opengl.GLES20
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.model.BlendMode

internal object LocalCutBlendShaderContract {
    fun modeCode(mode: BlendMode): Int = when (mode) {
        BlendMode.NORMAL -> 0
        BlendMode.MULTIPLY -> 1
        BlendMode.SCREEN -> 2
        BlendMode.OVERLAY -> 3
        BlendMode.DARKEN -> 4
        BlendMode.LIGHTEN -> 5
        BlendMode.ADD -> 6
        BlendMode.SUBTRACT -> 7
    }

    fun validateAlphaScale(alphaScale: Float): Float {
        require(alphaScale.isFinite() && alphaScale in 0f..1f) { "BLEND_ALPHA_SCALE_INVALID" }
        return alphaScale
    }

    const val vertexShader: String = """
        #version 100
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;

        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
        }
    """

    const val fragmentShader: String = """
        #version 100
        precision mediump float;
        uniform sampler2D uBackdropSampler;
        uniform sampler2D uSourceSampler;
        uniform float uBlendMode;
        uniform float uBackdropAlphaScale;
        uniform float uSourceAlphaScale;
        varying vec2 vTexSamplingCoord;

        vec3 blendRgb(vec3 backdrop, vec3 source) {
            if (uBlendMode < 0.5) {
                return source;
            }
            if (uBlendMode < 1.5) {
                return backdrop * source;
            }
            if (uBlendMode < 2.5) {
                return backdrop + source - backdrop * source;
            }
            if (uBlendMode < 3.5) {
                vec3 low = 2.0 * backdrop * source;
                vec3 high = 1.0 - 2.0 * (1.0 - backdrop) * (1.0 - source);
                return mix(low, high, step(vec3(0.5), backdrop));
            }
            if (uBlendMode < 4.5) {
                return min(backdrop, source);
            }
            if (uBlendMode < 5.5) {
                return max(backdrop, source);
            }
            if (uBlendMode < 6.5) {
                return min(vec3(1.0), backdrop + source);
            }
            return max(vec3(0.0), source - backdrop);
        }

        void main() {
            vec4 backdrop = texture2D(uBackdropSampler, vTexSamplingCoord);
            vec4 source = texture2D(uSourceSampler, vTexSamplingCoord);
            backdrop.a *= uBackdropAlphaScale;
            source.a *= uSourceAlphaScale;

            vec3 mixed = blendRgb(backdrop.rgb, source.rgb);
            float outputAlpha = source.a + backdrop.a * (1.0 - source.a);
            vec3 premultiplied =
                source.a * (1.0 - backdrop.a) * source.rgb +
                source.a * backdrop.a * mixed +
                (1.0 - source.a) * backdrop.a * backdrop.rgb;
            vec3 outputRgb = outputAlpha > 0.0 ? premultiplied / outputAlpha : vec3(0.0);
            gl_FragColor = vec4(clamp(outputRgb, 0.0, 1.0), clamp(outputAlpha, 0.0, 1.0));
        }
    """
}

@UnstableApi
internal class LocalCutBlendGlProgram {
    private val glProgram = GlProgram(
        LocalCutBlendShaderContract.vertexShader,
        LocalCutBlendShaderContract.fragmentShader,
    ).also { program ->
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    fun draw(
        backdrop: GlTextureInfo,
        source: GlTextureInfo,
        output: GlTextureInfo,
        mode: BlendMode,
        backdropAlphaScale: Float,
        sourceAlphaScale: Float,
    ) {
        require(
            backdrop.width == source.width &&
                backdrop.height == source.height &&
                backdrop.width == output.width &&
                backdrop.height == output.height,
        ) { "BLEND_TEXTURE_SIZE_MISMATCH" }
        val backdropAlpha = LocalCutBlendShaderContract.validateAlphaScale(backdropAlphaScale)
        val sourceAlpha = LocalCutBlendShaderContract.validateAlphaScale(sourceAlphaScale)

        GlUtil.focusFramebufferUsingCurrentContext(output.fboId, output.width, output.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtil.clearFocusedBuffers()

        glProgram.use()
        glProgram.setSamplerTexIdUniform("uBackdropSampler", backdrop.texId, 0)
        glProgram.setSamplerTexIdUniform("uSourceSampler", source.texId, 1)
        glProgram.setFloatUniform("uBlendMode", LocalCutBlendShaderContract.modeCode(mode).toFloat())
        glProgram.setFloatUniform("uBackdropAlphaScale", backdropAlpha)
        glProgram.setFloatUniform("uSourceAlphaScale", sourceAlpha)
        glProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    fun release() {
        glProgram.delete()
    }
}
