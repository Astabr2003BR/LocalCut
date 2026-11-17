package com.localcut.editor.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.localcut.editor.domain.animation.BrowEmotion
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry

/**
 * Optional eyebrow pixel deformation on user-calibrated regions.
 * It does not infer expression from a picture or touch TALK/eyes/audio.
 */
internal object BrowExpressionMath {
    fun shiftY(mode: BrowEmotion, intensity: Double, normalizedX: Double, leftEye: Boolean): Double {
        require(intensity.isFinite() && intensity in 0.0..1.0)
        require(normalizedX.isFinite() && normalizedX in -1.0..1.0)
        val inner = if (leftEye) (normalizedX + 1.0) / 2.0 else (1.0 - normalizedX) / 2.0
        val direction = when (mode) {
            BrowEmotion.NEUTRAL -> 0.0
            BrowEmotion.SURPRISED -> 1.0
            BrowEmotion.WORRIED -> -0.2 + inner * 1.1
            BrowEmotion.DETERMINED -> 0.2 - inner * 1.1
        }
        return (0.020 * intensity * direction).coerceIn(-0.022, 0.022)
    }
}

@UnstableApi
internal class LocalCutBrowExpressionEffect(private val effect: EffectInstance) : GlEffect {
    init {
        require(effect.typeId == TYPE_ID)
        require(effect.animations.isEmpty())
        require(EffectRegistry.validationIssues(effect).isEmpty())
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        (effect.parameters["emotion"] as? EffectParameter.Number)?.value == 0.0 ||
        (effect.parameters["intensity"] as? EffectParameter.Number)?.value == 0.0

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        BrowExpressionShaderProgram(effect, useHdr)

    companion object {
        const val TYPE_ID = "localcut.character.brow_expression"
    }
}

@UnstableApi
private class BrowExpressionShaderProgram(
    private val effect: EffectInstance,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram

    init {
        try {
            program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            program.setBufferAttribute(
                "aFramePosition", GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            program.setFloatsUniform("uLeftBrow", glPoint("leftBrow", 0.37, 0.29))
            program.setFloatsUniform("uRightBrow", glPoint("rightBrow", 0.63, 0.29))
            program.setFloatsUniform("uRadius", floatArrayOf(
                number("radiusX", 0.08).toFloat(), number("radiusY", 0.04).toFloat(),
            ))
            program.setFloatUniform("uEmotion", number("emotion", 0.0).toFloat())
            program.setFloatUniform("uIntensity", number("intensity", 0.0).toFloat())
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    private fun glPoint(name: String, x: Double, y: Double): FloatArray {
        val pt = effect.parameters[name] as? EffectParameter.Point ?: EffectParameter.Point(x, y)
        return floatArrayOf(pt.x.toFloat(), (1.0 - pt.y).toFloat())
    }

    private fun number(name: String, default: Double): Double =
        (effect.parameters[name] as? EffectParameter.Number)?.value ?: default

    private companion object {
        val VERTEX_SHADER = """
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
                    0.0, 1.0
                );
                vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 100
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uLeftBrow;
            uniform vec2 uRightBrow;
            uniform vec2 uRadius;
            uniform float uEmotion;
            uniform float uIntensity;
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            vec4 warpOne(vec4 base, vec2 uv, vec2 center, float isLeft) {
                vec2 local = (uv - center) / max(uRadius, vec2(0.001));
                if (abs(local.x) >= 1.25 || abs(local.y) >= 1.25) return base;
                float inside = 1.0 - smoothstep(0.48, 1.18, length(local));
                float inner = isLeft > 0.5 ?
                    clamp((local.x + 1.0) * 0.5, 0.0, 1.0) :
                    clamp((1.0 - local.x) * 0.5, 0.0, 1.0);
                float direction = 0.0;
                if (uEmotion > 0.5 && uEmotion < 1.5) {
                    direction = 1.0; // raised brows - surprise
                } else if (uEmotion > 1.5 && uEmotion < 2.5) {
                    direction = -0.2 + 1.1 * inner; // worried / inner lift
                } else if (uEmotion > 2.5) {
                    direction = 0.2 - 1.1 * inner; // determined / inner lower
                }
                float shift = 0.020 * clamp(uIntensity, 0.0, 1.0) * direction;
                // GL Y increases upwards: sample from below for raised brows.
                vec2 sampledUv = clampUv(uv - vec2(0.0, shift * inside));
                vec4 displaced = texture2D(uTexSampler, sampledUv);
                return mix(base, displaced, inside);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 source = texture2D(uTexSampler, uv);
                if (uIntensity <= 0.001 || uEmotion < 0.5) {
                    gl_FragColor = source;
                    return;
                }
                vec4 leftSide = warpOne(source, uv, uLeftBrow, 1.0);
                gl_FragColor = warpOne(leftSide, uv, uRightBrow, 0.0);
            }
        """.trimIndent()
    }
}
