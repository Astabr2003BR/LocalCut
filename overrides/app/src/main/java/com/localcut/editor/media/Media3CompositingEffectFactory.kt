package com.localcut.editor.media

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropSettings

@OptIn(UnstableApi::class)
internal object Media3CompositingEffectFactory {
    fun canRender(settings: CompositingSettings): Boolean {
        if (settings.validationIssues().isNotEmpty()) return false
        if (settings.blendMode != BlendMode.NORMAL) return false
        if (settings.masks.isNotEmpty()) return false
        if (settings.crop != CropSettings()) return false
        if (settings.chromaKey.enabled && settings.chromaKey.edgeFeatherPx != 0.0) return false
        return true
    }

    fun create(settings: CompositingSettings): List<Effect> {
        require(canRender(settings)) { "unsupported compositing state for Media3 backend" }
        return if (settings.chromaKey.enabled) {
            listOf(LocalCutChromaKeyEffect(settings.chromaKey))
        } else {
            emptyList()
        }
    }
}

@UnstableApi
internal class LocalCutChromaKeyEffect(
    private val settings: ChromaKeySettings,
) : GlEffect {
    init {
        require(settings.validationIssues().isEmpty()) { "invalid chroma key settings" }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = !settings.enabled

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LocalCutChromaKeyShaderProgram(settings, useHdr)
}

@UnstableApi
private class LocalCutChromaKeyShaderProgram(
    private val settings: ChromaKeySettings,
    useHdr: Boolean,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ useHdr,
    /* texturePoolCapacity = */ 1,
) {
    private val glProgram: GlProgram

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
            glProgram.setFloatsUniform("uKeyColor", argbToRgb(settings.keyArgb))
            glProgram.setFloatUniform("uThreshold", settings.threshold.toFloat())
            glProgram.setFloatUniform("uSoftness", settings.softness.toFloat().coerceAtLeast(0.0001f))
            glProgram.setFloatUniform("uSpillSuppression", settings.spillSuppression.toFloat())
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    companion object {
        private val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vec4 texturePosition = vec4(
                    aFramePosition.x * 0.5 + 0.5,
                    aFramePosition.y * 0.5 + 0.5,
                    0.0,
                    1.0
                );
                vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """.trimIndent()

        private val FRAGMENT_SHADER = """
            #version 100
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec3 uKeyColor;
            uniform float uThreshold;
            uniform float uSoftness;
            uniform float uSpillSuppression;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 source = texture2D(uTexSampler, vTexSamplingCoord);
                float colorDistance = distance(source.rgb, uKeyColor);
                float alpha = smoothstep(uThreshold, uThreshold + uSoftness, colorDistance);

                vec3 keyDirection = normalize(max(uKeyColor, vec3(0.0001)));
                float keyProjection = max(dot(source.rgb, keyDirection), 0.0);
                vec3 spillReduced = max(
                    source.rgb - keyDirection * keyProjection * uSpillSuppression * (1.0 - alpha),
                    vec3(0.0)
                );
                gl_FragColor = vec4(spillReduced, source.a * alpha);
            }
        """.trimIndent()

        private fun argbToRgb(argb: Int): FloatArray = floatArrayOf(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }
}
