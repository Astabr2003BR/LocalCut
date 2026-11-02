package com.localcut.editor.domain.model

import com.localcut.editor.analysis.ClipAnalysisState

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
    val propertyAnimations: List<PropertyAnimation> = emptyList(),
    val compositing: CompositingSettings = CompositingSettings(),
    val effects: List<EffectInstance> = emptyList(),
    val timeMap: TimeMap = TimeMap(),
    val audioProcessing: AudioProcessingSettings = AudioProcessingSettings(),
    val analysis: ClipAnalysisState = ClipAnalysisState(),
)
