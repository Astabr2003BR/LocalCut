package com.localcut.editor.media

import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

@OptIn(UnstableApi::class)
object TransformEffectFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        opacity: Double,
        outputWidth: Int,
        outputHeight: Int,
        transformSpaceWidth: Int,
        transformSpaceHeight: Int,
        presentationStartUs: Long = 0L,
        propertyAnimations: List<PropertyAnimation> = emptyList(),
    ): List<Effect> {
        val matrixEffect = MatrixTransformation { presentationTimeUs ->
            val value = PropertyTransformRenderResolver.valueAt(
                transform = transform,
                keyframes = keyframes,
                propertyAnimations = propertyAnimations,
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
        return listOf(
            Presentation.createForWidthAndHeight(outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT),
            matrixEffect,
        )
    }
}
