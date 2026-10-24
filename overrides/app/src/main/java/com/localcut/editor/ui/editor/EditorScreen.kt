package com.localcut.editor.ui.editor

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.history.AddBeatMarkerCommand
import com.localcut.editor.domain.history.ApplyDetectedBeatMarkersCommand
import com.localcut.editor.domain.history.DeleteBeatMarkerCommand
import com.localcut.editor.domain.history.SetClipOpacityCommand
import com.localcut.editor.domain.history.SetCompositionGraphCommand
import com.localcut.editor.domain.history.SetProjectCanvasSizeCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.media.ExportCapabilities
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.ExportSettingsState
import com.localcut.editor.media.ExportVideoCodec
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSessionState
import com.localcut.editor.ui.editor.components.InspectorPane
import com.localcut.editor.ui.editor.components.PreviewPane
import com.localcut.editor.ui.editor.components.TimelinePane

private val EditorBackground = Color(0xFF08080A)
private val EditorSurface = Color(0xFF101012)
private val EditorElevatedSurface = Color(0xFF17171A)
private val EditorAccent = Color(0xFF20D5D2)

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
    onImportOverlayRequested: () -> Unit,
    onExportRequested: (ExportProfile) -> Unit,
    onCancelExport: () -> Unit,
    onFinalPreviewRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.sessionState.collectAsState()
    val preview by viewModel.previewState.collectAsState()
    val configuration = LocalConfiguration.current
    val selectedClip = session.selectedClipId?.let(session.project::findClip)
    val selectedAssetType = selectedClip?.let { clip ->
        session.project.assets.firstOrNull { it.id == clip.assetId }?.type
    }
    val selectionKind = EditorChromeUiPolicy.selectionForAsset(selectedAssetType)

    var activeTool by remember { mutableStateOf<EditorToolId?>(null) }
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

    LaunchedEffect(selectionKind) {
        activeTool = EditorChromeUiPolicy.resolveActiveTool(activeTool, selectionKind)
    }

    if (showExportSettings) {
        ProfessionalExportSettingsDialog(
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

    fun selectTool(toolId: EditorToolId) {
        when (toolId) {
            EditorToolId.MEDIA -> onImportRequested()
            EditorToolId.TEXT -> showTextManager = true
            EditorToolId.OVERLAY -> onImportOverlayRequested()
            EditorToolId.AUDIO -> {
                if (selectionKind == EditorSelectionKind.AUDIO) {
                    activeTool = if (activeTool == EditorToolId.AUDIO) null else EditorToolId.AUDIO
                } else {
                    onImportAudioRequested()
                }
            }
            else -> activeTool = if (activeTool == toolId) null else toolId
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = EditorBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            ProfessionalEditorToolbar(
                canUndo = session.canUndo,
                canRedo = session.canRedo,
                loopEnabled = loopEnabled,
                exportState = exportState,
                onUndo = { viewModel.dispatch(EditorAction.Undo) },
                onRedo = { viewModel.dispatch(EditorAction.Redo) },
                onLoopChange = { enabled ->
                    loopEnabled = enabled
                    viewModel.setLoopRange(
                        0L,
                        if (enabled && preview.durationUs > 0L) preview.durationUs else null,
                    )
                },
                onExportRequested = { showExportSettings = true },
                onCancelExport = onCancelExport,
                onFinalPreviewRequested = onFinalPreviewRequested,
            )

            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                    ) {
                        PreviewPane(
                            controller = previewController,
                            state = preview,
                            onPlayPause = { if (preview.playing) viewModel.pause() else viewModel.play() },
                            onSeek = viewModel::seekToUs,
                            onStepFrames = viewModel::stepFrames,
                            viewportHeight = 205.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        if (activeTool != null) {
                            ProfessionalContextPanel(
                                activeTool = activeTool,
                                session = session,
                                playheadUs = preview.positionUs,
                                viewModel = viewModel,
                                onClose = { activeTool = null },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        color = EditorSurface,
                    ) {
                        TimelineForProfessionalState(
                            session = session,
                            playheadUs = preview.positionUs,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
                        )
                    }
                }
            } else {
                PreviewPane(
                    controller = previewController,
                    state = preview,
                    onPlayPause = { if (preview.playing) viewModel.pause() else viewModel.play() },
                    onSeek = viewModel::seekToUs,
                    onStepFrames = viewModel::stepFrames,
                    viewportHeight = if (activeTool == null) 292.dp else 238.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                )

                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = EditorSurface,
                ) {
                    TimelineForProfessionalState(
                        session = session,
                        playheadUs = preview.positionUs,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
                    )
                }

                if (activeTool != null) {
                    ProfessionalContextPanel(
                        activeTool = activeTool,
                        session = session,
                        playheadUs = preview.positionUs,
                        viewModel = viewModel,
                        onClose = { activeTool = null },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 258.dp),
                    )
                }
            }

            ProfessionalStatusStrip(session)
            EditorBottomToolRail(
                selection = selectionKind,
                activeTool = activeTool,
                onToolSelected = ::selectTool,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProfessionalEditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    loopEnabled: Boolean,
    exportState: ExportUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onExportRequested: () -> Unit,
    onCancelExport: () -> Unit,
    onFinalPreviewRequested: () -> Unit,
) {
    Surface(
        color = Color(0xFF0B0B0D),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    "LocalCut",
                    modifier = Modifier.padding(start = 3.dp, end = 1.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                TextButton(
                    onClick = onUndo,
                    enabled = canUndo && !exportState.running,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) { Text("↶", maxLines = 1, softWrap = false) }
                TextButton(
                    onClick = onRedo,
                    enabled = canRedo && !exportState.running,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) { Text("↷", maxLines = 1, softWrap = false) }
                TextButton(
                    onClick = { onLoopChange(!loopEnabled) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) { Text(if (loopEnabled) "∞•" else "∞", maxLines = 1, softWrap = false) }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = onFinalPreviewRequested,
                    enabled = !exportState.running,
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                ) { Text("Prévia", maxLines = 1, softWrap = false) }

                if (exportState.running) {
                    Button(
                        onClick = onCancelExport,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                    ) { Text("Cancelar", maxLines = 1, softWrap = false) }
                } else {
                    Button(
                        onClick = onExportRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EditorAccent,
                            contentColor = Color.Black,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(
                            "Exportar",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            if (exportState.running || exportState.message != null) {
                Text(
                    text = buildString {
                        exportState.message?.let(::append)
                        exportState.progressPercent?.let { progress ->
                            if (isNotEmpty()) append(" • ")
                            append("$progress%")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7B7BC),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ProfessionalContextPanel(
    activeTool: EditorToolId?,
    session: ProjectSessionState,
    playheadUs: Long,
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = EditorChromeUiPolicy.panelFor(activeTool) ?: return
    val tool = activeTool ?: return

    Surface(
        modifier = modifier,
        color = EditorElevatedSurface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toolTitle(tool),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Fechar") }
            }

            when (panel) {
                ContextPanelKind.EDIT -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        ProfessionalClipQuickActions(session, playheadUs, viewModel)
                        InspectorPane(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            playheadUs = playheadUs,
                            onDelete = { viewModel.dispatch(EditorAction.DeleteSelected) },
                            onSplit = { viewModel.dispatch(EditorAction.SplitSelected(it)) },
                            onTransform = { viewModel.dispatch(EditorAction.SetSelectedTransform(it)) },
                            onOpacity = { opacity ->
                                session.selectedClipId?.let { clipId ->
                                    viewModel.dispatch(EditorAction.Execute(SetClipOpacityCommand(clipId, opacity)))
                                }
                            },
                            onSetKeyframes = { viewModel.dispatch(EditorAction.SetSelectedKeyframes(it)) },
                            onSeek = viewModel::seekToUs,
                            onVolume = { viewModel.dispatch(EditorAction.SetSelectedVolume(it)) },
                            onAudioFades = { fadeInUs, fadeOutUs ->
                                viewModel.dispatch(EditorAction.SetSelectedAudioFades(fadeInUs, fadeOutUs))
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.AUDIO -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        val selectedClip = session.selectedClipId?.let(session.project::findClip)
                        val selectedType = selectedClip?.let { clip ->
                            session.project.assets.firstOrNull { it.id == clip.assetId }?.type
                        }
                        if (selectedType == AssetType.AUDIO) {
                            BeatMarkerTimelineControls(
                                project = session.project,
                                selectedClipId = session.selectedClipId,
                                playheadUs = playheadUs,
                                onAddMarker = { marker ->
                                    viewModel.dispatch(EditorAction.Execute(AddBeatMarkerCommand(marker)))
                                },
                                onDeleteMarker = { markerId ->
                                    viewModel.dispatch(EditorAction.Execute(DeleteBeatMarkerCommand(markerId)))
                                },
                                onApplyDetectedMarkers = { clipId, markers ->
                                    viewModel.dispatch(
                                        EditorAction.Execute(
                                            ApplyDetectedBeatMarkersCommand(
                                                clipId = clipId,
                                                detectedRelativeMarkers = markers,
                                            ),
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AudioToolsPanel(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            onSetAudioProcessing = { viewModel.dispatch(EditorAction.SetSelectedAudioProcessing(it)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.EFFECTS -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FocusedEffectsPanel(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            onSetEffects = { viewModel.dispatch(EditorAction.SetSelectedEffects(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.COLOR -> {
                    ColorToolsPanel(
                        project = session.project,
                        selectedClipId = session.selectedClipId,
                        onSetEffects = { viewModel.dispatch(EditorAction.SetSelectedEffects(it)) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                ContextPanelKind.SPEED -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FocusedSpeedPanel(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            onSetTimeMap = { viewModel.dispatch(EditorAction.SetSelectedTimeMap(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.MASK -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FocusedMaskPanel(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            onSetCompositing = { viewModel.dispatch(EditorAction.SetSelectedCompositing(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.CHROMA -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FocusedChromaPanel(
                            project = session.project,
                            selectedClipId = session.selectedClipId,
                            onSetCompositing = { viewModel.dispatch(EditorAction.SetSelectedCompositing(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.ANIMATION -> {
                    AnimationCurvePanel(
                        project = session.project,
                        selectedClipId = session.selectedClipId,
                        onSetPropertyAnimations = { viewModel.dispatch(EditorAction.SetSelectedPropertyAnimations(it)) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                ContextPanelKind.LAYERS -> {
                    LayerPanel(
                        project = session.project,
                        selectedClipId = session.selectedClipId,
                        onSelectClip = { viewModel.dispatch(EditorAction.SelectClip(it)) },
                        onSetCompositionGraph = { graph ->
                            viewModel.dispatch(EditorAction.Execute(SetCompositionGraphCommand(graph)))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                ContextPanelKind.DESIGN -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        DesignToolsPanel(
                            project = session.project,
                            playheadUs = playheadUs,
                            onAddElement = { viewModel.dispatch(EditorAction.AddDesignElement(it)) },
                            onUpdateElement = { viewModel.dispatch(EditorAction.UpdateDesignElement(it)) },
                            onDeleteElement = { viewModel.dispatch(EditorAction.DeleteDesignElement(it)) },
                            onSetCanvas = { viewModel.dispatch(EditorAction.SetDesignCanvas(it)) },
                            onSetProjectCanvasSize = { width, height ->
                                viewModel.dispatch(EditorAction.Execute(SetProjectCanvasSizeCommand(width, height)))
                            },
                            onUpsertComponent = { viewModel.dispatch(EditorAction.UpsertReusableComponent(it)) },
                            onDeleteComponent = { viewModel.dispatch(EditorAction.DeleteReusableComponent(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ContextPanelKind.CANVAS -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        CanvasToolsPanel(
                            project = session.project,
                            onSetCanvas = { viewModel.dispatch(EditorAction.SetDesignCanvas(it)) },
                            onSetProjectCanvasSize = { width, height ->
                                viewModel.dispatch(EditorAction.Execute(SetProjectCanvasSizeCommand(width, height)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun toolTitle(tool: EditorToolId): String = when (tool) {
    EditorToolId.EDIT -> "Editar"
    EditorToolId.AUDIO -> "Áudio"
    EditorToolId.EFFECTS -> "Efeitos"
    EditorToolId.COLOR -> "Ajustar"
    EditorToolId.SPEED -> "Velocidade"
    EditorToolId.MASK -> "Máscara"
    EditorToolId.CHROMA -> "Chroma key"
    EditorToolId.ANIMATION -> "Animação"
    EditorToolId.LAYERS -> "Camadas"
    EditorToolId.DESIGN -> "Design"
    EditorToolId.CANVAS -> "Proporção"
    else -> "Ferramenta"
}

@Composable
private fun ProfessionalClipQuickActions(
    session: ProjectSessionState,
    playheadUs: Long,
    viewModel: EditorViewModel,
) {
    val clip = session.selectedClipId?.let(session.project::findClip) ?: return
    val canSplit = playheadUs > clip.startUs && playheadUs < clip.startUs + clip.durationUs
    val transitionCandidate = FadeTransitionUiPolicy.createOrUpdate(session.project, clip.id, 500_000L)
    val existingFade = FadeTransitionUiPolicy.existingFade(session.project, clip.id)
    var showTransitionDialog by remember(clip.id) { mutableStateOf(false) }

    if (showTransitionDialog && transitionCandidate != null) {
        ProfessionalFadeTransitionDialog(
            existing = existingFade,
            onDurationSelected = { durationUs ->
                FadeTransitionUiPolicy.createOrUpdate(session.project, clip.id, durationUs)?.let { transition ->
                    viewModel.dispatch(
                        if (existingFade == null) EditorAction.AddClipTransition(transition)
                        else EditorAction.UpdateClipTransition(transition),
                    )
                }
                showTransitionDialog = false
            },
            onRemove = existingFade?.let { transition ->
                {
                    viewModel.dispatch(EditorAction.DeleteClipTransition(transition.id))
                    showTransitionDialog = false
                }
            },
            onDismiss = { showTransitionDialog = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FilledTonalButton(
            onClick = { viewModel.dispatch(EditorAction.SplitSelected(playheadUs)) },
            enabled = canSplit,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text("✂ Dividir") }
        FilledTonalButton(
            onClick = { viewModel.dispatch(EditorAction.DuplicateSelected) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text("Duplicar") }
        FilledTonalButton(
            onClick = { showTransitionDialog = true },
            enabled = transitionCandidate != null,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text(if (existingFade == null) "Transição" else "Transição ✓") }
        Button(
            onClick = { viewModel.dispatch(EditorAction.DeleteSelected) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text("Excluir") }
    }
}

@Composable
private fun TimelineForProfessionalState(
    session: ProjectSessionState,
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
private fun ProfessionalStatusStrip(session: ProjectSessionState) {
    val message = session.lastError?.let { "Erro: $it" }
        ?: session.issues.firstOrNull()?.let { "Aviso: ${it.message}" }
        ?: return
    Surface(color = Color(0xFF2A1113)) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfessionalFadeTransitionDialog(
    existing: ClipTransition?,
    onDurationSelected: (Long) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transição") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fade para preto")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(250_000L to "0,25 s", 500_000L to "0,5 s", 1_000_000L to "1 s").forEach { (durationUs, label) ->
                        val selected = existing?.durationUs == durationUs
                        if (selected) {
                            FilledTonalButton(onClick = { onDurationSelected(durationUs) }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { onDurationSelected(durationUs) }) { Text(label) }
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("Fechar") } },
        dismissButton = {
            onRemove?.let { remove ->
                Button(onClick = remove) { Text("Remover") }
            }
        },
    )
}

@Composable
private fun ProfessionalExportSettingsDialog(
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
                ProfessionalExportChoiceRow(
                    label = "Resolução",
                    options = state.resolutionOptions,
                    selected = state.selectedResolution,
                    optionLabel = { "${it.width}×${it.height}" },
                    onSelect = { onStateChange(state.selectResolution(it)) },
                )
                ProfessionalExportChoiceRow(
                    label = "FPS",
                    options = state.fpsOptions,
                    selected = state.selectedFps,
                    optionLabel = { "$it fps" },
                    onSelect = { onStateChange(state.selectFps(it)) },
                )
                ProfessionalExportChoiceRow(
                    label = "Bitrate",
                    options = state.bitrateOptions,
                    selected = state.selectedBitrateBitsPerSecond,
                    optionLabel = { "${it / 1_000_000} Mbps" },
                    onSelect = { onStateChange(state.selectBitrate(it)) },
                )
                ProfessionalExportChoiceRow(
                    label = "Codec",
                    options = state.codecOptions,
                    selected = state.selectedCodec,
                    optionLabel = { if (it == ExportVideoCodec.HEVC) "HEVC" else "H.264" },
                    onSelect = { onStateChange(state.selectCodec(it)) },
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Escolher arquivo") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun <T> ProfessionalExportChoiceRow(
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
