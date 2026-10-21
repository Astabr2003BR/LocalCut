package com.localcut.editor.session

import com.localcut.editor.domain.history.AddTextOverlayCommand
import com.localcut.editor.domain.history.DeleteTextOverlayCommand
import com.localcut.editor.domain.history.EditorCommand
import com.localcut.editor.domain.history.InsertAssetClipCommand
import com.localcut.editor.domain.history.InsertVisualOverlayAssetClipCommand
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.history.MoveClipCommand
import com.localcut.editor.domain.history.TrimClipCommand
import com.localcut.editor.domain.history.TrimClipLeftEdgeCommand
import com.localcut.editor.domain.history.TrimClipRightEdgeCommand
import com.localcut.editor.domain.history.SplitClipCommand
import com.localcut.editor.domain.history.SetTransformCommand
import com.localcut.editor.domain.history.SetVolumeCommand
import com.localcut.editor.domain.history.SetAudioFadesCommand
import com.localcut.editor.domain.history.SetTrackMutedCommand
import com.localcut.editor.domain.history.SetTrackSoloCommand
import com.localcut.editor.domain.history.SetKeyframesCommand
import com.localcut.editor.domain.history.DeleteClipCommand
import com.localcut.editor.domain.history.DuplicateClipCommand
import com.localcut.editor.domain.history.UpdateTextOverlayCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

enum class SessionIssueCode { ASSET_UNAVAILABLE, PREVIEW_FAILED }

data class SessionIssue(
    val code: SessionIssueCode,
    val assetId: String? = null,
    val message: String
)

data class ProjectSessionState(
    val project: Project,
    val issues: List<SessionIssue> = emptyList(),
    val lastError: String? = null,
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

sealed interface EditorAction {
    data class Execute(val command: EditorCommand) : EditorAction
    data class AddImportedClip(val trackId: String, val asset: Asset, val clip: Clip) : EditorAction
    data class AddImportedOverlayClip(val asset: Asset, val clip: Clip) : EditorAction
    data class AddTextOverlay(val overlay: TextOverlay) : EditorAction
    data class UpdateTextOverlay(val overlay: TextOverlay) : EditorAction
    data class DeleteTextOverlay(val overlayId: String) : EditorAction
    data class SelectClip(val clipId: String?) : EditorAction
    data class SelectTrack(val trackId: String?) : EditorAction
    data class MoveSelected(val newStartUs: Long) : EditorAction
    data class SetSelectedTransform(val transform: TransformValue) : EditorAction
    data class SetSelectedVolume(val volume: Double) : EditorAction
    data class SetSelectedAudioFades(val fadeInUs: Long, val fadeOutUs: Long) : EditorAction
    data class SetTrackMuted(val trackId: String, val muted: Boolean) : EditorAction
    data class SetTrackSolo(val trackId: String, val solo: Boolean) : EditorAction
    data class SetSelectedKeyframes(val keyframes: List<TransformKeyframe>) : EditorAction
    data class TrimSelected(val newSourceInUs: Long, val newSourceOutUs: Long) : EditorAction
    data class TrimSelectedLeftEdge(val newProjectStartUs: Long) : EditorAction
    data class TrimSelectedRightEdge(val newProjectEndUs: Long) : EditorAction
    data class SplitSelected(val splitAtProjectUs: Long) : EditorAction
    data object DuplicateSelected : EditorAction
    data object DeleteSelected : EditorAction
    data object Undo : EditorAction
    data object Redo : EditorAction
}

class ProjectSession(
    initialProject: Project,
    private val preview: ProjectPreviewLoader,
    private val autosave: DebouncedProjectSaver,
    private val store: ProjectStore? = null,
    historyLimit: Int = 100
) {
    private val historyLimit = historyLimit
    private var history = HistoryManager(initialProject, historyLimit)
    private var restoreAttempted = false
    private var restoreResult: Result<Unit>? = null

    var state: ProjectSessionState = ProjectSessionState(initialProject)
        private set

    fun dispatch(action: EditorAction) {
        when (action) {
            is EditorAction.Execute -> execute(action.command)
            is EditorAction.AddImportedClip -> {
                execute(InsertAssetClipCommand(action.trackId, action.asset, action.clip))
                if (state.project.findClip(action.clip.id) != null) {
                    state = state.copy(
                        selectedClipId = action.clip.id,
                        selectedTrackId = action.trackId,
                    )
                }
            }
            is EditorAction.AddImportedOverlayClip -> {
                execute(InsertVisualOverlayAssetClipCommand(action.asset, action.clip))
                if (state.project.findClip(action.clip.id) != null) {
                    state = state.copy(
                        selectedClipId = action.clip.id,
                        selectedTrackId = trackIdForClip(state.project, action.clip.id),
                    )
                }
            }
            is EditorAction.AddTextOverlay -> execute(AddTextOverlayCommand(action.overlay))
            is EditorAction.UpdateTextOverlay -> execute(UpdateTextOverlayCommand(action.overlay))
            is EditorAction.DeleteTextOverlay -> execute(DeleteTextOverlayCommand(action.overlayId))
            is EditorAction.SelectClip -> selectClip(action.clipId)
            is EditorAction.SelectTrack -> selectTrack(action.trackId)
            is EditorAction.MoveSelected -> withSelected { execute(MoveClipCommand(it, action.newStartUs)) }
            is EditorAction.SetSelectedTransform -> withSelected { execute(SetTransformCommand(it, action.transform)) }
            is EditorAction.SetSelectedVolume -> withSelected { execute(SetVolumeCommand(it, action.volume)) }
            is EditorAction.SetSelectedAudioFades -> withSelected { execute(SetAudioFadesCommand(it, action.fadeInUs, action.fadeOutUs)) }
            is EditorAction.SetTrackMuted -> execute(SetTrackMutedCommand(action.trackId, action.muted))
            is EditorAction.SetTrackSolo -> execute(SetTrackSoloCommand(action.trackId, action.solo))
            is EditorAction.SetSelectedKeyframes -> withSelected { execute(SetKeyframesCommand(it, action.keyframes)) }
            is EditorAction.TrimSelected -> withSelected { execute(TrimClipCommand(it, action.newSourceInUs, action.newSourceOutUs)) }
            is EditorAction.TrimSelectedLeftEdge -> withSelected { execute(TrimClipLeftEdgeCommand(it, action.newProjectStartUs)) }
            is EditorAction.TrimSelectedRightEdge -> withSelected { execute(TrimClipRightEdgeCommand(it, action.newProjectEndUs)) }
            is EditorAction.SplitSelected -> withSelected { execute(SplitClipCommand(it, action.splitAtProjectUs)) }
            EditorAction.DuplicateSelected -> withSelected { clipId ->
                val duplicateClipId = nextDuplicateClipId(state.project, clipId)
                execute(DuplicateClipCommand(clipId, duplicateClipId))
                if (state.project.findClip(duplicateClipId) != null) {
                    state = state.copy(selectedClipId = duplicateClipId)
                }
            }
            EditorAction.DeleteSelected -> withSelected { clipId ->
                execute(DeleteClipCommand(clipId))
                if (state.project.findClip(clipId) == null) state = state.copy(selectedClipId = null)
            }
            EditorAction.Undo -> applyHistoryChange { history.undo() }
            EditorAction.Redo -> applyHistoryChange { history.redo() }
        }
    }

    fun restore(): Result<Unit> {
        if (restoreAttempted) return restoreResult ?: Result.success(Unit)
        restoreAttempted = true
        val source = store ?: return Result.failure<Unit>(IllegalStateException("project store unavailable")).also {
            restoreResult = it
            state = state.copy(lastError = it.exceptionOrNull()?.message)
        }
        val loaded = source.load()
        if (loaded.isFailure) {
            return Result.failure<Unit>(loaded.exceptionOrNull()!!).also {
                restoreResult = it
                state = state.copy(lastError = it.exceptionOrNull()?.message)
            }
        }
        val project = loaded.getOrThrow()
        history = HistoryManager(project, historyLimit)
        state = ProjectSessionState(project)
        refreshPreview(project)
        return Result.success(Unit).also { restoreResult = it }
    }

    private fun execute(command: EditorCommand) {
        try {
            history.execute(command)
            commitEditedState()
        } catch (t: Throwable) {
            state = state.copy(lastError = t.message ?: t::class.simpleName)
        }
    }

    private fun applyHistoryChange(change: () -> Boolean) {
        try {
            if (change()) commitEditedState()
        } catch (t: Throwable) {
            state = state.copy(lastError = t.message ?: t::class.simpleName)
        }
    }

    private fun selectClip(clipId: String?) {
        if (clipId == null) {
            state = state.copy(selectedClipId = null, lastError = null)
            return
        }
        val trackId = trackIdForClip(state.project, clipId)
        if (trackId == null) {
            state = state.copy(
                selectedClipId = null,
                selectedTrackId = null,
                lastError = "clip $clipId not found",
            )
            return
        }
        state = state.copy(
            selectedClipId = clipId,
            selectedTrackId = trackId,
            lastError = null,
        )
    }

    private fun selectTrack(trackId: String?) {
        if (trackId == null) {
            state = state.copy(
                selectedClipId = null,
                selectedTrackId = null,
                lastError = null,
            )
            return
        }
        if (state.project.tracks.none { it.id == trackId }) {
            state = state.copy(
                selectedClipId = null,
                selectedTrackId = null,
                lastError = "track $trackId not found",
            )
            return
        }
        state = state.copy(
            selectedClipId = null,
            selectedTrackId = trackId,
            lastError = null,
        )
    }

    private fun trackIdForClip(project: Project, clipId: String): String? =
        project.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }?.id

    private fun nextDuplicateClipId(project: Project, clipId: String): String {
        var suffix = 1L
        while (true) {
            val candidate = if (suffix == 1L) "$clipId-copy" else "$clipId-copy-$suffix"
            if (project.findClip(candidate) == null) return candidate
            if (suffix == Long.MAX_VALUE) {
                throw IllegalStateException("unable to allocate duplicate clip id")
            }
            suffix++
        }
    }

    private inline fun withSelected(action: (String) -> Unit) {
        val clipId = state.selectedClipId
        if (clipId == null) {
            state = state.copy(lastError = "no clip selected")
            return
        }
        action(clipId)
    }

    private fun commitEditedState() {
        val project = history.project
        val selectedClip = state.selectedClipId?.takeIf { project.findClip(it) != null }
        val selectedTrack = selectedClip?.let { trackIdForClip(project, it) }
            ?: state.selectedTrackId?.takeIf { selectedId -> project.tracks.any { it.id == selectedId } }
        state = state.copy(
            project = project,
            lastError = null,
            selectedClipId = selectedClip,
            selectedTrackId = selectedTrack,
            canUndo = history.canUndo,
            canRedo = history.canRedo
        )
        refreshPreview(project)
        autosave.submit(project)
    }

    private fun refreshPreview(project: Project) {
        val result = preview.load(project)
        val error = result.exceptionOrNull()
        state = if (error == null) {
            state.copy(issues = emptyList())
        } else {
            val issue = when (error) {
                is PreviewLoadException -> when (error.code) {
                    PreviewErrorCode.ASSET_UNAVAILABLE -> SessionIssue(SessionIssueCode.ASSET_UNAVAILABLE, error.assetId, error.message ?: "asset unavailable")
                    PreviewErrorCode.PREVIEW_FAILED -> SessionIssue(SessionIssueCode.PREVIEW_FAILED, error.assetId, error.message ?: "preview failed")
                }
                else -> SessionIssue(SessionIssueCode.PREVIEW_FAILED, null, error.message ?: "preview failed")
            }
            state.copy(issues = listOf(issue))
        }
    }
}
