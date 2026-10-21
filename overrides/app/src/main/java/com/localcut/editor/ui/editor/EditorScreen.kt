package com.localcut.editor.ui.editor

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.media.ExportCapabilities
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.ExportSettingsState
import com.localcut.editor.media.ExportVideoCodec
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.session.EditorAction
import com.localcut.editor.ui.editor.components.InspectorPane
import com.localcut.editor.ui.editor.components.PreviewPane
import com.localcut.editor.ui.editor.components.TimelinePane

data class ExportUiState(
    val running: Boolean = false,
    val progressPercent: Int? = null,
    val message: String? = null,
)

@UnstableApi
@OptIn(ExperimentalApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    previewController: Media3PreviewController,
    exportState: ExportUiState,
    exportCapabilities: ExportCapabilities,
    onImportRequested: () -> Unit,
    onImportAudioRequested: () -> Unit,
    onExportRequested: (ExportProfile) -> Unit,
    onCancelExport: () -> Unit,
    onFinalPreviewRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.sessionState.collectAsState()
    val preview by viewModel.previewState.collectAsState()
    val configuration = LocalConfiguration.current
    var loopEnabled by remember { mutableStateOf(false) }
    var showExportSettings by remember { mutableStateOf(false) }
    var showTextManager by remember { mutableStateOf(false) }
    var textEditor by remember { mutableStateOf<TextOverlayEditorModel?>(null) }
    var exportSettings by remember(
        session.project.width,
        session.project.height,
        session.project.fps,
        exportCapabilities,
    ) {
        mutableStateOf(ExportSettingsState.from(session.project, exportCapabilities))
    }

    if (showExportSettings) {
        ExportSettingsDialog(
            state = exportSettings,
            onStateChange = { exportSettings = it },
            onDismiss = { showExportSettings = false },
            onConfirm = {
                showExportSettings = false
                onExportRequested(exportSettings.toProfile())
            },
        )
    }

    if (showTextManager) {
        TextOverlayManagerDialog(
            project = session.project,
            playheadUs = preview.positionUs,
            onEditRequested = { editor ->
                showTextManager = false
                textEditor = editor
            },
            onDeleteRequested = { overlayId ->
                viewModel.dispatch(EditorAction.DeleteTextOverlay(overlayId))
            },
            onDismiss = { showTextManager = false },
        )
    }

    textEditor?.let { editor ->
        TextOverlayEditorDialog(
            project = session.project,
            model = editor,
            onModelChange = { textEditor = it },
            onSave = { overlay, isNew ->
                viewModel.dispatch(
                    if (isNew) EditorAction.AddTextOverlay(overlay)
                    else EditorAction.UpdateTextOverlay(overlay),
                )
                textEditor = null
                showTextManager = true
            },
            onDismiss = {
                textEditor = null
                showTextManager = true
            },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Column(Modifier.fillMaxSize()) {
                EditorToolbar(
                    session.canUndo, session.canRedo, loopEnabled, exportState,
                    onImportRequested, onImportAudioRequested, { showTextManager = true }, { showExportSettings = true }, onCancelExport, onFinalPreviewRequested, viewModel
                ) {
                    loopEnabled = it
                    viewModel.setLoopRange(0L, if (it && preview.durationUs > 0) preview.durationUs else null)
                }
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Column(Modifier.weight(0.42f).fillMaxHeight().padding(end = 8.dp)) {
                        PreviewPane(
                            controller = previewController,
                            state = preview,
                            onPlayPause = { if (preview.playing) viewModel.pause() else viewModel.play() },
                            onSeek = viewModel::seekToUs,
                            onStepFrames = viewModel::stepFrames,
                            viewportHeight = 210.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SectionTitle("Propriedades")
                        InspectorForState(session, preview.positionUs, viewModel, Modifier.weight(1f))
                    }
                    Surface(
                        modifier = Modifier.weight(0.58f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            SectionTitle("Linha do tempo")
                            SelectedClipActions(session, preview.positionUs, viewModel)
                            TimelineForState(session, preview.positionUs, viewModel, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                EditorToolbar(
                    session.canUndo, session.canRedo, loopEnabled, exportState,
                    onImportRequested, onImportAudioRequested, { showTextManager = true }, { showExportSettings = true }, onCancelExport, onFinalPreviewRequested, viewModel
                ) {
                    loopEnabled = it
                    viewModel.setLoopRange(0L, if (it && preview.durationUs > 0) preview.durationUs else null)
                }
                PreviewPane(
                    controller = previewController,
                    state = preview,
                    onPlayPause = { if (preview.playing) viewModel.pause() else viewModel.play() },
                    onSeek = viewModel::seekToUs,
                    onStepFrames = viewModel::stepFrames,
                    viewportHeight = 180.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 138.dp, max = 166.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        SectionTitle("Linha do tempo")
                        TimelineForState(session, preview.positionUs, viewModel, Modifier.fillMaxWidth())
                    }
                }
                SelectedClipActions(session, preview.positionUs, viewModel)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        SectionTitle("Propriedades", Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        InspectorForState(session, preview.positionUs, viewModel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    loopEnabled: Boolean,
    exportState: ExportUiState,
    onImportRequested: () -> Unit,
    onImportAudioRequested: () -> Unit,
    onTextRequested: () -> Unit,
    onExportRequested: () -> Unit,
    onCancelExport: () -> Unit,
    onFinalPreviewRequested: () -> Unit,
    viewModel: EditorViewModel,
    onLoopChange: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "LocalCut",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onImportRequested, enabled = !exportState.running) { Text("Mídia") }
                FilledTonalButton(onClick = onImportAudioRequested, enabled = !exportState.running) { Text("Áudio") }
                FilledTonalButton(onClick = onTextRequested, enabled = !exportState.running) { Text("Texto") }
                FilledTonalButton(onClick = onExportRequested, enabled = !exportState.running) { Text("Exportar") }
                FilledTonalButton(onClick = onFinalPreviewRequested, enabled = !exportState.running) { Text("Prévia final") }
                if (exportState.running) {
                    Button(onClick = onCancelExport) { Text("Cancelar") }
                }
                OutlinedButton(onClick = { viewModel.dispatch(EditorAction.Undo) }, enabled = canUndo && !exportState.running) { Text("Desfazer") }
                OutlinedButton(onClick = { viewModel.dispatch(EditorAction.Redo) }, enabled = canRedo && !exportState.running) { Text("Refazer") }
                OutlinedButton(onClick = { onLoopChange(!loopEnabled) }) { Text(if (loopEnabled) "Loop ON" else "Loop OFF") }
            }
            if (exportState.running || exportState.message != null) {
                Text(
                    text = buildString {
                        exportState.message?.let { append(it) }
                        exportState.progressPercent?.let { if (isNotEmpty()) append(" • "); append("$it%") }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExportSettingsDialog(
    state: ExportSettingsState,
    onStateChange: (ExportSettingsState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar vídeo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExportChoiceRow(
                    label = "Resolução",
                    options = state.resolutionOptions,
                    selected = state.selectedResolution,
                    optionLabel = { "${it.width}×${it.height}" },
                    onSelect = { onStateChange(state.selectResolution(it)) },
                )
                ExportChoiceRow(
                    label = "FPS",
                    options = state.fpsOptions,
                    selected = state.selectedFps,
                    optionLabel = { "$it fps" },
                    onSelect = { onStateChange(state.selectFps(it)) },
                )
                ExportChoiceRow(
                    label = "Bitrate",
                    options = state.bitrateOptions,
                    selected = state.selectedBitrateBitsPerSecond,
                    optionLabel = { "${it / 1_000_000} Mbps" },
                    onSelect = { onStateChange(state.selectBitrate(it)) },
                )
                ExportChoiceRow(
                    label = "Codec",
                    options = state.codecOptions,
                    selected = state.selectedCodec,
                    optionLabel = { if (it == ExportVideoCodec.HEVC) "HEVC" else "H.264" },
                    onSelect = { onStateChange(state.selectCodec(it)) },
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Escolher arquivo") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun <T> ExportChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                if (option == selected) {
                    FilledTonalButton(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
                } else {
                    OutlinedButton(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
                }
            }
        }
    }
}

@Composable
private fun SelectedClipActions(
    session: com.localcut.editor.session.ProjectSessionState,
    playheadUs: Long,
    viewModel: EditorViewModel,
) {
    val clip = session.selectedClipId?.let(session.project::findClip) ?: return
    val canSplit = playheadUs > clip.startUs && playheadUs < clip.startUs + clip.durationUs
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "Arraste as bordas roxas para cortar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FilledTonalButton(
                    onClick = { viewModel.dispatch(EditorAction.SplitSelected(playheadUs)) },
                    enabled = canSplit,
                ) { Text("Dividir") }
                FilledTonalButton(onClick = { viewModel.dispatch(EditorAction.DuplicateSelected) }) { Text("Duplicar") }
                Button(onClick = { viewModel.dispatch(EditorAction.DeleteSelected) }) { Text("Excluir") }
                OutlinedButton(
                    onClick = { viewModel.dispatch(EditorAction.Undo) },
                    enabled = session.canUndo,
                ) { Text("Desfazer") }
                OutlinedButton(
                    onClick = { viewModel.dispatch(EditorAction.Redo) },
                    enabled = session.canRedo,
                ) { Text("Refazer") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier.padding(bottom = 6.dp)) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimelineForState(
    session: com.localcut.editor.session.ProjectSessionState,
    playheadUs: Long,
    viewModel: EditorViewModel,
    modifier: Modifier,
) {
    TimelinePane(
        project = session.project,
        selectedClipId = session.selectedClipId,
        playheadUs = playheadUs,
        onSelectClip = { viewModel.dispatch(EditorAction.SelectClip(it)) },
        onSeek = viewModel::seekToUs,
        onMoveClip = { clipId, newStart ->
            viewModel.dispatch(EditorAction.SelectClip(clipId))
            viewModel.dispatch(EditorAction.MoveSelected(newStart))
        },
        onTrimLeft = { clipId, newStart ->
            viewModel.dispatch(EditorAction.SelectClip(clipId))
            viewModel.dispatch(EditorAction.TrimSelectedLeftEdge(newStart))
        },
        onTrimRight = { clipId, newEnd ->
            viewModel.dispatch(EditorAction.SelectClip(clipId))
            viewModel.dispatch(EditorAction.TrimSelectedRightEdge(newEnd))
        },
        onSetTrackMuted = { trackId, muted ->
            viewModel.dispatch(EditorAction.SetTrackMuted(trackId, muted))
        },
        onSetTrackSolo = { trackId, solo ->
            viewModel.dispatch(EditorAction.SetTrackSolo(trackId, solo))
        },
        modifier = modifier,
    )
}

@Composable
private fun InspectorForState(
    session: com.localcut.editor.session.ProjectSessionState,
    playheadUs: Long,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    InspectorPane(
        project = session.project,
        selectedClipId = session.selectedClipId,
        playheadUs = playheadUs,
        onDelete = { viewModel.dispatch(EditorAction.DeleteSelected) },
        onSplit = { viewModel.dispatch(EditorAction.SplitSelected(it)) },
        onTransform = { viewModel.dispatch(EditorAction.SetSelectedTransform(it)) },
        onVolume = { viewModel.dispatch(EditorAction.SetSelectedVolume(it)) },
        onAudioFades = { fadeInUs, fadeOutUs ->
            viewModel.dispatch(EditorAction.SetSelectedAudioFades(fadeInUs, fadeOutUs))
        },
        modifier = modifier,
    )
    session.lastError?.let { Text("Erro: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
    session.issues.firstOrNull()?.let { Text("Aviso: ${it.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
}
