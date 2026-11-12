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

internal object LocalCutMouthWarpMath {
    /**
     * Mouth coordinates come from Android's top-left-origin image picker,
     * while the GL texture coordinates in this effect use a bottom-left origin.
     */
    fun glCenter(sourceX: Double, sourceY: Double): Pair<Double, Double> {
        require(sourceX.isFinite() && sourceY.isFinite())
        require(sourceX in 0.0..1.0 && sourceY in 0.0..1.0)
        return sourceX to (1.0 - sourceY)
    }

    fun sampleUv(
        u: Double,
        v: Double,
        centerX: Double,
        centerY: Double,
        radiusX: Double,
        radiusY: Double,
        openness: Double,
        widthScale: Double = 1.0,
        rounding: Double = 0.0,
    ): Pair<Double, Double> {
        require(listOf(u, v, centerX, centerY, radiusX, radiusY, openness, widthScale, rounding).all { it.isFinite() })
        require(radiusX > 0.0 && radiusY > 0.0)
        val safeOpen = openness.coerceIn(0.0, 1.0)
        val dx = u - centerX
        val dy = v - centerY
        val nx = dx / radiusX
        val ny = dy / radiusY
        val distanceSquared = nx * nx + ny * ny
        var sampleU = u
        var sampleV = v
        if (safeOpen > 0.001 && distanceSquared < 1.0) {
            val influence = (1.0 - distanceSquared) * (1.0 - distanceSquared)
            val rounded = rounding.coerceIn(0.0, 1.0)
            val widthRatio = widthScale.coerceIn(0.60, 1.30)
            val horizontalStretch = 1.0 + safeOpen * influence *
                ((widthRatio - 1.0) - 0.20 * rounded)
            val verticalStretch = 1.0 + 0.45 * safeOpen * influence * (1.0 + 0.22 * rounded)
            sampleU = centerX + dx / horizontalStretch
            sampleV = centerY + dy / verticalStretch
        }
        return sampleU.coerceIn(0.001, 0.999) to sampleV.coerceIn(0.001, 0.999)
    }

    /**
     * CPU reference for the visible inner-mouth mask used by the GL shader.
     * A closed mouth is completely transparent; the effect never edits source assets.
     * Used by unit tests, not by the rendering hot path.
     */
    fun openingMask(
        u: Double,
        v: Double,
        centerX: Double,
        centerY: Double,
        radiusX: Double,
        radiusY: Double,
        openness: Double,
    ): Double {
        require(listOf(u, v, centerX, centerY, radiusX, radiusY, openness).all { it.isFinite() })
        require(radiusX > 0.0 && radiusY > 0.0)
        val strength = openness.coerceIn(0.0, 1.0)
        if (strength <= 0.001) return 0.0
        val innerWidth = radiusX * 0.62
        val innerHeight = radiusY * (0.04 + 0.42 * strength)
        val dx = (u - centerX) / innerWidth
        val dy = (v - centerY) / innerHeight
        val r2 = dx * dx + dy * dy
        val blend = 1.0 - smoothStep(0.55, 1.0, r2)
        return blend * smoothStep(0.12, 0.58, strength) * 0.74
    }

    private fun smoothStep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}

@UnstableApi
internal class LocalCutMouthWarpEffect(
    private val effect: EffectInstance,
) : GlEffect {
    init {
        require(effect.typeId == TYPE_ID) { "unsupported mouth warp effect ${effect.typeId}" }
        require(effect.animations.all {
            it.propertyId in setOf("openness", "widthScale", "rounding", "teethVisibility")
        }) {
            "mouth warp only supports animated viseme parameters"
        }
        val issues = EffectRegistry.validationIssues(effect)
        require(issues.isEmpty()) { "invalid mouth warp ${effect.id}: ${issues.first()}" }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        effect.animations.isEmpty() && number(effect, "openness", 0.0) <= 0.0

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LocalCutMouthWarpShaderProgram(effect, useHdr)

    companion object {
        const val TYPE_ID = "localcut.character.mouth_warp"

        private fun number(effect: EffectInstance, name: String, default: Double): Double =
            (effect.parameters[name] as? EffectParameter.Number)?.value ?: default
    }
}

@UnstableApi
private class LocalCutMouthWarpShaderProgram(
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
            val evaluated = EffectRegistry.evaluate(effect, 0L)
            glProgram.setFloatsUniform("uCenter", center(evaluated))
            glProgram.setFloatsUniform("uRadius", radius(evaluated))
            glProgram.setFloatUniform("uOpenness", openness(evaluated).toFloat())
            glProgram.setFloatUniform("uWidthScale", number(evaluated, "widthScale", 1.0).toFloat())
            glProgram.setFloatUniform("uRounding", number(evaluated, "rounding", 0.0).toFloat())
            glProgram.setFloatUniform("uTeethVisibility", number(evaluated, "teethVisibility", 0.0).toFloat())
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val evaluated = EffectRegistry.evaluate(effect, max(0L, presentationTimeUs))
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uCenter", center(evaluated))
            glProgram.setFloatsUniform("uRadius", radius(evaluated))
            glProgram.setFloatUniform("uOpenness", openness(evaluated).toFloat())
            glProgram.setFloatUniform("uWidthScale", number(evaluated, "widthScale", 1.0).toFloat())
            glProgram.setFloatUniform("uRounding", number(evaluated, "rounding", 0.0).toFloat())
            glProgram.setFloatUniform("uTeethVisibility", number(evaluated, "teethVisibility", 0.0).toFloat())
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

    private fun center(effect: EffectInstance): FloatArray {
        val value = effect.parameters["center"] as? EffectParameter.Point ?: EffectParameter.Point(0.5, 0.65)
        val (glX, glY) = LocalCutMouthWarpMath.glCenter(value.x, value.y)
        return floatArrayOf(glX.toFloat(), glY.toFloat())
    }

    private fun radius(effect: EffectInstance): FloatArray = floatArrayOf(
        number(effect, "radiusX", 0.10).toFloat(),
        number(effect, "radiusY", 0.05).toFloat(),
    )

    private fun openness(effect: EffectInstance): Double = number(effect, "openness", 0.0).coerceIn(0.0, 1.0)

    private fun number(effect: EffectInstance, name: String, default: Double): Double =
        (effect.parameters[name] as? EffectParameter.Number)?.value ?: default

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
            uniform vec2 uCenter;
            uniform vec2 uRadius;
            uniform float uOpenness;
            uniform float uWidthScale;
            uniform float uRounding;
            uniform float uTeethVisibility;
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 original = texture2D(uTexSampler, clampUv(uv));
                float strength = clamp(uOpenness, 0.0, 1.0);

                // Exactly preserve the original portrait during silence. Also
                // avoid unneeded GPU work for every pixel outside the mouth ROI.
                if (strength <= 0.001) {
                    gl_FragColor = original;
                    return;
                }
                vec2 safeRadius = max(uRadius, vec2(0.0001));
                vec2 delta = uv - uCenter;
                vec2 normalized = delta / safeRadius;
                float distanceSquared = dot(normalized, normalized);
                if (distanceSquared >= 1.0) {
                    gl_FragColor = original;
                    return;
                }

                // Keep the original local deformation at the lip boundary.
                float edge = 1.0 - distanceSquared;
                float influence = edge * edge;
                float rounded = clamp(uRounding, 0.0, 1.0);
                float widthRatio = clamp(uWidthScale, 0.60, 1.30);
                // Inverse sampling: wide visemes expand the source lip line;
                // rounded/narrow visemes contract it. The feather is zero
                // at the ROI boundary, preserving adjacent facial features.
                float horizontalStretch = 1.0 + strength * influence *
                    ((widthRatio - 1.0) - 0.20 * rounded);
                float verticalStretch = 1.0 + 0.45 * strength * influence *
                    (1.0 + 0.22 * rounded);
                vec2 sampleUv = uCenter + delta / vec2(horizontalStretch, verticalStretch);
                vec4 warped = texture2D(uTexSampler, clampUv(sampleUv));

                // A broad opaque oval looks artificial on a still portrait.
                // Keep a shallow, feathered opening that retains the source lip
                // line and skin detail. Only the mouth ROI is processed.
                vec2 mouthRadius = safeRadius * vec2(
                    0.62 * widthRatio * (1.0 - 0.12 * rounded),
                    0.04 + 0.42 * strength * (1.0 + 0.22 * rounded)
                );
                vec2 mouthUv = delta / max(mouthRadius, vec2(0.0001));
                float mouthR2 = dot(mouthUv, mouthUv);
                float cavityMask =
                    (1.0 - smoothstep(0.55, 1.0, mouthR2)) *
                    smoothstep(0.12, 0.58, strength) * 0.74;
                vec3 cavityColor = mix(
                    vec3(0.29, 0.125, 0.16),
                    warped.rgb * vec3(0.40, 0.22, 0.24),
                    0.38
                );

                // GL Y points up. Put a very soft teeth highlight at the top
                // of the opening, not underneath it. Avoid a bright flat bar.
                float upperTeeth =
                    smoothstep(0.68, 0.95, strength) *
                    smoothstep(0.08, 0.35, mouthUv.y) *
                    (1.0 - smoothstep(0.56, 0.84, mouthUv.y)) *
                    (1.0 - smoothstep(0.45, 0.75, abs(mouthUv.x))) *
                    (0.24 + 0.28 * clamp(uTeethVisibility, 0.0, 1.0));
                cavityColor = mix(cavityColor, vec3(0.79, 0.68, 0.63), upperTeeth);

                gl_FragColor = vec4(
                    mix(warped.rgb, cavityColor, cavityMask),
                    warped.a
                );
            }
        """.trimIndent()
    }
}
