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
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import kotlin.math.max

/**
 * Top-left source image coordinates are converted once for the GL texture.
 * Blink timing is pure and deterministic across preview and MP4 export.
 */
internal object EyeBlinkMath {
    fun glPoint(point: EffectParameter.Point): FloatArray {
        require(point.x.isFinite() && point.y.isFinite() &&
            point.x in 0.0..1.0 && point.y in 0.0..1.0)
        return floatArrayOf(point.x.toFloat(), (1.0 - point.y).toFloat())
    }

    fun closureAt(
        presentationTimeUs: Long,
        firstBlinkSeconds: Double,
        intervalSeconds: Double,
        durationSeconds: Double,
    ): Double {
        require(listOf(firstBlinkSeconds, intervalSeconds, durationSeconds).all { it.isFinite() })
        require(firstBlinkSeconds >= 0.0 && intervalSeconds >= 1.0 &&
            durationSeconds > 0.0 && durationSeconds < intervalSeconds)
        val seconds = max(0L, presentationTimeUs) / 1_000_000.0
        if (seconds < firstBlinkSeconds) return 0.0
        val phase = (seconds - firstBlinkSeconds) % intervalSeconds
        if (phase >= durationSeconds) return 0.0
        val normalized = (phase / durationSeconds).coerceIn(0.0, 1.0)
        val triangle = if (normalized <= 0.5) normalized * 2.0
            else (1.0 - normalized) * 2.0
        return triangle * triangle * (3.0 - 2.0 * triangle)
    }
}

@UnstableApi
internal class LocalCutEyeBlinkEffect(
    private val effect: EffectInstance,
) : GlEffect {
    init {
        require(effect.typeId == TYPE_ID) { "unsupported blink effect" }
        require(effect.animations.isEmpty()) { "blink uses its own deterministic clock" }
        require(EffectRegistry.validationIssues(effect).isEmpty()) { "invalid blink effect" }
    }

    // A recurring blink is not a static no-op, even without model keyframes.
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        EyeBlinkShaderProgram(effect, useHdr)

    companion object {
        const val TYPE_ID = "localcut.character.eye_blink"
    }
}

@UnstableApi
private class EyeBlinkShaderProgram(
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
            program.setFloatsUniform("uLeftEye", eye("leftEye", 0.37, 0.36))
            program.setFloatsUniform("uRightEye", eye("rightEye", 0.63, 0.36))
            program.setFloatsUniform("uRadius", floatArrayOf(
                number("radiusX", 0.09).toFloat(), number("radiusY", 0.045).toFloat(),
            ))
            program.setFloatUniform("uClosure", 0f)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uClosure", EyeBlinkMath.closureAt(
                presentationTimeUs,
                number("firstBlinkSeconds", 1.40),
                number("intervalSeconds", 3.20),
                number("durationSeconds", 0.18),
            ).toFloat())
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

    private fun eye(name: String, defaultX: Double, defaultY: Double): FloatArray =
        EyeBlinkMath.glPoint(effect.parameters[name] as? EffectParameter.Point
            ?: EffectParameter.Point(defaultX, defaultY))

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
            uniform vec2 uLeftEye;
            uniform vec2 uRightEye;
            uniform vec2 uRadius;
            uniform float uClosure;
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            vec4 blinkOne(vec4 original, vec2 uv, vec2 center) {
                vec2 p = (uv - center) / max(uRadius, vec2(0.0001));
                float radius2 = dot(p, p);
                if (radius2 >= 1.0) return original;
                // The upper lid progressively descends across the eye.
                // Sampling nearby cheek skin covers the source iris, even
                // when the only input is a static portrait.
                float lidEdge = mix(1.15, -1.15, uClosure);
                float covered = smoothstep(lidEdge - 0.08, lidEdge + 0.08, p.y);
                float softRoi = 1.0 - smoothstep(0.82, 1.0, radius2);
                vec3 skin = texture2D(uTexSampler, clampUv(
                    vec2(uv.x, center.y - uRadius.y * 1.20)
                )).rgb;
                vec3 color = mix(original.rgb, skin, covered * softRoi);
                // A fine curved lid line appears at full closure.
                float line = (1.0 - smoothstep(0.025, 0.12, abs(p.y))) *
                    (1.0 - smoothstep(0.65, 0.98, abs(p.x))) *
                    smoothstep(0.80, 0.98, uClosure) * softRoi;
                color = mix(color, vec3(0.14, 0.085, 0.095), 0.86 * line);
                return vec4(color, original.a);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 original = texture2D(uTexSampler, clampUv(uv));
                if (uClosure <= 0.001) {
                    gl_FragColor = original;
                    return;
                }
                vec4 withLeft = blinkOne(original, uv, uLeftEye);
                gl_FragColor = blinkOne(withLeft, uv, uRightEye);
            }
        """.trimIndent()
    }
}
