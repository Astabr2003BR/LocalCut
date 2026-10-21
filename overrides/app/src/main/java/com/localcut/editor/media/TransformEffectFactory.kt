package com.localcut.editor.media

import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

@OptIn(UnstableApi::class)
object TransformEffectFactory {
    fun create(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        opacity: Double,
        outputWidth: Int,
        outputHeight: Int,
        transformSpaceWidth: Int,
        transformSpaceHeight: Int,
        presentationStartUs: Long = 0L,
    ): List<Effect> {
        val matrixEffect = MatrixTransformation { presentationTimeUs ->
            val value = KeyframeRenderResolver.valueAt(
                transform = transform,
                keyframes = keyframes,
                presentationTimeUs = presentationTimeUs,
                presentationStartUs = presentationStartUs,
            )
            val matrix = Matrix()
            // Domain coordinates are pixels with +Y down. Media3 matrix coordinates are normalized.
            val dx = (value.x / (transformSpaceWidth / 2.0)).toFloat()
            val dy = (-value.y / (transformSpaceHeight / 2.0)).toFloat()
            matrix.postTranslate(dx, dy)
            matrix.postRotate(value.rotationDeg.toFloat())
            matrix.postScale(value.scale.toFloat(), value.scale.toFloat())
            matrix
        }
        val staticOpacity = if (keyframes.isEmpty()) {
            KeyframeRenderResolver.effectiveOpacity(opacity, transform)
        } else {
            opacity
        }
        return buildList {
            add(Presentation.createForWidthAndHeight(outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT))
            add(matrixEffect)
            if (staticOpacity != 1.0) add(AlphaScale(staticOpacity.toFloat()))
        }
    }
}
