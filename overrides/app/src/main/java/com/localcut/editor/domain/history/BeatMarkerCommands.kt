package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator
import kotlin.math.abs

data class AddBeatMarkerCommand(
    val marker: BeatMarker,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.beatMarkers.none { it.id == marker.id }) {
            "beat marker ${marker.id} already exists"
        }
        val markers = (project.beatMarkers + marker).sortedWith(compareBy<BeatMarker> { it.timeUs }.thenBy { it.id })
        val edited = validatedBeatProject(project.copy(beatMarkers = markers))
        return CommandResult(
            project = edited,
            inverse = DeleteBeatMarkerCommand(marker.id),
        )
    }
}

data class DeleteBeatMarkerCommand(
    val markerId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.beatMarkers.indexOfFirst { it.id == markerId }
        require(index >= 0) { "beat marker $markerId not found" }
        val removed = project.beatMarkers[index]
        val markers = project.beatMarkers.toMutableList().also { it.removeAt(index) }
        val edited = validatedBeatProject(project.copy(beatMarkers = markers))
        return CommandResult(
            project = edited,
            inverse = RestoreBeatMarkerCommand(index, removed),
        )
    }
}

data class SetBeatMarkersCommand(
    val markers: List<BeatMarker>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val normalized = markers.sortedWith(compareBy<BeatMarker> { it.timeUs }.thenBy { it.id })
        val previous = project.beatMarkers
        val edited = validatedBeatProject(project.copy(beatMarkers = normalized))
        return CommandResult(
            project = edited,
            inverse = SetBeatMarkersCommand(previous),
        )
    }
}

data class ApplyDetectedBeatMarkersCommand(
    val clipId: String,
    val detectedRelativeMarkers: List<BeatMarker>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val clip = project.findClip(clipId) ?: error("clip $clipId not found for beat analysis")
        val asset = project.assets.firstOrNull { it.id == clip.assetId }
            ?: error("asset ${clip.assetId} not found for beat analysis")
        require(asset.type != AssetType.IMAGE) { "beat analysis requires audio-capable media" }
        require(clip.sourceOutUs > clip.sourceInUs) { "beat analysis requires a positive source range" }
        require(
            !clip.timeMap.reverse &&
                clip.timeMap.freezeSegments.isEmpty() &&
                clip.timeMap.speedPoints.isEmpty() &&
                abs(clip.timeMap.baseSpeed - 1.0) < 1e-9,
        ) { "beat analysis mapping currently requires neutral 1x time mapping" }

        val prefix = autoMarkerPrefix(clip.id)
        val preserved = project.beatMarkers.filterNot { it.id.startsWith(prefix) }
        val mapped = detectedRelativeMarkers
            .filter { it.timeUs in 0L..clip.durationUs }
            .mapIndexed { index, marker ->
                BeatMarker(
                    id = "$prefix${index + 1}",
                    timeUs = clip.startUs + marker.timeUs,
                    strength = marker.strength,
                )
            }
        val previous = project.beatMarkers
        val merged = (preserved + mapped).sortedWith(compareBy<BeatMarker> { it.timeUs }.thenBy { it.id })
        val edited = validatedBeatProject(project.copy(beatMarkers = merged))
        return CommandResult(
            project = edited,
            inverse = SetBeatMarkersCommand(previous),
        )
    }
}

private data class RestoreBeatMarkerCommand(
    val index: Int,
    val marker: BeatMarker,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.beatMarkers.none { it.id == marker.id }) {
            "beat marker ${marker.id} already exists"
        }
        require(index in 0..project.beatMarkers.size) { "beat marker restore index out of range" }
        val markers = project.beatMarkers.toMutableList().also { it.add(index, marker) }
        val edited = validatedBeatProject(project.copy(beatMarkers = markers))
        return CommandResult(
            project = edited,
            inverse = DeleteBeatMarkerCommand(marker.id),
        )
    }
}

private fun autoMarkerPrefix(clipId: String): String =
    "auto-beat-${clipId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-"

private fun validatedBeatProject(project: Project): Project {
    val beatIssues = BeatMarker.validationIssues(project.beatMarkers)
    require(beatIssues.isEmpty()) { "invalid beat marker edit: ${beatIssues.first()}" }
    val timelineIssues = TimelineValidator.validate(project)
    require(timelineIssues.isEmpty()) { "invalid beat marker edit: ${timelineIssues.first()}" }
    return project
}
