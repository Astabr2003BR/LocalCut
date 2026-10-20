package com.localcut.editor.media

import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay as Media3TextOverlay
import androidx.media3.effect.TextureOverlay
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment

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
        private val styledText = styledText(spec)

        override fun getText(presentationTimeUs: Long): SpannableString = styledText

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
            StaticOverlaySettings.Builder()
                .setAlphaScale(spec.alphaAt(presentationTimeUs))
                .setBackgroundFrameAnchor(spec.backgroundAnchorX, spec.backgroundAnchorY)
                .setOverlayFrameAnchor(0f, 0f)
                .setRotationDegrees(spec.rotationDegrees)
                .setScale(spec.textScale, spec.textScale)
                .build()
    }

    private fun styledText(spec: TextOverlayRenderSpec): SpannableString =
        SpannableString(spec.text).apply {
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
        }

    private fun TextHorizontalAlignment.toLayoutAlignment(): Layout.Alignment = when (this) {
        TextHorizontalAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        TextHorizontalAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextHorizontalAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}
