package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.TransformValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class PreviewSelectionRect(
    val leftPx: Double,
    val topPx: Double,
    val rightPx: Double,
    val bottomPx: Double,
) {
    init {
        require(listOf(leftPx, topPx, rightPx, bottomPx).all { it.isFinite() }) {
            "OBJECT_TRACKING_ROI_SELECTION_NON_FINITE"
        }
        require(rightPx > leftPx && bottomPx > topPx) {
            "OBJECT_TRACKING_ROI_SELECTION_INVALID"
        }
    }
}

data class NormalizedMediaRoi(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(listOf(x, y, width, height).all { it.isFinite() }) {
            "OBJECT_TRACKING_ROI_NON_FINITE"
        }
        require(x >= 0.0 && y >= 0.0 && width >= 0.01 && height >= 0.01) {
            "OBJECT_TRACKING_ROI_TOO_SMALL"
        }
        require(x + width <= 1.0 + ObjectTrackingRoiMappingPolicy.EPSILON) {
            "OBJECT_TRACKING_ROI_OUT_OF_RANGE"
        }
        require(y + height <= 1.0 + ObjectTrackingRoiMappingPolicy.EPSILON) {
            "OBJECT_TRACKING_ROI_OUT_OF_RANGE"
        }
    }
}

/**
 * Mirrors the geometry order used by LocalCut's Media3 render pipeline and maps an axis-aligned
 * selection in PlayerView coordinates back to an axis-aligned ROI in the original source media.
 *
 * Forward render order mirrored here:
 * source -> initial SCALE_TO_FIT -> effective clip transform -> explicit Crop -> crop Presentation
 * -> PlayerView SCALE_TO_FIT.
 *
 * Mapping runs the exact inverse order. A rotated selection becomes the smallest axis-aligned
 * source rectangle containing the inverse-mapped four corners because the Termux tracker accepts
 * an axis-aligned normalized ROI.
 */
object ObjectTrackingRoiMappingPolicy {
    internal const val EPSILON = 1e-7
    private const val MIN_NORMALIZED_ROI = 0.01

    fun map(
        selection: PreviewSelectionRect,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        outputWidthPx: Int,
        outputHeightPx: Int,
        transformSpaceWidthPx: Double,
        transformSpaceHeightPx: Double,
        effectiveTransform: TransformValue,
        crop: CropSettings,
    ): NormalizedMediaRoi {
        require(viewportWidthPx.isFinite() && viewportWidthPx > 0.0) {
            "OBJECT_TRACKING_VIEWPORT_INVALID"
        }
        require(viewportHeightPx.isFinite() && viewportHeightPx > 0.0) {
            "OBJECT_TRACKING_VIEWPORT_INVALID"
        }
        require(sourceWidthPx > 0 && sourceHeightPx > 0) { "OBJECT_TRACKING_SOURCE_SIZE_INVALID" }
        require(outputWidthPx > 0 && outputHeightPx > 0) { "OBJECT_TRACKING_OUTPUT_SIZE_INVALID" }
        require(transformSpaceWidthPx.isFinite() && transformSpaceWidthPx > 0.0) {
            "OBJECT_TRACKING_TRANSFORM_SPACE_INVALID"
        }
        require(transformSpaceHeightPx.isFinite() && transformSpaceHeightPx > 0.0) {
            "OBJECT_TRACKING_TRANSFORM_SPACE_INVALID"
        }
        require(crop.validationIssues().isEmpty() && crop.right > crop.left && crop.bottom > crop.top) {
            "OBJECT_TRACKING_CROP_INVALID"
        }
        require(
            effectiveTransform.x.isFinite() &&
                effectiveTransform.y.isFinite() &&
                effectiveTransform.scale.isFinite() &&
                effectiveTransform.scale > 0.0 &&
                effectiveTransform.rotationDeg.isFinite()
        ) { "OBJECT_TRACKING_TRANSFORM_INVALID" }
        require(
            selection.leftPx >= -EPSILON &&
                selection.topPx >= -EPSILON &&
                selection.rightPx <= viewportWidthPx + EPSILON &&
                selection.bottomPx <= viewportHeightPx + EPSILON
        ) { "OBJECT_TRACKING_ROI_SELECTION_OUT_OF_VIEWPORT" }

        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        val croppedWidthPx = max(1, (outputWidthPx * cropWidth).roundToInt())
        val croppedHeightPx = max(1, (outputHeightPx * cropHeight).roundToInt())
        val finalFrameWidthPx = if (crop.mode == CropMode.NONE) croppedWidthPx else outputWidthPx
        val finalFrameHeightPx = if (crop.mode == CropMode.NONE) croppedHeightPx else outputHeightPx

        val playerScale = min(
            viewportWidthPx / finalFrameWidthPx.toDouble(),
            viewportHeightPx / finalFrameHeightPx.toDouble(),
        )
        val contentWidth = finalFrameWidthPx * playerScale
        val contentHeight = finalFrameHeightPx * playerScale
        val contentLeft = (viewportWidthPx - contentWidth) / 2.0
        val contentTop = (viewportHeightPx - contentHeight) / 2.0
        require(
            selection.leftPx >= contentLeft - EPSILON &&
                selection.topPx >= contentTop - EPSILON &&
                selection.rightPx <= contentLeft + contentWidth + EPSILON &&
                selection.bottomPx <= contentTop + contentHeight + EPSILON
        ) { "OBJECT_TRACKING_ROI_OUTSIDE_PREVIEW_CONTENT" }

        val sourceAspect = sourceWidthPx.toDouble() / sourceHeightPx.toDouble()
        val outputAspect = outputWidthPx.toDouble() / outputHeightPx.toDouble()
        val initialFit = presentationScale(
            inputAspect = sourceAspect,
            requestedAspect = outputAspect,
            mode = CropMode.FIT,
        )
        val cropPresentation = if (crop.mode == CropMode.NONE || crop.mode == CropMode.STRETCH) {
            Vec2(1.0, 1.0)
        } else {
            presentationScale(
                inputAspect = croppedWidthPx.toDouble() / croppedHeightPx.toDouble(),
                requestedAspect = outputAspect,
                mode = crop.mode,
            )
        }

        val cropCenter = Vec2(
            x = crop.left + crop.right - 1.0,
            y = 1.0 - crop.top - crop.bottom,
        )
        val cropScale = Vec2(cropWidth, cropHeight)
        val transformTranslation = Vec2(
            x = effectiveTransform.x / (transformSpaceWidthPx / 2.0),
            y = -effectiveTransform.y / (transformSpaceHeightPx / 2.0),
        )
        val angleRad = effectiveTransform.rotationDeg * PI / 180.0
        val cosAngle = cos(angleRad)
        val sinAngle = sin(angleRad)

        val corners = listOf(
            Vec2(selection.leftPx, selection.topPx),
            Vec2(selection.rightPx, selection.topPx),
            Vec2(selection.rightPx, selection.bottomPx),
            Vec2(selection.leftPx, selection.bottomPx),
        )
        val sourcePoints = corners.map { viewportPoint ->
            val finalNdc = Vec2(
                x = ((viewportPoint.x - contentLeft) / contentWidth) * 2.0 - 1.0,
                y = 1.0 - ((viewportPoint.y - contentTop) / contentHeight) * 2.0,
            )

            val beforeCropPresentation = Vec2(
                x = finalNdc.x / cropPresentation.x,
                y = finalNdc.y / cropPresentation.y,
            )
            require(inNdc(beforeCropPresentation)) { "OBJECT_TRACKING_ROI_OUTSIDE_RENDERED_MEDIA" }

            val beforeCrop = Vec2(
                x = beforeCropPresentation.x * cropScale.x + cropCenter.x,
                y = beforeCropPresentation.y * cropScale.y + cropCenter.y,
            )

            // TransformEffectFactory uses Android Matrix postTranslate -> postRotate -> postScale.
            // Android post-concat is M' = other * M, so inverse order is scale -> rotation -> translation.
            val afterInverseScale = Vec2(
                x = beforeCrop.x / effectiveTransform.scale,
                y = beforeCrop.y / effectiveTransform.scale,
            )
            val afterInverseRotation = Vec2(
                x = cosAngle * afterInverseScale.x + sinAngle * afterInverseScale.y,
                y = -sinAngle * afterInverseScale.x + cosAngle * afterInverseScale.y,
            )
            val beforeClipTransform = Vec2(
                x = afterInverseRotation.x - transformTranslation.x,
                y = afterInverseRotation.y - transformTranslation.y,
            )
            val sourceNdc = Vec2(
                x = beforeClipTransform.x / initialFit.x,
                y = beforeClipTransform.y / initialFit.y,
            )
            require(inNdc(sourceNdc)) { "OBJECT_TRACKING_ROI_OUTSIDE_RENDERED_MEDIA" }
            Vec2(
                x = ((sourceNdc.x + 1.0) / 2.0).coerceNearUnit(),
                y = ((1.0 - sourceNdc.y) / 2.0).coerceNearUnit(),
            )
        }

        val left = sourcePoints.minOf { it.x }.coerceNearUnit()
        val top = sourcePoints.minOf { it.y }.coerceNearUnit()
        val right = sourcePoints.maxOf { it.x }.coerceNearUnit()
        val bottom = sourcePoints.maxOf { it.y }.coerceNearUnit()
        val width = right - left
        val height = bottom - top
        require(width + EPSILON >= MIN_NORMALIZED_ROI && height + EPSILON >= MIN_NORMALIZED_ROI) {
            "OBJECT_TRACKING_ROI_TOO_SMALL"
        }
        return NormalizedMediaRoi(
            x = left,
            y = top,
            width = width.coerceAtMost(1.0 - left),
            height = height.coerceAtMost(1.0 - top),
        )
    }

    private fun presentationScale(
        inputAspect: Double,
        requestedAspect: Double,
        mode: CropMode,
    ): Vec2 {
        require(inputAspect.isFinite() && inputAspect > 0.0) { "OBJECT_TRACKING_INPUT_ASPECT_INVALID" }
        require(requestedAspect.isFinite() && requestedAspect > 0.0) { "OBJECT_TRACKING_OUTPUT_ASPECT_INVALID" }
        return when (mode) {
            CropMode.NONE, CropMode.STRETCH -> Vec2(1.0, 1.0)
            CropMode.FIT -> if (requestedAspect > inputAspect) {
                Vec2(inputAspect / requestedAspect, 1.0)
            } else {
                Vec2(1.0, requestedAspect / inputAspect)
            }
            CropMode.FILL -> if (requestedAspect > inputAspect) {
                Vec2(1.0, requestedAspect / inputAspect)
            } else {
                Vec2(inputAspect / requestedAspect, 1.0)
            }
        }
    }

    private fun inNdc(point: Vec2): Boolean =
        point.x >= -1.0 - EPSILON && point.x <= 1.0 + EPSILON &&
            point.y >= -1.0 - EPSILON && point.y <= 1.0 + EPSILON

    private fun Double.coerceNearUnit(): Double = when {
        this < 0.0 && this >= -EPSILON -> 0.0
        this > 1.0 && this <= 1.0 + EPSILON -> 1.0
        else -> this.coerceIn(0.0, 1.0)
    }

    private data class Vec2(val x: Double, val y: Double)
}
