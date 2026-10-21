package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.domain.timeline.TimelineValidator

data class InsertVisualOverlayAssetClipCommand(
    val asset: Asset,
    val clip: Clip,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(asset.type != AssetType.AUDIO) { "audio assets cannot be imported as visual overlays" }
        require(clip.assetId == asset.id) { "overlay clip must reference imported asset" }

        val existingTrack = project.tracks
            .asSequence()
            .filter {
                it.type == TrackType.VISUAL &&
                    it.collisionPolicy == TrackCollisionPolicy.OVERLAY &&
                    !it.locked
            }
            .sortedWith(compareBy<Track> { it.order }.thenBy { it.id })
            .firstOrNull()

        val createdTrack = existingTrack == null
        val targetTrack = existingTrack ?: newOverlayTrack(project)
        val projectWithTrack = if (createdTrack) {
            validated(project.copy(tracks = project.tracks + targetTrack))
        } else {
            project
        }
        val inserted = TimelineOps.insertAssetClip(projectWithTrack, targetTrack.id, asset, clip)
        return CommandResult(
            project = inserted,
            inverse = RemoveVisualOverlayAssetClipCommand(
                trackId = targetTrack.id,
                asset = asset,
                clip = clip,
                removeTrackAfterUndo = createdTrack,
            ),
        )
    }

    private fun newOverlayTrack(project: Project): Track {
        val maxOrder = project.tracks.maxOfOrNull { it.order } ?: -1
        require(maxOrder < Int.MAX_VALUE) { "cannot allocate overlay track order" }
        return Track(
            id = nextOverlayTrackId(project),
            type = TrackType.VISUAL,
            order = maxOrder + 1,
            collisionPolicy = TrackCollisionPolicy.OVERLAY,
        )
    }

    private fun nextOverlayTrackId(project: Project): String {
        val used = project.tracks.asSequence().map { it.id }.toHashSet()
        var suffix = 1L
        while (true) {
            val candidate = "visual-overlay-$suffix"
            if (candidate !in used) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate overlay track id" }
            suffix += 1L
        }
    }
}

private data class RemoveVisualOverlayAssetClipCommand(
    val trackId: String,
    val asset: Asset,
    val clip: Clip,
    val removeTrackAfterUndo: Boolean,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val withoutImport = TimelineOps.removeImportedAssetClip(project, trackId, asset.id, clip.id)
        val restored = if (removeTrackAfterUndo) {
            val track = withoutImport.tracks.firstOrNull { it.id == trackId }
                ?: error("created overlay track $trackId disappeared before undo")
            require(track.clips.isEmpty()) { "created overlay track $trackId is not empty during undo" }
            validated(withoutImport.copy(tracks = withoutImport.tracks.filterNot { it.id == trackId }))
        } else {
            withoutImport
        }
        return CommandResult(
            project = restored,
            inverse = InsertVisualOverlayAssetClipCommand(asset, clip),
        )
    }
}

private fun validated(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid visual overlay import: ${issues.first()}" }
    return project
}
