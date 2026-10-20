package com.localcut.editor.domain.model

data class Clip(
    val id: String,
    val assetId: String,
    val startUs: Long,
    val durationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val transform: TransformValue = TransformValue(),
    val opacity: Double = 1.0,
    val volume: Double = 1.0,
    val keyframes: List<TransformKeyframe> = emptyList(),
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
)
