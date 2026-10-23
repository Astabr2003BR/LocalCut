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

@UnstableApi
internal class LocalCutStylizeEffect(
    private val effect: EffectInstance,
) : GlEffect {
    init {
        require(effect.animations.isEmpty()) { "animated stylize effects require a time-aware parameter backend" }
        require(effect.typeId in SUPPORTED_TYPES) { "unsupported stylize effect ${effect.typeId}" }
        val issues = EffectRegistry.validationIssues(effect)
        require(issues.isEmpty()) { "invalid stylize effect ${effect.id}: ${issues.first()}" }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = when (effect.typeId) {
        "localcut.sharpen", "localcut.vignette", "localcut.grain", "localcut.rgb_split", "localcut.displacement" ->
            numberOrDefault(effect, primaryParameter(effect.typeId), 0.0) == 0.0
        "localcut.mosaic" -> numberOrDefault(effect, "blockSize", 1.0) <= 1.0
        "localcut.glow" -> numberOrDefault(effect, "intensity", 1.0) == 0.0 || numberOrDefault(effect, "radius", 0.0) == 0.0
        "localcut.ripple" -> numberOrDefault(effect, "amplitude", 0.0) == 0.0
        else -> false
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LocalCutStylizeShaderProgram(effect, useHdr)

    companion object {
        val SUPPORTED_TYPES = setOf(
            "localcut.sharpen",
            "localcut.vignette",
            "localcut.grain",
            "localcut.rgb_split",
            "localcut.mosaic",
            "localcut.glow",
            "localcut.ripple",
            "localcut.displacement",
        )

        private fun primaryParameter(typeId: String): String = when (typeId) {
            "localcut.mosaic" -> "blockSize"
            else -> "amount"
        }

        private fun numberOrDefault(effect: EffectInstance, name: String, default: Double): Double =
            (effect.parameters[name] as? EffectParameter.Number)?.value ?: default
    }
}

@UnstableApi
private class LocalCutStylizeShaderProgram(
    private val effect: EffectInstance,
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
            glProgram.setFloatUniform("uMode", modeFor(effect.typeId))
            glProgram.setFloatUniform("uA", parameterA(effect).toFloat())
            glProgram.setFloatUniform("uB", parameterB(effect).toFloat())
            glProgram.setFloatUniform("uSeed", number(effect, "seed", 0.0).toFloat())
            glProgram.setFloatsUniform("uColor", color(effect))
            glProgram.setFloatsUniform("uCenter", point(effect))
            glProgram.setFloatsUniform("uResolution", floatArrayOf(1f, 1f))
            glProgram.setFloatUniform("uTime", 0f)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        glProgram.setFloatsUniform(
            "uResolution",
            floatArrayOf(inputWidth.coerceAtLeast(1).toFloat(), inputHeight.coerceAtLeast(1).toFloat()),
        )
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
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

    private fun parameterA(effect: EffectInstance): Double = when (effect.typeId) {
        "localcut.sharpen" -> number(effect, "amount", 0.0)
        "localcut.vignette" -> number(effect, "amount", 0.0)
        "localcut.grain" -> number(effect, "amount", 0.0)
        "localcut.rgb_split" -> number(effect, "amount", 0.0)
        "localcut.mosaic" -> number(effect, "blockSize", 1.0)
        "localcut.glow" -> number(effect, "radius", 0.0)
        "localcut.ripple" -> number(effect, "amplitude", 0.0)
        "localcut.displacement" -> number(effect, "amount", 0.0)
        else -> 0.0
    }

    private fun parameterB(effect: EffectInstance): Double = when (effect.typeId) {
        "localcut.grain" -> number(effect, "size", 1.0)
        "localcut.glow" -> number(effect, "intensity", 1.0)
        "localcut.ripple" -> number(effect, "frequency", 1.0)
        else -> 0.0
    }

    private fun color(effect: EffectInstance): FloatArray {
        val argb = (effect.parameters["color"] as? EffectParameter.Color)?.argb ?: 0xFFFFFFFF.toInt()
        return floatArrayOf(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }

    private fun point(effect: EffectInstance): FloatArray {
        val point = effect.parameters["center"] as? EffectParameter.Point
        return floatArrayOf(point?.x?.toFloat() ?: 0.5f, point?.y?.toFloat() ?: 0.5f)
    }

    private fun number(effect: EffectInstance, name: String, default: Double): Double =
        (effect.parameters[name] as? EffectParameter.Number)?.value ?: default

    private fun modeFor(typeId: String): Float = when (typeId) {
        "localcut.sharpen" -> 1f
        "localcut.vignette" -> 2f
        "localcut.grain" -> 3f
        "localcut.rgb_split" -> 4f
        "localcut.mosaic" -> 5f
        "localcut.glow" -> 6f
        "localcut.ripple" -> 7f
        "localcut.displacement" -> 8f
        else -> error("unsupported stylize effect $typeId")
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
            uniform float uMode;
            uniform float uA;
            uniform float uB;
            uniform float uSeed;
            uniform vec3 uColor;
            uniform vec2 uCenter;
            uniform vec2 uResolution;
            uniform float uTime;
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            float randomValue(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233)) + uSeed + uTime * 17.0) * 43758.5453);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec2 texel = 1.0 / max(uResolution, vec2(1.0));
                vec4 source = texture2D(uTexSampler, clampUv(uv));
                vec4 result = source;

                if (uMode < 1.5) {
                    vec3 left = texture2D(uTexSampler, clampUv(uv - vec2(texel.x, 0.0))).rgb;
                    vec3 right = texture2D(uTexSampler, clampUv(uv + vec2(texel.x, 0.0))).rgb;
                    vec3 up = texture2D(uTexSampler, clampUv(uv - vec2(0.0, texel.y))).rgb;
                    vec3 down = texture2D(uTexSampler, clampUv(uv + vec2(0.0, texel.y))).rgb;
                    vec3 sharpened = source.rgb * (1.0 + 4.0 * uA) - (left + right + up + down) * uA;
                    result.rgb = clamp(sharpened, 0.0, 1.0);
                } else if (uMode < 2.5) {
                    float distanceFromCenter = length((uv - vec2(0.5)) * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0));
                    float vignette = smoothstep(0.25, 0.78, distanceFromCenter);
                    result.rgb *= 1.0 - clamp(uA, 0.0, 1.0) * vignette;
                } else if (uMode < 3.5) {
                    float grainSize = max(uB, 0.1);
                    vec2 grainCell = floor(uv * uResolution / grainSize);
                    float noise = randomValue(grainCell) - 0.5;
                    result.rgb = clamp(source.rgb + noise * uA, 0.0, 1.0);
                } else if (uMode < 4.5) {
                    vec2 offset = vec2(uA / max(uResolution.x, 1.0), 0.0);
                    float red = texture2D(uTexSampler, clampUv(uv + offset)).r;
                    float green = source.g;
                    float blue = texture2D(uTexSampler, clampUv(uv - offset)).b;
                    result.rgb = vec3(red, green, blue);
                } else if (uMode < 5.5) {
                    float blockSize = max(uA, 1.0);
                    vec2 pixel = floor(uv * uResolution / blockSize) * blockSize + vec2(blockSize * 0.5);
                    result = texture2D(uTexSampler, clampUv(pixel / uResolution));
                } else if (uMode < 6.5) {
                    vec2 radius = texel * max(uA, 0.0);
                    vec3 blurred = vec3(0.0);
                    blurred += texture2D(uTexSampler, clampUv(uv + vec2(radius.x, 0.0))).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv - vec2(radius.x, 0.0))).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv + vec2(0.0, radius.y))).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv - vec2(0.0, radius.y))).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv + radius)).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv - radius)).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv + vec2(radius.x, -radius.y))).rgb;
                    blurred += texture2D(uTexSampler, clampUv(uv + vec2(-radius.x, radius.y))).rgb;
                    blurred *= 0.125;
                    result.rgb = clamp(source.rgb + blurred * uColor * uB * 0.35, 0.0, 1.0);
                } else if (uMode < 7.5) {
                    vec2 delta = uv - uCenter;
                    float distanceFromCenter = length(delta);
                    vec2 direction = distanceFromCenter > 0.0001 ? delta / distanceFromCenter : vec2(0.0);
                    float displacement = sin(distanceFromCenter * uB * 40.0) * uA / max(min(uResolution.x, uResolution.y), 1.0);
                    result = texture2D(uTexSampler, clampUv(uv + direction * displacement));
                } else {
                    vec2 delta = uv - uCenter;
                    float amount = uA / max(min(uResolution.x, uResolution.y), 1.0);
                    result = texture2D(uTexSampler, clampUv(uv + delta * amount));
                }

                result.a = source.a;
                gl_FragColor = result;
            }
        """.trimIndent()
    }
}
