package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project

data class ReverseApplySnapshot(
    val clipId: String,
    val assetId: String,
    val assetFingerprint: String?,
    val sourceDurationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
)

object ReverseApplyGuard {
    fun capture(project: Project, clipId: String): Result<ReverseApplySnapshot> = runCatching {
        val clip = project.findClip(clipId) ?: error("REVERSE_GUARD_CLIP_NOT_FOUND")
        val asset = project.assets.firstOrNull { it.id == clip.assetId }
            ?: error("REVERSE_GUARD_ASSET_NOT_FOUND")
        require(asset.type == AssetType.VIDEO) { "REVERSE_GUARD_REQUIRES_VIDEO" }
        val durationUs = asset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("REVERSE_GUARD_DURATION_INVALID")
        require(!clip.timeMap.reverse) { "REVERSE_GUARD_ALREADY_REVERSED" }
        require(clip.timeMap.freezeSegments.isEmpty()) { "REVERSE_GUARD_FREEZE_UNSUPPORTED" }
        require(clip.sourceInUs >= 0L && clip.sourceOutUs > clip.sourceInUs && clip.sourceOutUs <= durationUs) {
            "REVERSE_GUARD_SOURCE_RANGE_INVALID"
        }
        ReverseApplySnapshot(
            clipId = clip.id,
            assetId = asset.id,
            assetFingerprint = asset.fingerprint,
            sourceDurationUs = durationUs,
            sourceInUs = clip.sourceInUs,
            sourceOutUs = clip.sourceOutUs,
        )
    }

    fun isStillCurrent(project: Project, snapshot: ReverseApplySnapshot): Boolean {
        val clip = project.findClip(snapshot.clipId) ?: return false
        if (clip.assetId != snapshot.assetId) return false
        if (clip.sourceInUs != snapshot.sourceInUs || clip.sourceOutUs != snapshot.sourceOutUs) return false
        if (clip.timeMap.reverse || clip.timeMap.freezeSegments.isNotEmpty()) return false
        val asset = project.assets.firstOrNull { it.id == snapshot.assetId } ?: return false
        if (asset.type != AssetType.VIDEO) return false
        if (asset.fingerprint != snapshot.assetFingerprint) return false
        if (asset.sourceDurationUs != snapshot.sourceDurationUs) return false
        return true
    }
}
