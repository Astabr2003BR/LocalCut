package com.localcut.editor.mobile

import com.localcut.editor.data.project.jsonString
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

object CutDeleteAfterResolver {
    fun resolvePayload(
        command: MobileHandoffCommand,
        project: Project,
        selectedClipId: String?,
    ): Result<String> = runCatching {
        val positionUs = command.resolveVisualClipAtPositionUs
            ?: return@runCatching command.payloadJson

        val visualClips = project.tracks
            .asSequence()
            .filter { it.type == TrackType.VISUAL }
            .flatMap { it.clips.asSequence() }
            .toList()

        fun containsCutPoint(startUs: Long, durationUs: Long): Boolean =
            positionUs > startUs && positionUs - startUs < durationUs

        val selected = selectedClipId
            ?.let { id -> visualClips.firstOrNull { it.id == id } }
            ?.takeIf { containsCutPoint(it.startUs, it.durationUs) }

        val clip = selected ?: run {
            val candidates = visualClips.filter { containsCutPoint(it.startUs, it.durationUs) }
            when (candidates.size) {
                0 -> error("CLIP_AT_POSITION_NOT_FOUND")
                1 -> candidates.single()
                else -> error("CLIP_AT_POSITION_AMBIGUOUS")
            }
        }

        "{\"clipId\":${jsonString(clip.id)},\"newEndUs\":$positionUs}"
    }
}
