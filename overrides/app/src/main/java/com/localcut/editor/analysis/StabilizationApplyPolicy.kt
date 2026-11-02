package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyAnimation

object StabilizationApplyPolicy {
    private val stabilizedProperties = linkedSetOf(
        "transform.x",
        "transform.y",
        "transform.rotationDeg",
        "transform.scale",
    )
    private val generatedKeyPrefixes = mapOf(
        "transform.x" to "stabilize-x-",
        "transform.y" to "stabilize-y-",
        "transform.rotationDeg" to "stabilize-rotation-",
        "transform.scale" to "stabilize-scale-",
    )

    fun merge(
        existing: List<PropertyAnimation>,
        stabilization: List<PropertyAnimation>,
    ): List<PropertyAnimation> {
        val ids = stabilization.map { it.propertyId }
        require(ids.distinct().size == ids.size) { "stabilization properties must be unique" }
        require(ids.all { it in stabilizedProperties }) { "unexpected stabilization property" }
        stabilization.forEach { animation ->
            require(animation.validationIssues().isEmpty()) { "invalid stabilization animation" }
        }

        val preserved = existing.filterNot { it.propertyId in stabilizedProperties }
        val merged = preserved + stabilization
        require(merged.map { it.propertyId }.distinct().size == merged.size) {
            "merged property animations must be unique"
        }
        return merged
    }

    fun hasGeneratedStabilization(animations: List<PropertyAnimation>): Boolean {
        val byProperty = animations.groupBy { it.propertyId }
        return stabilizedProperties.all { propertyId ->
            val matches = byProperty[propertyId].orEmpty()
            matches.size == 1 && isGeneratedChannel(matches.single())
        }
    }

    fun hasAnyGeneratedStabilization(animations: List<PropertyAnimation>): Boolean =
        animations.any(::isGeneratedChannel)

    fun removeGeneratedStabilization(animations: List<PropertyAnimation>): List<PropertyAnimation> {
        if (!hasGeneratedStabilization(animations)) return animations
        return animations.filterNot(::isGeneratedChannel)
    }

    private fun isGeneratedChannel(animation: PropertyAnimation): Boolean {
        val prefix = generatedKeyPrefixes[animation.propertyId] ?: return false
        return animation.keyframes.isNotEmpty() && animation.keyframes.all { keyframe ->
            keyframe.id.startsWith(prefix)
        }
    }
}
