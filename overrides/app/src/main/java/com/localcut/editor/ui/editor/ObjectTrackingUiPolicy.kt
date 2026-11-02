package com.localcut.editor.ui.editor

import com.localcut.editor.analysis.StabilizationApplyPolicy
import com.localcut.editor.domain.model.PropertyAnimation

object ObjectTrackingUiPolicy {
    private val motionProperties = setOf("transform.x", "transform.y")

    fun requireStartCompatible(existing: List<PropertyAnimation>) {
        require(!StabilizationApplyPolicy.hasAnyGeneratedStabilization(existing)) {
            "OBJECT_TRACKING_CONFLICTS_WITH_STABILIZATION"
        }
        require(!MotionPathUiPolicy.hasGeneratedMotionPath(existing)) {
            "OBJECT_TRACKING_CONFLICTS_WITH_MOTION_PATH"
        }
        val replacingGeneratedTracking = hasGeneratedObjectTracking(existing)
        require(replacingGeneratedTracking || existing.none { it.propertyId in motionProperties }) {
            "OBJECT_TRACKING_CONFLICTS_WITH_POSITION_ANIMATION"
        }
    }

    fun merge(
        existing: List<PropertyAnimation>,
        tracking: List<PropertyAnimation>,
    ): List<PropertyAnimation> {
        requireStartCompatible(existing)
        require(tracking.map { it.propertyId }.toSet() == motionProperties && tracking.size == motionProperties.size) {
            "object tracking must contain exactly transform.x and transform.y"
        }
        require(tracking.all { it.validationIssues().isEmpty() }) { "object tracking animations must be valid" }
        require(hasGeneratedObjectTracking(tracking)) { "object tracking animations must use generated tracking ids" }

        val preserved = existing.filterNot { it.propertyId in motionProperties }
        val merged = preserved + tracking
        require(merged.map { it.propertyId }.distinct().size == merged.size) {
            "object tracking merge produced duplicate properties"
        }
        return merged
    }

    fun hasGeneratedObjectTracking(animations: List<PropertyAnimation>): Boolean {
        val byId = animations.associateBy { it.propertyId }
        val x = byId["transform.x"] ?: return false
        val y = byId["transform.y"] ?: return false
        return x.keyframes.isNotEmpty() && y.keyframes.isNotEmpty() &&
            x.keyframes.all { it.id.startsWith("object-track-x-") } &&
            y.keyframes.all { it.id.startsWith("object-track-y-") }
    }

    fun removeGeneratedObjectTracking(animations: List<PropertyAnimation>): List<PropertyAnimation> =
        if (hasGeneratedObjectTracking(animations)) {
            animations.filterNot { it.propertyId in motionProperties }
        } else {
            animations
        }
}
