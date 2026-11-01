package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track

internal object TrackEditGuard {
    fun requireUnlocked(track: Track) {
        if (track.locked) {
            throw TimelineEditException(
                TimelineErrorCode.TRACK_LOCKED,
                "track locked: ${track.id}",
            )
        }
    }

    fun requireClipTrackUnlocked(project: Project, clipId: String): Track {
        val track = project.tracks.firstOrNull { candidate ->
            candidate.clips.any { clip -> clip.id == clipId }
        } ?: throw TimelineEditException(
            TimelineErrorCode.CLIP_NOT_FOUND,
            "clip $clipId not found",
        )
        requireUnlocked(track)
        return track
    }
}
