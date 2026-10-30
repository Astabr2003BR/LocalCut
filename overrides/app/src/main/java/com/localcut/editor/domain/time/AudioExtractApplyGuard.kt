package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType

data class AudioExtractApplySnapshot(
    val clipId: String,
    val sourceTrackId: String,
    val assetId: String,
    val assetFingerprint: String?,
    val sourceDurationUs: Long,
    val startUs: Long,
    val durationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val volume: Double,
    val fadeInUs: Long,
    val fadeOutUs: Long,
    val timeMap: TimeMap,
    val audioProcessing: AudioProcessingSettings,
)

object AudioExtractApplyGuard {
    fun capture(project: Project, clipId: String): Result<AudioExtractApplySnapshot> = runCatching {
        val track = project.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
            ?: error("EXTRACT_AUDIO_GUARD_CLIP_NOT_FOUND")
        require(track.type == TrackType.VISUAL) { "EXTRACT_AUDIO_GUARD_REQUIRES_VISUAL_TRACK" }
        require(!track.locked) { "EXTRACT_AUDIO_GUARD_TRACK_LOCKED" }
        val clip = track.clips.first { it.id == clipId }
        val asset = project.assets.firstOrNull { it.id == clip.assetId }
            ?: error("EXTRACT_AUDIO_GUARD_ASSET_NOT_FOUND")
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "EXTRACT_AUDIO_GUARD_REQUIRES_VIDEO"
        }
        val durationUs = asset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("EXTRACT_AUDIO_GUARD_DURATION_INVALID")
        require(clip.startUs >= 0L && clip.durationUs > 0L) { "EXTRACT_AUDIO_GUARD_TIMING_INVALID" }
        require(clip.sourceInUs >= 0L && clip.sourceOutUs > clip.sourceInUs && clip.sourceOutUs <= durationUs) {
            "EXTRACT_AUDIO_GUARD_SOURCE_RANGE_INVALID"
        }
        AudioExtractApplySnapshot(
            clipId = clip.id,
            sourceTrackId = track.id,
            assetId = asset.id,
            assetFingerprint = asset.fingerprint,
            sourceDurationUs = durationUs,
            startUs = clip.startUs,
            durationUs = clip.durationUs,
            sourceInUs = clip.sourceInUs,
            sourceOutUs = clip.sourceOutUs,
            volume = clip.volume,
            fadeInUs = clip.fadeInUs,
            fadeOutUs = clip.fadeOutUs,
            timeMap = clip.timeMap,
            audioProcessing = clip.audioProcessing,
        )
    }

    fun isStillCurrent(project: Project, snapshot: AudioExtractApplySnapshot): Boolean {
        val track = project.tracks.firstOrNull { it.id == snapshot.sourceTrackId } ?: return false
        if (track.type != TrackType.VISUAL || track.locked) return false
        val clip = track.clips.firstOrNull { it.id == snapshot.clipId } ?: return false
        if (clip.assetId != snapshot.assetId) return false
        if (clip.startUs != snapshot.startUs || clip.durationUs != snapshot.durationUs) return false
        if (clip.sourceInUs != snapshot.sourceInUs || clip.sourceOutUs != snapshot.sourceOutUs) return false
        if (clip.volume != snapshot.volume || clip.fadeInUs != snapshot.fadeInUs || clip.fadeOutUs != snapshot.fadeOutUs) return false
        if (clip.timeMap != snapshot.timeMap || clip.audioProcessing != snapshot.audioProcessing) return false
        val asset = project.assets.firstOrNull { it.id == snapshot.assetId } ?: return false
        if (asset.type != AssetType.VIDEO || asset.mimeType?.startsWith("video/") != true) return false
        if (asset.fingerprint != snapshot.assetFingerprint) return false
        if (asset.sourceDurationUs != snapshot.sourceDurationUs) return false
        return true
    }
}
