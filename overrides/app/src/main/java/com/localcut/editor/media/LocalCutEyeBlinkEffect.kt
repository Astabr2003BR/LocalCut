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
import kotlin.math.PI
import kotlin.math.sin

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

    /**
     * Safe, dimensionless movement values; the shader masks motion to the
     * portrait's central lower portion to avoid distorting face and background.
     * All preset variations are data, not executable code.
     */
    fun idleOffsets(
        presentationTimeUs: Long,
        swayStrength: Double,
        breathStrength: Double,
    ): FloatArray {
        require(swayStrength.isFinite() && swayStrength in 0.0..1.0)
        require(breathStrength.isFinite() && breathStrength in 0.0..1.0)
        val seconds = max(0L, presentationTimeUs) / 1_000_000.0
        return floatArrayOf(
            (0.008 * swayStrength * sin(seconds * 2.0 * PI / 4.2)).toFloat(),
            (0.010 * breathStrength * sin(seconds * 2.0 * PI / 3.3)).toFloat(),
        )
    }

    /**
     * Slow horizontal/vertical iris target, deterministic for preview and export.
     * This is a pixel-art approximation; no pupil segmentation or AI.
     */
    fun gazeOffsets(timeUs: Long, strength: Double): FloatArray {
        require(strength.isFinite() && strength in 0.0..1.0)
        val seconds = max(0L, timeUs) / 1_000_000.0
        return floatArrayOf(
            (0.009 * strength * sin(seconds * 2.0 * PI / 2.8 + 0.23)).toFloat(),
            (0.0035 * strength * sin(seconds * 2.0 * PI / 3.7 + 1.1)).toFloat(),
        )
    }

    /** Head and hair movement only sample a tightly feathered face ROI. */
    fun headOffsets(timeUs: Long, strength: Double): FloatArray {
        require(strength.isFinite() && strength in 0.0..1.0)
        val seconds = max(0L, timeUs) / 1_000_000.0
        return floatArrayOf(
            (0.004 * strength * sin(seconds * 2.0 * PI / 4.8)).toFloat(),
            (0.0025 * strength * sin(seconds * 2.0 * PI / 5.6 + 0.85)).toFloat(),
        )
    }

    fun hairOffsets(timeUs: Long, strength: Double): FloatArray {
        require(strength.isFinite() && strength in 0.0..1.0)
        val seconds = max(0L, timeUs) / 1_000_000.0
        return floatArrayOf(
            (0.005 * strength * sin(seconds * 2.0 * PI / 2.7 + 0.6)).toFloat(),
            (0.002 * strength * sin(seconds * 2.0 * PI / 3.5 + 0.3)).toFloat(),
        )
    }

    /**
     * Slight, reproducible blink-timing variation; no Random, no frame state,
     * no difference between preview and exported video.
     */
    fun naturalClosureAt(
        presentationTimeUs: Long,
        firstBlinkSeconds: Double,
        intervalSeconds: Double,
        durationSeconds: Double,
        variation: Double,
    ): Double {
        require(variation.isFinite() && variation in 0.0..1.0)
        if (variation == 0.0) {
            return closureAt(presentationTimeUs, firstBlinkSeconds, intervalSeconds, durationSeconds)
        }
        require(listOf(firstBlinkSeconds, intervalSeconds, durationSeconds).all(Double::isFinite))
        require(firstBlinkSeconds >= 0.0 && intervalSeconds >= 1.0 &&
            durationSeconds > 0.0 && durationSeconds < intervalSeconds)
        val seconds = max(0L, presentationTimeUs) / 1_000_000.0
        val nominal = kotlin.math.floor((seconds - firstBlinkSeconds) / intervalSeconds)
            .toLong()
        val from = maxOf(0L, nominal - 1L)
        val until = maxOf(0L, nominal + 1L)
        var closure = 0.0
        for (cycle in from..until) {
            val offset = sin(cycle.toDouble() * 2.37 + 0.9) *
                intervalSeconds * 0.13 * variation
            val phase = seconds - (firstBlinkSeconds + cycle * intervalSeconds + offset)
            if (phase >= 0.0 && phase < durationSeconds) {
                val t = (phase / durationSeconds).coerceIn(0.0, 1.0)
                val triangle = if (t <= 0.5) t * 2.0 else (1.0 - t) * 2.0
                val eased = triangle * triangle * (3.0 - 2.0 * triangle)
                closure = maxOf(closure, eased)
            }
        }
        return closure
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
            program.setFloatUniform("uLeftClosure", 0f)
            program.setFloatUniform("uRightClosure", 0f)
            program.setFloatsUniform("uIdleOffsets", floatArrayOf(0f, 0f))
            program.setFloatsUniform("uHeadOffsets", floatArrayOf(0f, 0f))
            program.setFloatsUniform("uHairOffsets", floatArrayOf(0f, 0f))
            program.setFloatsUniform("uGazeOffsets", floatArrayOf(0f, 0f))
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            val first = number("firstBlinkSeconds", 1.40)
            val interval = number("intervalSeconds", 3.20)
            val duration = number("durationSeconds", 0.18)
            val variation = number("blinkVariation", 0.0)
            val asymmetryDelayUs = (35_000.0 * number("blinkAsymmetry", 0.0)).toLong()
            val strength = number("blinkStrength", 1.0)
            program.setFloatUniform("uLeftClosure", (
                EyeBlinkMath.naturalClosureAt(
                    presentationTimeUs, first, interval, duration, variation,
                ) * strength
            ).toFloat())
            program.setFloatUniform("uRightClosure", (
                EyeBlinkMath.naturalClosureAt(
                    (presentationTimeUs - asymmetryDelayUs).coerceAtLeast(0L),
                    first, interval, duration, variation,
                ) * strength
            ).toFloat())
            program.setFloatsUniform("uIdleOffsets", EyeBlinkMath.idleOffsets(
                presentationTimeUs,
                number("swayStrength", 0.0),
                number("breathStrength", 0.0),
            ))
            program.setFloatsUniform("uHeadOffsets", EyeBlinkMath.headOffsets(
                presentationTimeUs, number("headMotionStrength", 0.0),
            ))
            program.setFloatsUniform("uHairOffsets", EyeBlinkMath.hairOffsets(
                presentationTimeUs, number("hairMotionStrength", 0.0),
            ))
            program.setFloatsUniform("uGazeOffsets", EyeBlinkMath.gazeOffsets(
                presentationTimeUs, number("gazeStrength", 0.0),
            ))
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
            uniform float uLeftClosure;
            uniform float uRightClosure;
            uniform vec2 uIdleOffsets;
            uniform vec2 uHeadOffsets;
            uniform vec2 uHairOffsets;
            uniform vec2 uGazeOffsets;
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            // Eye-local, darkness-aware resampling affects the iris rather than
            // dragging the entire eyelid or the background with the gaze.
            vec4 gazeOne(vec4 original, vec2 uv, vec2 center) {
                vec2 p = (uv - center) / max(uRadius, vec2(0.0001));
                float centerMask = 1.0 - smoothstep(0.22, 0.76, length(p));
                if (centerMask <= 0.001) return original;
                vec3 moved = texture2D(
                    uTexSampler, clampUv(uv - uGazeOffsets)
                ).rgb;
                vec3 weights = vec3(0.2126, 0.7152, 0.0722);
                float sourceDark = 1.0 - smoothstep(0.30, 0.74, dot(original.rgb, weights));
                float movedDark = 1.0 - smoothstep(0.30, 0.74, dot(moved, weights));
                float factor = centerMask * max(sourceDark, movedDark) * 0.86;
                return vec4(mix(original.rgb, moved, factor), original.a);
            }

            vec4 blinkOne(vec4 original, vec2 uv, vec2 center, float closure) {
                vec2 p = (uv - center) / max(uRadius, vec2(0.0001));
                float radius2 = dot(p, p);
                if (radius2 >= 1.0) return original;
                // The upper lid progressively descends across the eye.
                // Sampling nearby cheek skin covers the source iris, even
                // when the only input is a static portrait.
                float lidEdge = mix(1.15, -1.15, closure);
                float covered = smoothstep(lidEdge - 0.08, lidEdge + 0.08, p.y);
                float softRoi = 1.0 - smoothstep(0.82, 1.0, radius2);
                vec3 skin = texture2D(uTexSampler, clampUv(
                    vec2(uv.x, center.y - uRadius.y * 1.20)
                )).rgb;
                vec3 color = mix(original.rgb, skin, covered * softRoi);
                // A fine curved lid line appears at full closure.
                float line = (1.0 - smoothstep(0.025, 0.12, abs(p.y))) *
                    (1.0 - smoothstep(0.65, 0.98, abs(p.x))) *
                    smoothstep(0.80, 0.98, closure) * softRoi;
                color = mix(color, vec3(0.14, 0.085, 0.095), 0.86 * line);
                return vec4(color, original.a);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                // Localized, subpixel idle motion affects the lower part of
                // the portrait only. Face and outer background remain still.
                float horizontalBlend = smoothstep(0.04, 0.20, uv.x) *
                    (1.0 - smoothstep(0.80, 0.96, uv.x));
                float lowerBodyBlend = 1.0 - smoothstep(0.26, 0.63, uv.y);
                // Optional local 2D/2.5D motion: face and fringe regions
                // are softly bounded. Pure image warps, not 3D reconstruction.
                float faceX = smoothstep(0.15, 0.32, uv.x) *
                    (1.0 - smoothstep(0.68, 0.85, uv.x));
                float faceY = smoothstep(0.30, 0.49, uv.y) *
                    (1.0 - smoothstep(0.84, 0.97, uv.y));
                float faceBlend = faceX * faceY;
                float hairX = smoothstep(0.17, 0.32, uv.x) *
                    (1.0 - smoothstep(0.68, 0.83, uv.x));
                float hairY = smoothstep(0.65, 0.77, uv.y) *
                    (1.0 - smoothstep(0.94, 0.99, uv.y));
                vec2 sampleUv = clampUv(
                    uv +
                    uIdleOffsets * horizontalBlend * lowerBodyBlend +
                    uHeadOffsets * faceBlend +
                    uHairOffsets * hairX * hairY
                );
                vec4 original = texture2D(uTexSampler, sampleUv);
                // When strength=0, this preserves bit-identical legacy behavior.
                vec4 withGaze = original;
                if (length(uGazeOffsets) > 0.00001) {
                    withGaze = gazeOne(withGaze, sampleUv, uLeftEye);
                    withGaze = gazeOne(withGaze, sampleUv, uRightEye);
                }
                if (max(uLeftClosure, uRightClosure) <= 0.001) {
                    gl_FragColor = withGaze;
                    return;
                }
                vec4 withLeft = blinkOne(withGaze, sampleUv, uLeftEye, uLeftClosure);
                gl_FragColor = blinkOne(withLeft, sampleUv, uRightEye, uRightClosure);
            }
        """.trimIndent()
    }
}
