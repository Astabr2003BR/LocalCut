package com.localcut.editor.render

import com.localcut.editor.domain.animation.PropertyAnimationEvaluator
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.PropertyValue

object CompositingEvaluator {
    fun valueAt(settings: CompositingSettings, offsetUs: Long): CompositingSettings {
        require(settings.validationIssues().isEmpty()) {
            "invalid compositing settings: ${settings.validationIssues().first()}"
        }
        val evaluatedMasks = settings.masks.map { evaluateMask(it, offsetUs) }
        return settings.copy(masks = evaluatedMasks)
    }

    private fun evaluateMask(mask: MaskSpec, offsetUs: Long): MaskSpec {
        var opacity = mask.opacity
        var featherPx = mask.featherPx
        var expansionPx = mask.expansionPx
        val points = mask.points.toMutableList()

        for (animation in mask.animations) {
            val value = PropertyAnimationEvaluator.valueAt(animation, offsetUs)
            when {
                animation.propertyId == "opacity" -> opacity = scalar(value, "opacity")
                animation.propertyId == "featherPx" -> featherPx = scalar(value, "featherPx")
                animation.propertyId == "expansionPx" -> expansionPx = scalar(value, "expansionPx")
                animation.propertyId.startsWith("point.") -> {
                    val index = animation.propertyId.removePrefix("point.").toIntOrNull()
                        ?: throw IllegalArgumentException("mask point animation index is invalid")
                    require(index in points.indices) { "mask point animation index is out of bounds" }
                    val point = value as? PropertyValue.Point
                        ?: throw IllegalArgumentException("mask point animation requires point values")
                    points[index] = MaskPoint(point.x, point.y)
                }
                else -> throw IllegalArgumentException("unsupported mask animation property: ${animation.propertyId}")
            }
        }

        val evaluated = mask.copy(
            points = points,
            featherPx = featherPx,
            expansionPx = expansionPx,
            opacity = opacity,
            animations = emptyList(),
        )
        require(evaluated.validationIssues().isEmpty()) {
            "evaluated mask is invalid: ${evaluated.validationIssues().first()}"
        }
        return evaluated
    }

    private fun scalar(value: PropertyValue, propertyId: String): Double =
        (value as? PropertyValue.Scalar)?.value
            ?: throw IllegalArgumentException("mask $propertyId animation requires scalar values")
}
