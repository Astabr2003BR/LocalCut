package com.localcut.editor.ui.editor

import com.localcut.editor.audio.BeatDetectionSettings

enum class BeatDetectionSensitivity {
    HIGH,
    STANDARD,
    LOW,
}

object BeatDetectionUiPolicy {
    fun settings(sensitivity: BeatDetectionSensitivity): BeatDetectionSettings = when (sensitivity) {
        BeatDetectionSensitivity.HIGH -> BeatDetectionSettings(
            analysisWindowMs = 40,
            minimumIntervalMs = 180,
            thresholdMultiplier = 1.35,
        )
        BeatDetectionSensitivity.STANDARD -> BeatDetectionSettings(
            analysisWindowMs = 40,
            minimumIntervalMs = 240,
            thresholdMultiplier = 1.6,
        )
        BeatDetectionSensitivity.LOW -> BeatDetectionSettings(
            analysisWindowMs = 50,
            minimumIntervalMs = 320,
            thresholdMultiplier = 2.0,
        )
    }

    fun label(sensitivity: BeatDetectionSensitivity): String = when (sensitivity) {
        BeatDetectionSensitivity.HIGH -> "Alta"
        BeatDetectionSensitivity.STANDARD -> "Padrão"
        BeatDetectionSensitivity.LOW -> "Baixa"
    }
}
