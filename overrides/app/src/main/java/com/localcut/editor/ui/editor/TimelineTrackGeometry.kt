package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Track

data class TimelineTrackRow(
    val trackId: String,
    val topDp: Float,
    val bottomDp: Float,
)

data class TimelineTrackLayout(
    val rows: List<TimelineTrackRow>,
    val contentHeightDp: Float,
) {
    fun trackIdAt(yDp: Float): String? {
        if (!yDp.isFinite() || yDp < 0f) return null
        return rows.firstOrNull { row -> yDp >= row.topDp && yDp < row.bottomDp }?.trackId
    }
}

object TimelineTrackGeometry {
    const val DEFAULT_ROW_HEIGHT_DP: Float = 58f
    const val DEFAULT_ROW_GAP_DP: Float = 6f

    fun layout(
        tracks: List<Track>,
        rowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
        rowGapDp: Float = DEFAULT_ROW_GAP_DP,
    ): TimelineTrackLayout {
        require(rowHeightDp.isFinite() && rowHeightDp > 0f) { "rowHeightDp must be finite and positive" }
        require(rowGapDp.isFinite() && rowGapDp >= 0f) { "rowGapDp must be finite and non-negative" }

        val rows = tracks
            .sortedBy { it.order }
            .mapIndexed { index, track ->
                val top = index * (rowHeightDp + rowGapDp)
                TimelineTrackRow(
                    trackId = track.id,
                    topDp = top,
                    bottomDp = top + rowHeightDp,
                )
            }

        return TimelineTrackLayout(
            rows = rows,
            contentHeightDp = rows.lastOrNull()?.bottomDp ?: 0f,
        )
    }
}
