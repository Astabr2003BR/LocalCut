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
import androidx.media3.effect.Crop
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskSpec
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
internal object Media3CompositingEffectFactory {
    fun canRender(settings: CompositingSettings): Boolean {
        if (settings.validationIssues().isNotEmpty()) return false
        if (settings.blendMode != BlendMode.NORMAL) return false
        if (settings.chromaKey.enabled && settings.chromaKey.edgeFeatherPx != 0.0) return false
        if (settings.masks.size > 1) return false
        val mask = settings.masks.singleOrNull()
        if (mask != null) {
            if (mask.kind !in setOf(MaskKind.RECTANGLE, MaskKind.ELLIPSE)) return false
            if (mask.animations.isNotEmpty()) return false
            if (mask.points.isNotEmpty() && mask.points.size < 2) return false
        }
        val crop = settings.crop
        if (crop.right <= crop.left || crop.bottom <= crop.top) return false
        return true
    }

    fun create(
        settings: CompositingSettings,
        outputWidth: Int,
        outputHeight: Int,
    ): List<Effect> {
        require(canRender(settings)) { "unsupported compositing state for Media3 backend" }
        require(outputWidth > 0 && outputHeight > 0) { "output dimensions must be positive" }
        return buildList {
            cropEffectOrNull(settings.crop)?.let(::add)
            if (settings.chromaKey.enabled || settings.masks.isNotEmpty()) {
                add(LocalCutCompositingEffect(settings))
            }
            presentationEffectOrNull(settings.crop, outputWidth, outputHeight)?.let(::add)
        }
    }

    private fun cropEffectOrNull(crop: CropSettings): Effect? {
        val explicitBounds = crop.left != 0.0 || crop.top != 0.0 || crop.right != 1.0 || crop.bottom != 1.0
        if (!explicitBounds) return null
        val left = (crop.left * 2.0 - 1.0).toFloat()
        val right = (crop.right * 2.0 - 1.0).toFloat()
        val bottom = (1.0 - crop.bottom * 2.0).toFloat()
        val top = (1.0 - crop.top * 2.0).toFloat()
        return Crop(left, right, bottom, top)
    }

    private fun presentationEffectOrNull(
        crop: CropSettings,
        outputWidth: Int,
        outputHeight: Int,
    ): Effect? {
        val layout = when (crop.mode) {
            CropMode.NONE -> return null
            CropMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
            CropMode.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            CropMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
        }
        return Presentation.createForWidthAndHeight(outputWidth, outputHeight, layout)
    }
}

@UnstableApi
internal class LocalCutCompositingEffect(
    private val settings: CompositingSettings,
) : GlEffect {
    init {
        require(Media3CompositingEffectFactory.canRender(settings)) { "unsupported compositing state" }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        !settings.chromaKey.enabled && settings.masks.isEmpty()

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LocalCutCompositingShaderProgram(settings, useHdr)
}

@UnstableApi
private class LocalCutCompositingShaderProgram(
    private val settings: CompositingSettings,
    useHdr: Boolean,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ useHdr,
    /* texturePoolCapacity = */ 1,
) {
    private val glProgram: GlProgram
    private val mask: MaskSpec? = settings.masks.singleOrNull()

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

            val chroma = settings.chromaKey
            glProgram.setFloatUniform("uChromaEnabled", if (chroma.enabled) 1f else 0f)
            glProgram.setFloatsUniform("uKeyColor", argbToRgb(chroma.keyArgb))
            glProgram.setFloatUniform("uThreshold", chroma.threshold.toFloat())
            glProgram.setFloatUniform("uSoftness", chroma.softness.toFloat().coerceAtLeast(0.0001f))
            glProgram.setFloatUniform("uSpillSuppression", chroma.spillSuppression.toFloat())

            val bounds = maskBounds(mask)
            glProgram.setFloatUniform("uMaskEnabled", if (mask != null) 1f else 0f)
            glProgram.setFloatUniform(
                "uMaskKind",
                when (mask?.kind) {
                    MaskKind.RECTANGLE -> 1f
                    MaskKind.ELLIPSE -> 2f
                    else -> 0f
                },
            )
            glProgram.setFloatsUniform("uMaskMin", floatArrayOf(bounds[0], bounds[1]))
            glProgram.setFloatsUniform("uMaskMax", floatArrayOf(bounds[2], bounds[3]))
            glProgram.setFloatUniform("uMaskInvert", if (mask?.invert == true) 1f else 0f)
            glProgram.setFloatUniform("uMaskOpacity", mask?.opacity?.toFloat() ?: 1f)
            glProgram.setFloatUniform("uMaskFeather", 0.000001f)
            glProgram.setFloatUniform("uMaskExpansion", 0f)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val minDimension = min(inputWidth, inputHeight).coerceAtLeast(1).toFloat()
        glProgram.setFloatUniform(
            "uMaskFeather",
            ((mask?.featherPx ?: 0.0).toFloat() / minDimension).coerceAtLeast(0.000001f),
        )
        glProgram.setFloatUniform(
            "uMaskExpansion",
            (mask?.expansionPx ?: 0.0).toFloat() / minDimension,
        )
        return Size(inputWidth, inputHeight)
    }

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
            uniform float uChromaEnabled;
            uniform vec3 uKeyColor;
            uniform float uThreshold;
            uniform float uSoftness;
            uniform float uSpillSuppression;
            uniform float uMaskEnabled;
            uniform float uMaskKind;
            uniform vec2 uMaskMin;
            uniform vec2 uMaskMax;
            uniform float uMaskInvert;
            uniform float uMaskOpacity;
            uniform float uMaskFeather;
            uniform float uMaskExpansion;
            varying vec2 vTexSamplingCoord;

            float rectangleMask(vec2 uv) {
                vec2 center = (uMaskMin + uMaskMax) * 0.5;
                vec2 halfSize = max((uMaskMax - uMaskMin) * 0.5 + vec2(uMaskExpansion), vec2(0.0001));
                vec2 d = abs(uv - center) - halfSize;
                float signedDistance = length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0);
                return 1.0 - smoothstep(-uMaskFeather, uMaskFeather, signedDistance);
            }

            float ellipseMask(vec2 uv) {
                vec2 center = (uMaskMin + uMaskMax) * 0.5;
                vec2 radius = max((uMaskMax - uMaskMin) * 0.5 + vec2(uMaskExpansion), vec2(0.0001));
                float signedDistance = (length((uv - center) / radius) - 1.0) * min(radius.x, radius.y);
                return 1.0 - smoothstep(-uMaskFeather, uMaskFeather, signedDistance);
            }

            void main() {
                vec4 source = texture2D(uTexSampler, vTexSamplingCoord);

                float chromaAlpha = 1.0;
                vec3 outputRgb = source.rgb;
                if (uChromaEnabled > 0.5) {
                    float colorDistance = distance(source.rgb, uKeyColor);
                    chromaAlpha = smoothstep(uThreshold, uThreshold + uSoftness, colorDistance);
                    vec3 keyDirection = normalize(max(uKeyColor, vec3(0.0001)));
                    float keyProjection = max(dot(source.rgb, keyDirection), 0.0);
                    outputRgb = max(
                        source.rgb - keyDirection * keyProjection * uSpillSuppression * (1.0 - chromaAlpha),
                        vec3(0.0)
                    );
                }

                float maskAlpha = 1.0;
                if (uMaskEnabled > 0.5) {
                    maskAlpha = uMaskKind < 1.5
                        ? rectangleMask(vTexSamplingCoord)
                        : ellipseMask(vTexSamplingCoord);
                    if (uMaskInvert > 0.5) {
                        maskAlpha = 1.0 - maskAlpha;
                    }
                    maskAlpha *= uMaskOpacity;
                }

                gl_FragColor = vec4(outputRgb, source.a * chromaAlpha * maskAlpha);
            }
        """.trimIndent()

        private fun maskBounds(mask: MaskSpec?): FloatArray {
            if (mask == null || mask.points.size < 2) {
                return floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f)
            }
            val first = mask.points[0]
            val second = mask.points[1]
            val left = min(first.x, second.x).coerceIn(0.0, 1.0).toFloat()
            val top = min(first.y, second.y).coerceIn(0.0, 1.0).toFloat()
            val right = max(first.x, second.x).coerceIn(0.0, 1.0).toFloat()
            val bottom = max(first.y, second.y).coerceIn(0.0, 1.0).toFloat()
            return floatArrayOf(left, top, right, bottom)
        }

        private fun argbToRgb(argb: Int): FloatArray = floatArrayOf(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }
}
