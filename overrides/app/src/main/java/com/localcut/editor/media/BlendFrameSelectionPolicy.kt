package com.localcut.editor.media

object BlendFrameSelectionPolicy {
    fun selectClosest(
        primaryTimeUs: Long,
        secondaryTimesUs: List<Long>,
        secondaryEnded: Boolean,
    ): Long? {
        require(primaryTimeUs >= 0L) { "BLEND_PRIMARY_TIME_INVALID" }
        require(secondaryTimesUs.all { it >= 0L }) { "BLEND_SECONDARY_TIME_INVALID" }
        require(secondaryTimesUs.zipWithNext().all { (left, right) -> right > left }) {
            "BLEND_SECONDARY_TIMES_NOT_STRICTLY_INCREASING"
        }
        if (secondaryTimesUs.isEmpty()) return null

        if (!secondaryEnded) {
            if (secondaryTimesUs.size < 2) return null
            if (secondaryTimesUs.none { it > primaryTimeUs }) return null
        }

        return secondaryTimesUs.minWithOrNull(
            compareBy<Long> { candidate -> distance(candidate, primaryTimeUs) }
                .thenBy { it },
        )
    }

    private fun distance(left: Long, right: Long): Long =
        if (left >= right) left - right else right - left
}
