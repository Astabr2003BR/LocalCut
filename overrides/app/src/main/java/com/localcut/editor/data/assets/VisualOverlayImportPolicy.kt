package com.localcut.editor.data.assets

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType

object VisualOverlayImportPolicy {
    private const val MICROS_PER_SECOND = 1_000_000L

    fun create(
        project: Project,
        asset: Asset,
        clipId: String,
        playheadUs: Long,
    ): Result<Clip> = runCatching {
        require(asset.type != AssetType.AUDIO) { "audio assets cannot be visual overlays" }
        require(project.fps > 0) { "project fps must be positive" }

        val primaryEndUs = project.tracks
            .asSequence()
            .filter {
                it.type == TrackType.VISUAL &&
                    it.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE
            }
            .flatMap { it.clips.asSequence() }
            .mapNotNull { clip ->
                if (clip.durationUs <= 0L || clip.startUs < 0L || clip.startUs > Long.MAX_VALUE - clip.durationUs) {
                    null
                } else {
                    clip.startUs + clip.durationUs
                }
            }
            .maxOrNull()
            ?: 0L
        require(primaryEndUs > 0L) { "add a primary visual clip before importing an overlay" }

        val frameUs = (MICROS_PER_SECOND / project.fps.toLong()).coerceAtLeast(1L)
        val latestStartUs = (primaryEndUs - minOf(frameUs, primaryEndUs)).coerceAtLeast(0L)
        val startUs = playheadUs.coerceIn(0L, latestStartUs)
        val remainingUs = primaryEndUs - startUs
        require(remainingUs > 0L) { "no visual timeline remains for overlay" }

        when (asset.type) {
            AssetType.IMAGE -> ImportedClipFactory.create(
                asset = asset,
                clipId = clipId,
                startUs = startUs,
                imageDurationUs = minOf(ImportedClipFactory.DEFAULT_IMAGE_DURATION_US, remainingUs),
            ).getOrThrow()

            AssetType.VIDEO -> {
                val sourceClip = ImportedClipFactory.create(
                    asset = asset,
                    clipId = clipId,
                    startUs = startUs,
                ).getOrThrow()
                val durationUs = minOf(sourceClip.durationUs, remainingUs)
                sourceClip.copy(
                    durationUs = durationUs,
                    sourceOutUs = sourceClip.sourceInUs + durationUs,
                )
            }

            AssetType.AUDIO -> error("unreachable")
        }
    }
}
