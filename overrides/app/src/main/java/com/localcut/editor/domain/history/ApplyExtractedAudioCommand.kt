package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineValidator

data class ApplyExtractedAudioCommand(
    val sourceClipId: String,
    val extractedAsset: Asset,
    val extractedClipId: String,
    val extractedTrackId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(extractedAsset.type == AssetType.AUDIO) { "EXTRACT_AUDIO_REPLACEMENT_REQUIRES_AUDIO" }
        require(extractedAsset.mimeType?.startsWith("audio/") == true) { "EXTRACT_AUDIO_REPLACEMENT_MIME_INVALID" }
        require(extractedAsset.id.isNotBlank()) { "EXTRACT_AUDIO_ASSET_ID_REQUIRED" }
        require(extractedClipId.isNotBlank()) { "EXTRACT_AUDIO_CLIP_ID_REQUIRED" }
        require(extractedTrackId.isNotBlank()) { "EXTRACT_AUDIO_TRACK_ID_REQUIRED" }
        require(project.assets.none { it.id == extractedAsset.id }) { "EXTRACT_AUDIO_ASSET_ALREADY_EXISTS" }
        require(project.tracks.none { it.id == extractedTrackId }) { "EXTRACT_AUDIO_TRACK_ALREADY_EXISTS" }
        require(project.tracks.asSequence().flatMap { it.clips.asSequence() }.none { it.id == extractedClipId }) {
            "EXTRACT_AUDIO_CLIP_ALREADY_EXISTS"
        }

        val sourceTrackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == sourceClipId } }
        require(sourceTrackIndex >= 0) { "EXTRACT_AUDIO_SOURCE_CLIP_NOT_FOUND" }
        val sourceTrack = project.tracks[sourceTrackIndex]
        require(sourceTrack.type == TrackType.VISUAL) { "EXTRACT_AUDIO_SOURCE_TRACK_INVALID" }
        require(!sourceTrack.locked) { "EXTRACT_AUDIO_SOURCE_TRACK_LOCKED" }
        val sourceClipIndex = sourceTrack.clips.indexOfFirst { it.id == sourceClipId }
        require(sourceClipIndex >= 0) { "EXTRACT_AUDIO_SOURCE_CLIP_NOT_FOUND" }
        val sourceClip = sourceTrack.clips[sourceClipIndex]
        val sourceAsset = project.assets.firstOrNull { it.id == sourceClip.assetId }
            ?: error("EXTRACT_AUDIO_SOURCE_ASSET_NOT_FOUND")
        require(sourceAsset.type == AssetType.VIDEO) { "EXTRACT_AUDIO_SOURCE_REQUIRES_VIDEO" }
        require(sourceAsset.mimeType?.startsWith("video/") == true) { "EXTRACT_AUDIO_SOURCE_MIME_INVALID" }
        val extractedDurationUs = extractedAsset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("EXTRACT_AUDIO_DURATION_INVALID")
        require(sourceClip.sourceInUs >= 0L && sourceClip.sourceOutUs > sourceClip.sourceInUs) {
            "EXTRACT_AUDIO_SOURCE_RANGE_INVALID"
        }
        require(sourceClip.sourceOutUs <= extractedDurationUs) { "EXTRACT_AUDIO_SOURCE_RANGE_OUT_OF_BOUNDS" }

        val extractedClip = Clip(
            id = extractedClipId,
            assetId = extractedAsset.id,
            startUs = sourceClip.startUs,
            durationUs = sourceClip.durationUs,
            sourceInUs = sourceClip.sourceInUs,
            sourceOutUs = sourceClip.sourceOutUs,
            volume = sourceClip.volume,
            fadeInUs = sourceClip.fadeInUs,
            fadeOutUs = sourceClip.fadeOutUs,
            timeMap = sourceClip.timeMap,
            audioProcessing = sourceClip.audioProcessing,
        )
        val mutedSourceTrack = sourceTrack.copy(
            clips = sourceTrack.clips.toMutableList().also { clips ->
                clips[sourceClipIndex] = sourceClip.copy(volume = 0.0)
            },
        )
        val nextOrder = project.tracks.maxOfOrNull { it.order }?.let { Math.addExact(it, 1) } ?: 0
        val extractedTrack = Track(
            id = extractedTrackId,
            type = TrackType.AUDIO,
            order = nextOrder,
            collisionPolicy = TrackCollisionPolicy.OVERLAY,
            clips = listOf(extractedClip),
        )
        val updated = project.copy(
            assets = project.assets + extractedAsset,
            tracks = project.tracks.toMutableList().also { tracks ->
                tracks[sourceTrackIndex] = mutedSourceTrack
                tracks += extractedTrack
            },
        )
        require(TimelineValidator.validate(updated).isEmpty()) { "EXTRACT_AUDIO_PROJECT_INVALID" }

        return CommandResult(
            project = updated,
            inverse = RestoreExtractedAudioCommand(
                sourceClipId = sourceClipId,
                originalSourceClip = sourceClip,
                extractedAsset = extractedAsset,
                extractedClipId = extractedClipId,
                extractedTrackId = extractedTrackId,
            ),
        )
    }
}

private data class RestoreExtractedAudioCommand(
    val sourceClipId: String,
    val originalSourceClip: Clip,
    val extractedAsset: Asset,
    val extractedClipId: String,
    val extractedTrackId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val sourceTrackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == sourceClipId } }
        require(sourceTrackIndex >= 0) { "EXTRACT_AUDIO_UNDO_SOURCE_CLIP_NOT_FOUND" }
        val sourceTrack = project.tracks[sourceTrackIndex]
        val sourceClipIndex = sourceTrack.clips.indexOfFirst { it.id == sourceClipId }
        require(sourceClipIndex >= 0) { "EXTRACT_AUDIO_UNDO_SOURCE_CLIP_NOT_FOUND" }
        val currentSourceClip = sourceTrack.clips[sourceClipIndex]
        require(currentSourceClip.assetId == originalSourceClip.assetId) { "EXTRACT_AUDIO_UNDO_SOURCE_ASSET_MISMATCH" }
        require(currentSourceClip.volume == 0.0) { "EXTRACT_AUDIO_UNDO_SOURCE_VOLUME_MISMATCH" }

        val extractedTrack = project.tracks.firstOrNull { it.id == extractedTrackId }
            ?: error("EXTRACT_AUDIO_UNDO_TRACK_NOT_FOUND")
        require(extractedTrack.type == TrackType.AUDIO) { "EXTRACT_AUDIO_UNDO_TRACK_INVALID" }
        val extractedClip = extractedTrack.clips.singleOrNull { it.id == extractedClipId }
            ?: error("EXTRACT_AUDIO_UNDO_CLIP_NOT_FOUND")
        require(extractedClip.assetId == extractedAsset.id) { "EXTRACT_AUDIO_UNDO_ASSET_MISMATCH" }
        require(project.tracks.asSequence().flatMap { it.clips.asSequence() }
            .filter { it.id != extractedClipId }
            .none { it.assetId == extractedAsset.id }) { "EXTRACT_AUDIO_UNDO_ASSET_STILL_REFERENCED" }

        val restoredSourceTrack = sourceTrack.copy(
            clips = sourceTrack.clips.toMutableList().also { clips ->
                clips[sourceClipIndex] = originalSourceClip
            },
        )
        val restored = project.copy(
            assets = project.assets.filterNot { it.id == extractedAsset.id },
            tracks = project.tracks.mapNotNull { track ->
                when (track.id) {
                    extractedTrackId -> null
                    sourceTrack.id -> restoredSourceTrack
                    else -> track
                }
            },
        )
        require(TimelineValidator.validate(restored).isEmpty()) { "EXTRACT_AUDIO_UNDO_PROJECT_INVALID" }

        return CommandResult(
            project = restored,
            inverse = ApplyExtractedAudioCommand(
                sourceClipId = sourceClipId,
                extractedAsset = extractedAsset,
                extractedClipId = extractedClipId,
                extractedTrackId = extractedTrackId,
            ),
        )
    }
}
