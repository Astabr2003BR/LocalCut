package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropSettings

object CropToolsUiPolicy {
    const val MIN_SPAN = 0.001

    fun setLeft(source: CompositingSettings, left: Double): CompositingSettings {
        require(left.isFinite()) { "crop left must be finite" }
        val maxLeft = (source.crop.right - MIN_SPAN).coerceAtLeast(0.0)
        return withCrop(source, source.crop.copy(left = left.coerceIn(0.0, maxLeft)))
    }

    fun setTop(source: CompositingSettings, top: Double): CompositingSettings {
        require(top.isFinite()) { "crop top must be finite" }
        val maxTop = (source.crop.bottom - MIN_SPAN).coerceAtLeast(0.0)
        return withCrop(source, source.crop.copy(top = top.coerceIn(0.0, maxTop)))
    }

    fun setRight(source: CompositingSettings, right: Double): CompositingSettings {
        require(right.isFinite()) { "crop right must be finite" }
        val minRight = (source.crop.left + MIN_SPAN).coerceAtMost(1.0)
        return withCrop(source, source.crop.copy(right = right.coerceIn(minRight, 1.0)))
    }

    fun setBottom(source: CompositingSettings, bottom: Double): CompositingSettings {
        require(bottom.isFinite()) { "crop bottom must be finite" }
        val minBottom = (source.crop.top + MIN_SPAN).coerceAtMost(1.0)
        return withCrop(source, source.crop.copy(bottom = bottom.coerceIn(minBottom, 1.0)))
    }

    fun resetBounds(source: CompositingSettings): CompositingSettings =
        withCrop(
            source,
            source.crop.copy(
                left = 0.0,
                top = 0.0,
                right = 1.0,
                bottom = 1.0,
            ),
        )

    private fun withCrop(source: CompositingSettings, crop: CropSettings): CompositingSettings {
        val updated = source.copy(crop = crop)
        val issues = updated.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        require(crop.right > crop.left && crop.bottom > crop.top) {
            "crop bounds must keep a positive area"
        }
        return updated
    }
}
