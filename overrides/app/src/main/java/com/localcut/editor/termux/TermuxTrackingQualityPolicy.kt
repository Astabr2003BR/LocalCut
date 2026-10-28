package com.localcut.editor.termux

enum class TermuxTrackingQuality {
    FAST,
    NORMAL,
    PRECISE,
}

object TermuxTrackingQualityPolicy {
    fun options(quality: TermuxTrackingQuality): TermuxTrackingOptions = when (quality) {
        TermuxTrackingQuality.FAST -> TermuxTrackingOptions(
            maxFrames = 120,
            sampleEvery = 4,
            maxCorners = 80,
        )
        TermuxTrackingQuality.NORMAL -> TermuxTrackingOptions(
            maxFrames = 180,
            sampleEvery = 2,
            maxCorners = 120,
        )
        TermuxTrackingQuality.PRECISE -> TermuxTrackingOptions(
            maxFrames = 300,
            sampleEvery = 1,
            maxCorners = 200,
        )
    }
}
