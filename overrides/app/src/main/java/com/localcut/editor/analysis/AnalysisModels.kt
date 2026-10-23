package com.localcut.editor.analysis

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue

enum class TrackingTargetKind {
    OBJECT,
    FACE,
    BODY,
    HAND,
    POINT,
}

data class TrackingTarget(
    val kind: TrackingTargetKind,
    val normalizedX: Double,
    val normalizedY: Double,
    val label: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        if (!normalizedX.isFinite() || normalizedX !in 0.0..1.0) add("tracking target x must be finite and in 0..1")
        if (!normalizedY.isFinite() || normalizedY !in 0.0..1.0) add("tracking target y must be finite and in 0..1")
        if (label != null && label.isBlank()) add("tracking target label must not be blank")
    }
}

data class TrackingSample(
    val timeUs: Long,
    val normalizedX: Double,
    val normalizedY: Double,
    val scale: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val confidence: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (timeUs < 0L) add("tracking sample time must be non-negative")
        if (!normalizedX.isFinite() || normalizedX !in 0.0..1.0) add("tracking sample x must be finite and in 0..1")
        if (!normalizedY.isFinite() || normalizedY !in 0.0..1.0) add("tracking sample y must be finite and in 0..1")
        if (!scale.isFinite() || scale <= 0.0) add("tracking sample scale must be finite and positive")
        if (!rotationDeg.isFinite()) add("tracking sample rotation must be finite")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) add("tracking sample confidence must be finite and in 0..1")
    }
}

data class TrackingData(
    val target: TrackingTarget,
    val samples: List<TrackingSample>,
    val sourceAnalyzerId: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        addAll(target.validationIssues())
        if (samples.isEmpty()) add("tracking data requires samples")
        samples.forEach { addAll(it.validationIssues()) }
        if (samples.zipWithNext().any { (a, b) -> a.timeUs >= b.timeUs }) add("tracking samples must be strictly ordered")
        if (sourceAnalyzerId != null && sourceAnalyzerId.isBlank()) add("tracking analyzer id must not be blank")
    }
}

data class StabilizationSample(
    val timeUs: Long,
    val motionX: Double,
    val motionY: Double,
    val rotationDeg: Double,
    val zoom: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (timeUs < 0L) add("stabilization sample time must be non-negative")
        if (!motionX.isFinite() || !motionY.isFinite() || !rotationDeg.isFinite()) add("stabilization motion must be finite")
        if (!zoom.isFinite() || zoom <= 0.0) add("stabilization zoom must be finite and positive")
    }
}

data class StabilizationData(
    val samples: List<StabilizationSample>,
    val cropScale: Double = 1.0,
    val sourceAnalyzerId: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        if (samples.isEmpty()) add("stabilization data requires samples")
        samples.forEach { addAll(it.validationIssues()) }
        if (samples.zipWithNext().any { (a, b) -> a.timeUs >= b.timeUs }) add("stabilization samples must be strictly ordered")
        if (!cropScale.isFinite() || cropScale < 1.0) add("stabilization crop scale must be finite and at least 1")
        if (sourceAnalyzerId != null && sourceAnalyzerId.isBlank()) add("stabilization analyzer id must not be blank")
    }
}

data class ReframeSample(
    val timeUs: Long,
    val centerX: Double,
    val centerY: Double,
    val scale: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (timeUs < 0L) add("reframe sample time must be non-negative")
        if (!centerX.isFinite() || centerX !in 0.0..1.0) add("reframe center x must be finite and in 0..1")
        if (!centerY.isFinite() || centerY !in 0.0..1.0) add("reframe center y must be finite and in 0..1")
        if (!scale.isFinite() || scale <= 0.0) add("reframe scale must be finite and positive")
    }
}

data class AutoReframeData(
    val aspectWidth: Int,
    val aspectHeight: Int,
    val samples: List<ReframeSample>,
    val sourceAnalyzerId: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        if (aspectWidth <= 0 || aspectHeight <= 0) add("reframe aspect must be positive")
        if (samples.isEmpty()) add("reframe data requires samples")
        samples.forEach { addAll(it.validationIssues()) }
        if (samples.zipWithNext().any { (a, b) -> a.timeUs >= b.timeUs }) add("reframe samples must be strictly ordered")
        if (sourceAnalyzerId != null && sourceAnalyzerId.isBlank()) add("reframe analyzer id must not be blank")
    }
}

object TrackingAnimationAdapter {
    fun toPropertyAnimations(
        data: TrackingData,
        projectWidth: Int,
        projectHeight: Int,
    ): List<PropertyAnimation> {
        require(data.validationIssues().isEmpty()) { "invalid tracking data: ${data.validationIssues().first()}" }
        require(projectWidth > 0 && projectHeight > 0) { "project dimensions must be positive" }
        return listOf(
            scalarAnimation("transform.x", data.samples) { (it.normalizedX - 0.5) * projectWidth },
            scalarAnimation("transform.y", data.samples) { (it.normalizedY - 0.5) * projectHeight },
            scalarAnimation("transform.scale", data.samples) { it.scale },
            scalarAnimation("transform.rotationDeg", data.samples) { it.rotationDeg },
        )
    }

    private fun scalarAnimation(
        propertyId: String,
        samples: List<TrackingSample>,
        value: (TrackingSample) -> Double,
    ): PropertyAnimation = PropertyAnimation(
        propertyId = propertyId,
        keyframes = samples.mapIndexed { index, sample ->
            PropertyKeyframe(
                id = "$propertyId-$index",
                offsetUs = sample.timeUs,
                value = PropertyValue.Scalar(value(sample)),
                easingToNext = Easing(),
            )
        },
    )
}

object StabilizationAdapter {
    fun correctionAt(data: StabilizationData, timeUs: Long): TransformValue {
        require(data.validationIssues().isEmpty()) { "invalid stabilization data: ${data.validationIssues().first()}" }
        require(timeUs >= 0L) { "timeUs must be non-negative" }
        val sample = data.samples.lastOrNull { it.timeUs <= timeUs } ?: data.samples.first()
        return TransformValue(
            x = -sample.motionX,
            y = -sample.motionY,
            scale = data.cropScale / sample.zoom,
            rotationDeg = -sample.rotationDeg,
        )
    }
}
