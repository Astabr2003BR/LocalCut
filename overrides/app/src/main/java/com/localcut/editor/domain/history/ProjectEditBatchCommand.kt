package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.timeline.TimelineValidator

enum class ProjectItemKind { VIDEO, IMAGE, AUDIO, TEXT }

sealed interface ProjectEditOperation {
    data class DeleteByKind(val kinds: Set<ProjectItemKind>) : ProjectEditOperation
    data class DeleteClip(val clipId: String) : ProjectEditOperation
    data class DeleteText(val overlayId: String) : ProjectEditOperation
    data class AddText(val overlay: TextOverlay) : ProjectEditOperation
    data class PatchText(
        val overlayId: String,
        val text: String? = null,
        val startUs: Long? = null,
        val durationUs: Long? = null,
        val x: Double? = null,
        val y: Double? = null,
        val scale: Double? = null,
        val rotationDeg: Double? = null,
        val opacity: Double? = null,
        val fontSizePx: Double? = null,
        val fillArgb: Int? = null,
        val zIndex: Int? = null,
    ) : ProjectEditOperation

    data class PatchClip(
        val clipId: String,
        val startUs: Long? = null,
        val x: Double? = null,
        val y: Double? = null,
        val scale: Double? = null,
        val rotationDeg: Double? = null,
        val opacity: Double? = null,
        val volume: Double? = null,
        val transform: TransformValue? = null,
    ) : ProjectEditOperation
}

data class ProjectEditBatchCommand(
    val operations: List<ProjectEditOperation>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(operations.isNotEmpty()) { "project edit batch requires at least one operation" }
        require(operations.size <= MAX_OPERATIONS) { "project edit batch exceeds $MAX_OPERATIONS operations" }

        var edited = project
        operations.forEachIndexed { index, operation ->
            edited = applyOperation(edited, operation)
            validate(edited, "project edit operation ${index + 1}")
        }
        require(edited != project) { "project edit batch made no changes" }
        return CommandResult(
            project = edited,
            inverse = RestoreProjectEditSnapshotCommand(project, operations),
        )
    }

    private fun applyOperation(project: Project, operation: ProjectEditOperation): Project = when (operation) {
        is ProjectEditOperation.DeleteByKind -> deleteByKind(project, operation.kinds)
        is ProjectEditOperation.DeleteClip -> deleteClip(project, operation.clipId)
        is ProjectEditOperation.DeleteText -> deleteText(project, operation.overlayId)
        is ProjectEditOperation.AddText -> addText(project, operation.overlay)
        is ProjectEditOperation.PatchText -> patchText(project, operation)
        is ProjectEditOperation.PatchClip -> patchClip(project, operation)
    }

    companion object {
        const val MAX_OPERATIONS = 32
    }
}

private data class RestoreProjectEditSnapshotCommand(
    val snapshot: Project,
    val redoOperations: List<ProjectEditOperation>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        validate(snapshot, "restored project edit snapshot")
        return CommandResult(
            project = snapshot,
            inverse = ProjectEditBatchCommand(redoOperations),
        )
    }
}

private fun deleteByKind(project: Project, kinds: Set<ProjectItemKind>): Project {
    require(kinds.isNotEmpty()) { "deleteByKind requires at least one kind" }
    val assetsById = project.assets.associateBy { it.id }
    val clipIds = project.tracks.asSequence()
        .flatMap { it.clips.asSequence() }
        .filter { clip ->
            when (assetsById[clip.assetId]?.type) {
                AssetType.VIDEO -> ProjectItemKind.VIDEO in kinds
                AssetType.IMAGE -> ProjectItemKind.IMAGE in kinds
                AssetType.AUDIO -> ProjectItemKind.AUDIO in kinds
                null -> false
            }
        }
        .map { it.id }
        .toSet()
    val textIds = if (ProjectItemKind.TEXT in kinds) project.textOverlays.map { it.id }.toSet() else emptySet()
    require(clipIds.isNotEmpty() || textIds.isNotEmpty()) { "no matching project items" }
    return removeItems(project, clipIds, textIds)
}

private fun deleteClip(project: Project, clipId: String): Project {
    require(project.findClip(clipId) != null) { "clip $clipId not found" }
    return removeItems(project, setOf(clipId), emptySet())
}

private fun deleteText(project: Project, overlayId: String): Project {
    require(project.textOverlays.any { it.id == overlayId }) { "text overlay $overlayId not found" }
    return removeItems(project, emptySet(), setOf(overlayId))
}

private fun removeItems(project: Project, clipIds: Set<String>, textIds: Set<String>): Project {
    val remainingNodes = project.compositionGraph.nodes.filterNot { node ->
        node.kind == CompositionNodeKind.LAYER && node.clipId?.let { it in clipIds } == true
    }
    val remainingNodeIds = remainingNodes.map { it.id }.toSet()
    val cleanedNodes = remainingNodes.map { node ->
        if (node.parentId != null && node.parentId !in remainingNodeIds) node.copy(parentId = null) else node
    }
    return project.copy(
        tracks = project.tracks.map { track ->
            track.copy(clips = track.clips.filterNot { it.id in clipIds })
        },
        textOverlays = project.textOverlays.filterNot { it.id in textIds },
        transitions = project.transitions.filterNot { transition ->
            transition.fromClipId in clipIds || transition.toClipId in clipIds
        },
        compositionGraph = project.compositionGraph.copy(nodes = cleanedNodes),
        designElements = project.designElements.map { element ->
            element.copy(
                clipId = element.clipId?.takeUnless { it in clipIds },
                textOverlayId = element.textOverlayId?.takeUnless { it in textIds },
            )
        },
    )
}

private fun addText(project: Project, overlay: TextOverlay): Project {
    require(project.textOverlays.none { it.id == overlay.id }) { "text overlay ${overlay.id} already exists" }
    return project.copy(textOverlays = project.textOverlays + overlay)
}

private fun patchText(project: Project, operation: ProjectEditOperation.PatchText): Project {
    val index = project.textOverlays.indexOfFirst { it.id == operation.overlayId }
    require(index >= 0) { "text overlay ${operation.overlayId} not found" }
    val current = project.textOverlays[index]
    val nextTransform = current.transform.copy(
        x = operation.x ?: current.transform.x,
        y = operation.y ?: current.transform.y,
        scale = operation.scale ?: current.transform.scale,
        rotationDeg = operation.rotationDeg ?: current.transform.rotationDeg,
    )
    val nextStyle = current.style.copy(
        fontSizePx = operation.fontSizePx ?: current.style.fontSizePx,
        fillArgb = operation.fillArgb ?: current.style.fillArgb,
    )
    val next = current.copy(
        text = operation.text ?: current.text,
        startUs = operation.startUs ?: current.startUs,
        durationUs = operation.durationUs ?: current.durationUs,
        transform = nextTransform,
        opacity = operation.opacity ?: current.opacity,
        style = nextStyle,
        zIndex = operation.zIndex ?: current.zIndex,
    )
    val overlays = project.textOverlays.toMutableList().also { it[index] = next }
    return project.copy(textOverlays = overlays)
}

private fun patchClip(project: Project, operation: ProjectEditOperation.PatchClip): Project {
    val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == operation.clipId } }
    require(trackIndex >= 0) { "clip ${operation.clipId} not found" }
    val track = project.tracks[trackIndex]
    val clipIndex = track.clips.indexOfFirst { it.id == operation.clipId }
    val current = track.clips[clipIndex]
    val baseTransform = operation.transform ?: current.transform
    val nextTransform = baseTransform.copy(
        x = operation.x ?: baseTransform.x,
        y = operation.y ?: baseTransform.y,
        scale = operation.scale ?: baseTransform.scale,
        rotationDeg = operation.rotationDeg ?: baseTransform.rotationDeg,
        opacity = operation.opacity ?: baseTransform.opacity,
    )
    val next = current.copy(
        startUs = operation.startUs ?: current.startUs,
        transform = nextTransform,
        volume = operation.volume ?: current.volume,
    )
    val clips = track.clips.toMutableList().also { it[clipIndex] = next }
    val tracks = project.tracks.toMutableList().also { it[trackIndex] = track.copy(clips = clips) }
    return project.copy(tracks = tracks)
}

private fun validate(project: Project, context: String) {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "$context invalid: ${issues.first()}" }
}
