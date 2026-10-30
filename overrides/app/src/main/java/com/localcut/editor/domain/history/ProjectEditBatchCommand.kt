package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.VectorPath
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.timeline.TimelineValidator

enum class ProjectItemKind { VIDEO, IMAGE, AUDIO, TEXT, DESIGN, DRAWING }

sealed interface ProjectEditOperation {
    data class DeleteByKind(val kinds: Set<ProjectItemKind>) : ProjectEditOperation
    data class DeleteClip(val clipId: String) : ProjectEditOperation
    data class DeleteText(val overlayId: String) : ProjectEditOperation
    data class DeleteDesign(val elementId: String) : ProjectEditOperation
    data class AddDesign(val element: DesignElement) : ProjectEditOperation
    data class AddText(val overlay: TextOverlay) : ProjectEditOperation
    data class AddClip(
        val clipId: String,
        val assetId: String,
        val trackId: String,
        val startUs: Long,
        val durationUs: Long,
        val sourceInUs: Long = 0L,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val scale: Double = 1.0,
        val rotationDeg: Double = 0.0,
        val opacity: Double = 1.0,
        val volume: Double = 1.0,
    ) : ProjectEditOperation
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

    data class PatchDesign(
        val elementId: String,
        val startUs: Long? = null,
        val durationUs: Long? = null,
        val x: Double? = null,
        val y: Double? = null,
        val scale: Double? = null,
        val rotationDeg: Double? = null,
        val opacity: Double? = null,
        val zIndex: Int? = null,
        val fillArgb: Int? = null,
        val strokeArgb: Int? = null,
        val strokeWidthPx: Double? = null,
        val cornerRadiusPx: Double? = null,
        val points: List<PathPoint>? = null,
        val closed: Boolean? = null,
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
            edited = try {
                applyOperation(edited, operation)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("project edit operation ${index + 1} invalid", error)
            }
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
        is ProjectEditOperation.DeleteDesign -> deleteDesign(project, operation.elementId)
        is ProjectEditOperation.AddDesign -> addDesign(project, operation.element)
        is ProjectEditOperation.AddText -> addText(project, operation.overlay)
        is ProjectEditOperation.AddClip -> addClip(project, operation)
        is ProjectEditOperation.PatchText -> patchText(project, operation)
        is ProjectEditOperation.PatchDesign -> patchDesign(project, operation)
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
    val designIds = when {
        ProjectItemKind.DESIGN in kinds -> project.designElements.map { it.id }.toSet()
        ProjectItemKind.DRAWING in kinds -> project.designElements
            .filter { it.shape?.kind == com.localcut.editor.domain.model.ShapeKind.PATH }
            .map { it.id }
            .toSet()
        else -> emptySet()
    }
    require(clipIds.isNotEmpty() || textIds.isNotEmpty() || designIds.isNotEmpty()) { "no matching project items" }
    return removeItems(project, clipIds, textIds, designIds)
}

private fun deleteClip(project: Project, clipId: String): Project {
    require(project.findClip(clipId) != null) { "clip $clipId not found" }
    return removeItems(project, setOf(clipId), emptySet(), emptySet())
}

private fun deleteText(project: Project, overlayId: String): Project {
    require(project.textOverlays.any { it.id == overlayId }) { "text overlay $overlayId not found" }
    return removeItems(project, emptySet(), setOf(overlayId), emptySet())
}

private fun deleteDesign(project: Project, elementId: String): Project {
    require(project.designElements.any { it.id == elementId }) { "design element $elementId not found" }
    return removeItems(project, emptySet(), emptySet(), setOf(elementId))
}

private fun removeItems(
    project: Project,
    clipIds: Set<String>,
    textIds: Set<String>,
    designIds: Set<String>,
): Project {
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
        designElements = project.designElements
            .filterNot { it.id in designIds }
            .map { element ->
                element.copy(
                    clipId = element.clipId?.takeUnless { it in clipIds },
                    textOverlayId = element.textOverlayId?.takeUnless { it in textIds },
                )
            },
    )
}

private fun addDesign(project: Project, element: DesignElement): Project {
    require(project.designElements.none { it.id == element.id }) { "design element ${element.id} already exists" }
    validateDesignElement(element)
    return project.copy(designElements = project.designElements + element)
}

private fun addText(project: Project, overlay: TextOverlay): Project {
    require(project.textOverlays.none { it.id == overlay.id }) { "text overlay ${overlay.id} already exists" }
    return project.copy(textOverlays = project.textOverlays + overlay)
}

private fun addClip(project: Project, operation: ProjectEditOperation.AddClip): Project {
    require(operation.clipId.isNotBlank()) { "clipId must not be blank" }
    require(project.findClip(operation.clipId) == null) { "clip ${operation.clipId} already exists" }
    val asset = project.assets.firstOrNull { it.id == operation.assetId }
        ?: throw IllegalArgumentException("asset ${operation.assetId} not found")
    val trackIndex = project.tracks.indexOfFirst { it.id == operation.trackId }
    require(trackIndex >= 0) { "track ${operation.trackId} not found" }
    val track = project.tracks[trackIndex]
    require(!track.locked) { "track ${operation.trackId} is locked" }
    val expectedTrackType = if (asset.type == AssetType.AUDIO) TrackType.AUDIO else TrackType.VISUAL
    require(track.type == expectedTrackType) { "asset and track type mismatch" }
    require(operation.startUs >= 0L) { "startUs must be non-negative" }
    require(operation.durationUs > 0L) { "durationUs must be positive" }
    require(operation.sourceInUs >= 0L) { "sourceInUs must be non-negative" }
    require(operation.scale > 0.0) { "scale must be positive" }
    require(operation.opacity in 0.0..1.0) { "opacity must be in 0..1" }
    require(operation.volume >= 0.0) { "volume must be non-negative" }
    val sourceOutUs = Math.addExact(operation.sourceInUs, operation.durationUs)
    asset.sourceDurationUs?.let { sourceDurationUs ->
        require(sourceOutUs <= sourceDurationUs) { "clip source range exceeds asset duration" }
    }
    val clip = Clip(
        id = operation.clipId,
        assetId = operation.assetId,
        startUs = operation.startUs,
        durationUs = operation.durationUs,
        sourceInUs = operation.sourceInUs,
        sourceOutUs = sourceOutUs,
        transform = TransformValue(
            x = operation.x,
            y = operation.y,
            scale = operation.scale,
            rotationDeg = operation.rotationDeg,
        ),
        opacity = operation.opacity,
        volume = operation.volume,
    )
    val tracks = project.tracks.toMutableList().also {
        it[trackIndex] = track.copy(clips = track.clips + clip)
    }
    return project.copy(tracks = tracks)
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

private fun patchDesign(project: Project, operation: ProjectEditOperation.PatchDesign): Project {
    val index = project.designElements.indexOfFirst { it.id == operation.elementId }
    require(index >= 0) { "design element ${operation.elementId} not found" }
    val current = project.designElements[index]
    operation.startUs?.let { require(it >= 0L) { "design startUs must be non-negative" } }
    operation.durationUs?.let { require(it > 0L) { "design durationUs must be positive" } }
    operation.x?.let { require(it.isFinite()) { "design x must be finite" } }
    operation.y?.let { require(it.isFinite()) { "design y must be finite" } }
    operation.scale?.let { require(it.isFinite() && it > 0.0) { "design scale must be finite and positive" } }
    operation.rotationDeg?.let { require(it.isFinite()) { "design rotation must be finite" } }
    operation.opacity?.let { require(it.isFinite() && it in 0.0..1.0) { "design opacity must be in 0..1" } }
    operation.strokeWidthPx?.let { require(it.isFinite() && it >= 0.0) { "design strokeWidthPx must be non-negative" } }
    operation.cornerRadiusPx?.let { require(it.isFinite() && it >= 0.0) { "design cornerRadiusPx must be non-negative" } }

    val nextTransform = current.transform.copy(
        x = operation.x ?: current.transform.x,
        y = operation.y ?: current.transform.y,
        scale = operation.scale ?: current.transform.scale,
        rotationDeg = operation.rotationDeg ?: current.transform.rotationDeg,
    )
    val shapePatchRequested = operation.fillArgb != null || operation.strokeArgb != null ||
        operation.strokeWidthPx != null || operation.cornerRadiusPx != null ||
        operation.points != null || operation.closed != null
    val nextShape = if (shapePatchRequested) {
        val shape = requireNotNull(current.shape) { "design patch requires shape element" }
        val pathPatchRequested = operation.points != null || operation.closed != null
        val nextPath = if (pathPatchRequested) {
            require(shape.kind == com.localcut.editor.domain.model.ShapeKind.PATH) { "path patch requires PATH element" }
            val currentPath = requireNotNull(shape.path) { "PATH element missing path" }
            VectorPath(
                points = operation.points ?: currentPath.points,
                closed = operation.closed ?: currentPath.closed,
            )
        } else {
            shape.path
        }
        shape.copy(
            path = nextPath,
            fillArgb = operation.fillArgb ?: shape.fillArgb,
            strokeArgb = operation.strokeArgb ?: shape.strokeArgb,
            strokeWidthPx = operation.strokeWidthPx ?: shape.strokeWidthPx,
            cornerRadiusPx = operation.cornerRadiusPx ?: shape.cornerRadiusPx,
        )
    } else {
        current.shape
    }
    val next = current.copy(
        startUs = operation.startUs ?: current.startUs,
        durationUs = operation.durationUs ?: current.durationUs,
        transform = nextTransform,
        opacity = operation.opacity ?: current.opacity,
        zIndex = operation.zIndex ?: current.zIndex,
        shape = nextShape,
    )
    validateDesignElement(next)
    val elements = project.designElements.toMutableList().also { it[index] = next }
    return project.copy(designElements = elements)
}

private fun validateDesignElement(element: DesignElement) {
    require(element.transform.x.isFinite() && element.transform.y.isFinite()) { "design transform position must be finite" }
    require(element.transform.scale.isFinite() && element.transform.scale > 0.0) { "design transform scale must be positive" }
    require(element.transform.rotationDeg.isFinite()) { "design transform rotation must be finite" }
    val issues = element.validationIssues()
    require(issues.isEmpty()) { issues.first() }
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
