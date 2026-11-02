package com.localcut.editor.domain.model

enum class TextHorizontalAlignment { START, CENTER, END }

data class FontReference(
    val familyName: String? = null,
    val uri: String? = null,
    val fingerprint: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        if (familyName.isNullOrBlank() && uri.isNullOrBlank()) add("font reference requires familyName or uri")
        if (uri != null && uri.isBlank()) add("font uri must not be blank")
        if (fingerprint != null && fingerprint.isBlank()) add("font fingerprint must not be blank")
    }
}

data class TextBackgroundStyle(
    val argb: Int,
    val paddingPx: Double = 8.0,
    val cornerRadiusPx: Double = 0.0,
    val opacity: Double = 1.0,
)

data class TextStrokeStyle(
    val argb: Int,
    val widthPx: Double,
)

data class TextShadowStyle(
    val argb: Int,
    val radiusPx: Double,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val opacity: Double = 1.0,
)

enum class TextAnimationKind {
    NONE,
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    POP,
    SCALE,
    TYPEWRITER,
    BOUNCE,
    WIGGLE,
}

data class TextAnimationDescriptor(
    val kind: TextAnimationKind = TextAnimationKind.NONE,
    val durationUs: Long = 0L,
    val easing: Easing = Easing(),
    val amount: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (durationUs < 0L) add("text animation duration must be non-negative")
        if (!amount.isFinite()) add("text animation amount must be finite")
        addAll(easing.validationIssues())
    }
}

data class CaptionMetadata(
    val languageTag: String? = null,
    val speaker: String? = null,
    val confidence: Double? = null,
    val sourceText: String? = null,
) {
    fun validationIssues(): List<String> = buildList {
        if (confidence != null && (!confidence.isFinite() || confidence !in 0.0..1.0)) {
            add("caption confidence must be finite and in 0..1")
        }
    }
}

data class TextStyle(
    val fontSizePx: Double = 64.0,
    val alignment: TextHorizontalAlignment = TextHorizontalAlignment.CENTER,
    val fillArgb: Int = 0xFFFFFFFF.toInt(),
    val strokeArgb: Int? = null,
    val strokeWidthPx: Double = 0.0,
    val shadowArgb: Int? = null,
    val shadowRadiusPx: Double = 0.0,
    val shadowOffsetX: Double = 0.0,
    val shadowOffsetY: Double = 0.0,
    val font: FontReference? = null,
    val letterSpacingEm: Double = 0.0,
    val lineSpacingMultiplier: Double = 1.0,
    val background: TextBackgroundStyle? = null,
    val strokes: List<TextStrokeStyle> = emptyList(),
    val shadows: List<TextShadowStyle> = emptyList(),
) {
    fun validationIssues(): List<String> = buildList {
        if (!fontSizePx.isFinite() || fontSizePx <= 0.0) add("fontSizePx must be finite and positive")
        if (!strokeWidthPx.isFinite() || strokeWidthPx < 0.0) add("strokeWidthPx must be finite and non-negative")
        if (!shadowRadiusPx.isFinite() || shadowRadiusPx < 0.0) add("shadowRadiusPx must be finite and non-negative")
        if (!shadowOffsetX.isFinite() || !shadowOffsetY.isFinite()) add("shadow offsets must be finite")
        font?.let { addAll(it.validationIssues()) }
        if (!letterSpacingEm.isFinite()) add("letterSpacingEm must be finite")
        if (!lineSpacingMultiplier.isFinite() || lineSpacingMultiplier <= 0.0) add("lineSpacingMultiplier must be finite and positive")
        background?.let { bg ->
            if (!bg.paddingPx.isFinite() || bg.paddingPx < 0.0) add("text background padding must be finite and non-negative")
            if (!bg.cornerRadiusPx.isFinite() || bg.cornerRadiusPx < 0.0) add("text background corner radius must be finite and non-negative")
            if (!bg.opacity.isFinite() || bg.opacity !in 0.0..1.0) add("text background opacity must be finite and in 0..1")
        }
        strokes.forEach { stroke ->
            if (!stroke.widthPx.isFinite() || stroke.widthPx < 0.0) add("text stroke width must be finite and non-negative")
        }
        shadows.forEach { shadow ->
            if (!shadow.radiusPx.isFinite() || shadow.radiusPx < 0.0) add("text shadow radius must be finite and non-negative")
            if (!shadow.offsetX.isFinite() || !shadow.offsetY.isFinite()) add("text shadow offsets must be finite")
            if (!shadow.opacity.isFinite() || shadow.opacity !in 0.0..1.0) add("text shadow opacity must be finite and in 0..1")
        }
    }
}

data class TextOverlay(
    val id: String,
    val startUs: Long,
    val durationUs: Long,
    val text: String,
    val style: TextStyle = TextStyle(),
    val transform: TransformValue = TransformValue(),
    val opacity: Double = 1.0,
    val zIndex: Int = 0,
    val entrance: TextAnimationDescriptor = TextAnimationDescriptor(),
    val exit: TextAnimationDescriptor = TextAnimationDescriptor(),
    val loop: TextAnimationDescriptor = TextAnimationDescriptor(),
    val perCharacterDelayUs: Long = 0L,
    val caption: CaptionMetadata? = null,
)
