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
    fun sampleUv(
        u: Double,
        v: Double,
        centerX: Double,
        centerY: Double,
        radiusX: Double,
        radiusY: Double,
        openness: Double,
    ): Pair<Double, Double> {
        require(listOf(u, v, centerX, centerY, radiusX, radiusY, openness).all { it.isFinite() })
        require(radiusX > 0.0 && radiusY > 0.0)
        val safeOpen = openness.coerceIn(0.0, 1.0)
        val dx = u - centerX
        val dy = v - centerY
        val nx = dx / radiusX
        val ny = dy / radiusY
        val distanceSquared = nx * nx + ny * ny
        var sampleU = u
        var sampleV = v
        if (safeOpen > 0.0 && distanceSquared < 1.0) {
            val influence = (1.0 - distanceSquared) * (1.0 - distanceSquared)
            val stretch = 1.0 + 0.45 * safeOpen * influence
            sampleV = centerY + dy / stretch
        }
        return sampleU.coerceIn(0.001, 0.999) to sampleV.coerceIn(0.001, 0.999)
    }
}

@UnstableApi
internal class LocalCutMouthWarpEffect(
    private val effect: EffectInstance,
) : GlEffect {
    init {
        require(effect.typeId == TYPE_ID) { "unsupported mouth warp effect ${effect.typeId}" }
        require(effect.animations.all { it.propertyId == "openness" }) {
            "mouth warp only supports animated openness"
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
        return floatArrayOf(value.x.toFloat(), value.y.toFloat())
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
            varying vec2 vTexSamplingCoord;

            vec2 clampUv(vec2 uv) {
                return clamp(uv, vec2(0.001), vec2(0.999));
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec2 safeRadius = max(uRadius, vec2(0.0001));
                vec2 delta = uv - uCenter;
                vec2 normalized = delta / safeRadius;
                float distanceSquared = dot(normalized, normalized);
                vec2 sampleUv = uv;
                if (uOpenness > 0.0 && distanceSquared < 1.0) {
                    float edge = 1.0 - distanceSquared;
                    float influence = edge * edge;
                    float stretch = 1.0 + 0.45 * clamp(uOpenness, 0.0, 1.0) * influence;
                    sampleUv.y = uCenter.y + delta.y / stretch;
                }
                gl_FragColor = texture2D(uTexSampler, clampUv(sampleUv));
            }
        """.trimIndent()
    }
}
