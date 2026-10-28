package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyAnimation

object StabilizationApplyPolicy {
    private val stabilizedProperties = linkedSetOf(
        "transform.x",
        "transform.y",
        "transform.rotationDeg",
        "transform.scale",
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
}
