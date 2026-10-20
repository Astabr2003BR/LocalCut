package com.localcut.editor.domain.model

enum class TextHorizontalAlignment { START, CENTER, END }

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
)

data class TextOverlay(
    val id: String,
    val startUs: Long,
    val durationUs: Long,
    val text: String,
    val style: TextStyle = TextStyle(),
    val transform: TransformValue = TransformValue(),
    val opacity: Double = 1.0,
    val zIndex: Int = 0,
)
