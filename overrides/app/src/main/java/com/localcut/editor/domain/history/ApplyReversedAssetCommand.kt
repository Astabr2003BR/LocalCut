package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator
import com.localcut.editor.domain.time.ReverseClipReplacementPlanner

data class ApplyReversedAssetCommand(
    val clipId: String,
    val reversedAsset: Asset,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(reversedAsset.type == AssetType.VIDEO) { "REVERSE_REPLACEMENT_REQUIRES_VIDEO" }
        require(reversedAsset.mimeType?.startsWith("video/") == true) { "REVERSE_REPLACEMENT_MIME_INVALID" }
        require(reversedAsset.id.isNotBlank()) { "REVERSE_REPLACEMENT_ASSET_ID_REQUIRED" }
        require(project.assets.none { it.id == reversedAsset.id }) { "REVERSE_REPLACEMENT_ASSET_ALREADY_EXISTS" }

        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        require(trackIndex >= 0) { "REVERSE_CLIP_NOT_FOUND" }
        val track = project.tracks[trackIndex]
        require(!track.locked) { "REVERSE_CLIP_TRACK_LOCKED" }
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "REVERSE_CLIP_NOT_FOUND" }
        val originalClip = track.clips[clipIndex]
        val originalAsset = project.assets.firstOrNull { it.id == originalClip.assetId }
            ?: error("REVERSE_ORIGINAL_ASSET_NOT_FOUND")
        require(originalAsset.type == AssetType.VIDEO) { "REVERSE_ORIGINAL_REQUIRES_VIDEO" }
        val originalDurationUs = originalAsset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("REVERSE_ORIGINAL_DURATION_INVALID")
        val replacementDurationUs = reversedAsset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("REVERSE_REPLACEMENT_DURATION_INVALID")

        val replacementClip = ReverseClipReplacementPlanner.replace(
            clip = originalClip,
            replacementAssetId = reversedAsset.id,
            originalSourceDurationUs = originalDurationUs,
            replacementSourceDurationUs = replacementDurationUs,
        )
        val replacementTrack = track.copy(
            clips = track.clips.toMutableList().also { it[clipIndex] = replacementClip },
        )
        val updated = project.copy(
            assets = project.assets + reversedAsset,
            tracks = project.tracks.toMutableList().also { it[trackIndex] = replacementTrack },
        )
        require(TimelineValidator.validate(updated).isEmpty()) { "REVERSE_REPLACEMENT_PROJECT_INVALID" }

        return CommandResult(
            project = updated,
            inverse = RestoreOriginalClipMediaCommand(
                clipId = clipId,
                originalClip = originalClip,
                reversedAsset = reversedAsset,
            ),
        )
    }
}

private data class RestoreOriginalClipMediaCommand(
    val clipId: String,
    val originalClip: Clip,
    val reversedAsset: Asset,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        require(trackIndex >= 0) { "REVERSE_UNDO_CLIP_NOT_FOUND" }
        val track = project.tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "REVERSE_UNDO_CLIP_NOT_FOUND" }
        require(track.clips[clipIndex].assetId == reversedAsset.id) { "REVERSE_UNDO_ASSET_MISMATCH" }
        require(project.tracks.asSequence().flatMap { it.clips.asSequence() }
            .filter { it.id != clipId }
            .none { it.assetId == reversedAsset.id }) { "REVERSE_UNDO_ASSET_STILL_REFERENCED" }

        val restoredTrack = track.copy(
            clips = track.clips.toMutableList().also { it[clipIndex] = originalClip },
        )
        val restored = project.copy(
            assets = project.assets.filterNot { it.id == reversedAsset.id },
            tracks = project.tracks.toMutableList().also { it[trackIndex] = restoredTrack },
        )
        require(TimelineValidator.validate(restored).isEmpty()) { "REVERSE_UNDO_PROJECT_INVALID" }

        return CommandResult(
            project = restored,
            inverse = ApplyReversedAssetCommand(clipId, reversedAsset),
        )
    }
}
