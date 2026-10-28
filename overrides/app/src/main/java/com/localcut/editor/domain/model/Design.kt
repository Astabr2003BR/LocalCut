package com.localcut.editor.domain.model

enum class GuideAxis {
    HORIZONTAL,
    VERTICAL,
}

data class CanvasGuide(
    val id: String,
    val axis: GuideAxis,
    val positionNormalized: Double,
) {
    fun validationIssues(): List<String> = buildList {
        if (id.isBlank()) add("guide id must not be blank")
        if (!positionNormalized.isFinite() || positionNormalized !in 0.0..1.0) {
            add("guide position must be finite and in 0..1")
        }
    }
}

data class DesignCanvasSettings(
    val guides: List<CanvasGuide> = emptyList(),
    val snapToleranceNormalized: Double = 0.015,
) {
    fun validationIssues(): List<String> = buildList {
        if (!snapToleranceNormalized.isFinite() || snapToleranceNormalized < 0.0) {
            add("snap tolerance must be finite and non-negative")
        }
        guides.forEach { addAll(it.validationIssues()) }
    }
}

enum class ShapeKind {
    RECTANGLE,
    ELLIPSE,
    LINE,
    PATH,
}

data class PathPoint(
    val x: Double,
    val y: Double,
)

data class VectorPath(
    val points: List<PathPoint> = emptyList(),
    val closed: Boolean = false,
) {
    fun validationIssues(): List<String> = buildList {
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            add("vector path points must be finite")
        }
    }
}

data class ShapeSpec(
    val kind: ShapeKind,
    val path: VectorPath? = null,
    val fillArgb: Int? = 0xFFFFFFFF.toInt(),
    val strokeArgb: Int? = null,
    val strokeWidthPx: Double = 0.0,
    val cornerRadiusPx: Double = 0.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (kind == ShapeKind.PATH && path == null) add("path shape requires a vector path")
        if (kind != ShapeKind.PATH && path != null) add("non-path shape must not contain a vector path")
        path?.let { addAll(it.validationIssues()) }
        if (!strokeWidthPx.isFinite() || strokeWidthPx < 0.0) add("shape stroke width must be finite and non-negative")
        if (!cornerRadiusPx.isFinite() || cornerRadiusPx < 0.0) add("shape corner radius must be finite and non-negative")
    }
}

data class DesignElement(
    val id: String,
    val startUs: Long,
    val durationUs: Long,
    val shape: ShapeSpec? = null,
    val clipId: String? = null,
    val textOverlayId: String? = null,
    val transform: TransformValue = TransformValue(),
    val opacity: Double = 1.0,
    val zIndex: Int = 0,
) {
    fun validationIssues(): List<String> = buildList {
        if (id.isBlank()) add("design element id must not be blank")
        if (startUs < 0L || durationUs <= 0L) add("design element timing must be non-negative with positive duration")
        if (!opacity.isFinite() || opacity !in 0.0..1.0) add("design element opacity must be finite and in 0..1")
        val sources = listOf(shape != null, clipId != null, textOverlayId != null).count { it }
        if (sources > 1) add("design element must reference at most one content source")
        shape?.let { addAll(it.validationIssues()) }
        if (clipId != null && clipId.isBlank()) add("design element clipId must not be blank")
        if (textOverlayId != null && textOverlayId.isBlank()) add("design element textOverlayId must not be blank")
    }
}

data class DesignGroup(
    val id: String,
    val elementIds: List<String>,
) {
    fun validationIssues(existingElementIds: Set<String>): List<String> = buildList {
        if (id.isBlank()) add("design group id must not be blank")
        if (elementIds.size < 2) add("design group requires at least two elements")
        if (elementIds.distinct().size != elementIds.size) add("design group element ids must be unique")
        val missing = elementIds.filterNot { it in existingElementIds }
        if (missing.isNotEmpty()) add("design group references missing elements: ${missing.joinToString()}")
    }
}

data class ReusableComponent(
    val id: String,
    val name: String,
    val elementIds: List<String>,
    val exposedProperties: Set<String> = emptySet(),
) {
    fun validationIssues(existingElementIds: Set<String>): List<String> = buildList {
        if (id.isBlank()) add("reusable component id must not be blank")
        if (name.isBlank()) add("reusable component name must not be blank")
        if (elementIds.isEmpty()) add("reusable component requires at least one element")
        if (elementIds.distinct().size != elementIds.size) add("reusable component element ids must be unique")
        val missing = elementIds.filterNot { it in existingElementIds }
        if (missing.isNotEmpty()) add("reusable component references missing elements: ${missing.joinToString()}")
        if (exposedProperties.any { it.isBlank() }) add("reusable component exposed properties must not be blank")
    }
}
