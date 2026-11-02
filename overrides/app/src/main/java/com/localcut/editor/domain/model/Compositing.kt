package com.localcut.editor.domain.model

enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    ADD,
    SUBTRACT,
}

enum class MaskKind {
    RECTANGLE,
    ELLIPSE,
    ROUNDED_RECTANGLE,
    LINEAR_GRADIENT,
    FREEFORM,
}

enum class CropMode { NONE, FIT, FILL, STRETCH }

data class MaskPoint(val x: Double, val y: Double)

data class MaskSpec(
    val id: String,
    val kind: MaskKind,
    val points: List<MaskPoint> = emptyList(),
    val invert: Boolean = false,
    val featherPx: Double = 0.0,
    val expansionPx: Double = 0.0,
    val opacity: Double = 1.0,
    val animations: List<PropertyAnimation> = emptyList(),
) {
    fun validationIssues(): List<String> = buildList {
        if (id.isBlank()) add("mask id must not be blank")
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) add("mask points must be finite")
        if (!featherPx.isFinite() || featherPx < 0.0) add("mask feather must be finite and non-negative")
        if (!expansionPx.isFinite()) add("mask expansion must be finite")
        if (!opacity.isFinite() || opacity !in 0.0..1.0) add("mask opacity must be finite and in 0..1")
        if (kind == MaskKind.FREEFORM && points.isNotEmpty() && points.size < 2) add("freeform mask requires at least two points")
        animations.forEach { addAll(it.validationIssues()) }
    }
}

data class ChromaKeySettings(
    val enabled: Boolean = false,
    val keyArgb: Int = 0xFF00FF00.toInt(),
    val threshold: Double = 0.2,
    val softness: Double = 0.1,
    val spillSuppression: Double = 0.0,
    val edgeFeatherPx: Double = 0.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (!threshold.isFinite() || threshold !in 0.0..1.0) add("chroma threshold must be finite and in 0..1")
        if (!softness.isFinite() || softness !in 0.0..1.0) add("chroma softness must be finite and in 0..1")
        if (!spillSuppression.isFinite() || spillSuppression !in 0.0..1.0) add("chroma spill suppression must be finite and in 0..1")
        if (!edgeFeatherPx.isFinite() || edgeFeatherPx < 0.0) add("chroma edge feather must be finite and non-negative")
    }
}

data class CropSettings(
    val mode: CropMode = CropMode.NONE,
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 1.0,
    val bottom: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        val values = listOf(left, top, right, bottom)
        if (values.any { !it.isFinite() }) add("crop values must be finite")
        if (values.any { it !in 0.0..1.0 }) add("crop values must be in 0..1")
        if (right < left || bottom < top) add("crop bounds must not be inverted")
    }
}

data class CompositingSettings(
    val blendMode: BlendMode = BlendMode.NORMAL,
    val masks: List<MaskSpec> = emptyList(),
    val chromaKey: ChromaKeySettings = ChromaKeySettings(),
    val crop: CropSettings = CropSettings(),
) {
    fun validationIssues(): List<String> = buildList {
        if (masks.map { it.id }.distinct().size != masks.size) add("mask ids must be unique")
        masks.forEach { addAll(it.validationIssues()) }
        addAll(chromaKey.validationIssues())
        addAll(crop.validationIssues())
    }
}
