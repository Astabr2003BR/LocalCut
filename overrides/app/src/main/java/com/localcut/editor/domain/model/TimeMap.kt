package com.localcut.editor.domain.model

data class SpeedPoint(
    val offsetUs: Long,
    val speed: Double,
)

data class FreezeSegment(
    val startOffsetUs: Long,
    val durationUs: Long,
    val sourceOffsetUs: Long,
)

data class TimeMap(
    val baseSpeed: Double = 1.0,
    val reverse: Boolean = false,
    val speedPoints: List<SpeedPoint> = emptyList(),
    val freezeSegments: List<FreezeSegment> = emptyList(),
) {
    fun validationIssues(): List<String> = buildList {
        if (!baseSpeed.isFinite() || baseSpeed <= 0.0) add("baseSpeed must be finite and positive")
        if (speedPoints.any { it.offsetUs < 0L }) add("speed point offsets must be non-negative")
        if (speedPoints.zipWithNext().any { (left, right) -> right.offsetUs <= left.offsetUs }) {
            add("speed point offsets must be strictly increasing")
        }
        if (speedPoints.any { !it.speed.isFinite() || it.speed <= 0.0 }) {
            add("speed point speed must be finite and positive")
        }
        if (freezeSegments.any { it.startOffsetUs < 0L || it.durationUs <= 0L || it.sourceOffsetUs < 0L }) {
            add("freeze segment values must be non-negative and duration must be positive")
        }
        if (freezeSegments.sortedBy { it.startOffsetUs }.zipWithNext().any { (left, right) ->
                left.startOffsetUs + left.durationUs > right.startOffsetUs
            }) {
            add("freeze segments must not overlap")
        }
    }
}
