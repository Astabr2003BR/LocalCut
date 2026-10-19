package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.session.EditorAction

data class TimelineDragContext(
    val clipId: String,
    val region: TimelineHitRegion,
    val grabOffsetUs: Long,
    val minActionTimeUs: Long = 0L,
    val maxActionTimeUs: Long = Long.MAX_VALUE,
) {
    val selectAction: EditorAction = EditorAction.SelectClip(clipId)
}

object TimelineEditBounds {
    fun clampLeft(track: Track, clipId: String, requestedStartUs: Long): Long {
        val (clip, previous, _) = neighbours(track, clipId)
        val previousEndUs = previous?.let(::endUs) ?: 0L
        val latestStartUs = endUs(clip) - 1L
        return requestedStartUs.coerceIn(previousEndUs, latestStartUs)
    }

    fun clampRight(track: Track, clipId: String, requestedEndUs: Long): Long {
        val (clip, _, next) = neighbours(track, clipId)
        val earliestEndUs = clip.startUs + 1L
        val latestEndUs = next?.startUs ?: Long.MAX_VALUE
        return requestedEndUs.coerceIn(earliestEndUs, latestEndUs)
    }

    fun clampMove(track: Track, clipId: String, requestedStartUs: Long): Long {
        val (clip, previous, next) = neighbours(track, clipId)
        val earliestStartUs = previous?.let(::endUs) ?: 0L
        val latestStartUs = next?.let { (it.startUs - clip.durationUs).coerceAtLeast(earliestStartUs) } ?: Long.MAX_VALUE
        return requestedStartUs.coerceIn(earliestStartUs, latestStartUs)
    }

    fun bounds(track: Track, clipId: String, region: TimelineHitRegion): LongRange {
        val (clip, previous, next) = neighbours(track, clipId)
        return when (region) {
            TimelineHitRegion.BODY -> {
                val min = previous?.let(::endUs) ?: 0L
                val max = next?.let { (it.startUs - clip.durationUs).coerceAtLeast(min) } ?: Long.MAX_VALUE
                min..max
            }
            TimelineHitRegion.LEFT_HANDLE -> {
                val min = previous?.let(::endUs) ?: 0L
                min..(endUs(clip) - 1L)
            }
            TimelineHitRegion.RIGHT_HANDLE -> {
                val min = clip.startUs + 1L
                val max = next?.startUs ?: Long.MAX_VALUE
                min..max
            }
        }
    }

    private data class Neighbours(val clip: Clip, val previous: Clip?, val next: Clip?)

    private fun neighbours(track: Track, clipId: String): Neighbours {
        val sorted = track.clips.sortedBy { it.startUs }
        val index = sorted.indexOfFirst { it.id == clipId }
        require(index >= 0) { "clip $clipId not found in track ${track.id}" }
        return Neighbours(
            clip = sorted[index],
            previous = sorted.getOrNull(index - 1),
            next = sorted.getOrNull(index + 1),
        )
    }

    private fun endUs(clip: Clip): Long = clip.startUs + clip.durationUs
}

object TimelineDragPlanner {
    fun begin(
        project: Project,
        trackId: String,
        pointerXPx: Double,
        geometry: TimelineGeometry,
        handleWidthPx: Double,
    ): TimelineDragContext? {
        val hit = TimelineHitTester.hit(project, trackId, pointerXPx, geometry, handleWidthPx) ?: return null
        val clip = project.findClip(hit.clipId) ?: return null
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        val pointerUs = geometry.pxToTimeUs(pointerXPx)
        val grabOffsetUs = if (hit.region == TimelineHitRegion.BODY) {
            (pointerUs - clip.startUs).coerceIn(0L, clip.durationUs)
        } else {
            0L
        }
        val bounds = TimelineEditBounds.bounds(track, hit.clipId, hit.region)
        return TimelineDragContext(
            clipId = hit.clipId,
            region = hit.region,
            grabOffsetUs = grabOffsetUs,
            minActionTimeUs = bounds.first,
            maxActionTimeUs = bounds.last,
        )
    }

    fun actionAt(
        context: TimelineDragContext,
        pointerXPx: Double,
        geometry: TimelineGeometry,
    ): EditorAction {
        val pointerUs = geometry.pxToTimeUs(pointerXPx)
        val rawTargetUs = when (context.region) {
            TimelineHitRegion.BODY -> pointerUs - context.grabOffsetUs
            TimelineHitRegion.LEFT_HANDLE,
            TimelineHitRegion.RIGHT_HANDLE -> pointerUs
        }
        val targetUs = rawTargetUs.coerceIn(context.minActionTimeUs, context.maxActionTimeUs)
        return when (context.region) {
            TimelineHitRegion.BODY -> EditorAction.MoveSelected(targetUs)
            TimelineHitRegion.LEFT_HANDLE -> EditorAction.TrimSelectedLeftEdge(targetUs)
            TimelineHitRegion.RIGHT_HANDLE -> EditorAction.TrimSelectedRightEdge(targetUs)
        }
    }
}
