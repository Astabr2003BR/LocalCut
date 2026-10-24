package com.localcut.editor.media

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.TypefaceSpan
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay as Media3TextOverlay
import androidx.media3.effect.TextureOverlay
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
object Media3TextOverlayEffectFactory {
    fun create(
        project: Project,
        outputWidth: Int,
        outputHeight: Int,
    ): OverlayEffect? {
        val specs = TextOverlayRenderSpecCompiler.compile(
            project = project,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
        )
        if (specs.isEmpty()) return null

        val overlays = specs.map<TextOverlayRenderSpec, TextureOverlay> { spec ->
            DynamicTextOverlay(spec)
        }
        return OverlayEffect(overlays)
    }

    private class DynamicTextOverlay(
        private val spec: TextOverlayRenderSpec,
    ) : Media3TextOverlay() {
        override fun getText(presentationTimeUs: Long): SpannableString {
            val motion = spec.motionAt(presentationTimeUs)
            return styledText(spec, motion.visibleText)
        }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val motion = spec.motionAt(presentationTimeUs)
            return StaticOverlaySettings.Builder()
                .setAlphaScale(motion.alpha)
                .setBackgroundFrameAnchor(motion.anchorX, motion.anchorY)
                .setOverlayFrameAnchor(0f, 0f)
                .setRotationDegrees(spec.rotationDegrees + motion.rotationOffsetDeg)
                .setScale(
                    spec.textScale * motion.scaleMultiplier,
                    spec.textScale * motion.scaleMultiplier,
                )
                .build()
        }
    }

    private fun styledText(spec: TextOverlayRenderSpec, text: String): SpannableString =
        SpannableString(text).apply {
            if (isEmpty()) return@apply
            setSpan(
                ForegroundColorSpan(spec.style.fillArgb),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                AlignmentSpan.Standard(spec.alignment.toLayoutAlignment()),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            spec.style.font?.familyName?.takeIf { it.isNotBlank() }?.let { family ->
                setSpan(TypefaceSpan(family), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spec.style.letterSpacingEm != 0.0) {
                setSpan(
                    LetterSpacingSpan(spec.style.letterSpacingEm.toFloat()),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            spec.style.background?.let { background ->
                val alpha = (background.opacity * 255.0).roundToInt().coerceIn(0, 255)
                val rgb = background.argb and 0x00FFFFFF
                setSpan(
                    BackgroundColorSpan((alpha shl 24) or rgb),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            val shadow = spec.style.shadows.firstOrNull()
            if (shadow != null) {
                setSpan(
                    ShadowTextSpan(
                        color = applyOpacity(shadow.argb, shadow.opacity),
                        radius = shadow.radiusPx.toFloat(),
                        dx = shadow.offsetX.toFloat(),
                        dy = shadow.offsetY.toFloat(),
                    ),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else if (spec.style.shadowArgb != null && spec.style.shadowRadiusPx > 0.0) {
                setSpan(
                    ShadowTextSpan(
                        color = spec.style.shadowArgb,
                        radius = spec.style.shadowRadiusPx.toFloat(),
                        dx = spec.style.shadowOffsetX.toFloat(),
                        dy = spec.style.shadowOffsetY.toFloat(),
                    ),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

    private class LetterSpacingSpan(
        private val em: Float,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.letterSpacing = em
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.letterSpacing = em
        }
    }

    private class ShadowTextSpan(
        private val color: Int,
        private val radius: Float,
        private val dx: Float,
        private val dy: Float,
    ) : CharacterStyle() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.setShadowLayer(radius, dx, dy, color)
        }
    }

    private fun applyOpacity(argb: Int, opacity: Double): Int {
        val sourceAlpha = (argb ushr 24) and 0xFF
        val alpha = (sourceAlpha * opacity.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    private fun TextHorizontalAlignment.toLayoutAlignment(): Layout.Alignment = when (this) {
        TextHorizontalAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        TextHorizontalAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextHorizontalAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}
