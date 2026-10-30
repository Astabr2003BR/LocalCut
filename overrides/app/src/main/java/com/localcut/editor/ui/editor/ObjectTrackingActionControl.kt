package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.mobile.AndroidObjectTrackingVideoMetadataReader
import com.localcut.editor.session.EditorAction
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxTrackingQuality
import kotlinx.coroutines.launch

@Composable
fun ObjectTrackingActionControl(
    viewModel: EditorViewModel,
    selectedClipId: String?,
    selection: PreviewSelectionRect?,
    viewportSize: IntSize,
    playheadUs: Long,
    onApplied: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val termuxRuntime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val geometryResolver = remember(context) {
        ObjectTrackingSourceGeometryResolver(AndroidObjectTrackingVideoMetadataReader(context))
    }
    val actionCoordinator = remember(viewModel, termuxRuntime) {
        ObjectTrackingActionCoordinator.fromTermux(
            coordinator = termuxRuntime.mediaObjectTrackingCoordinator,
            currentClipProvider = { clipId -> viewModel.sessionState.value.project.findClip(clipId) },
        )
    }
    val workflow = remember(viewModel, geometryResolver, actionCoordinator) {
        ObjectTrackingWorkflowCoordinator.fromResolver(
            resolver = geometryResolver,
            actionCoordinator = actionCoordinator,
            currentProjectProvider = { viewModel.sessionState.value.project },
        )
    }
    val scope = rememberCoroutineScope()

    val project = viewModel.sessionState.value.project
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }
    val videoSelected = clip != null && asset?.type == AssetType.VIDEO
    val playheadInsideClip = clip != null &&
        playheadUs >= clip.startUs &&
        playheadUs < clip.startUs + clip.durationUs
    val hasViewport = viewportSize.width > 0 && viewportSize.height > 0

    var quality by remember(selectedClipId) { mutableStateOf(TermuxTrackingQuality.NORMAL) }
    var running by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Marque o objeto no preview e execute o rastreamento.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                TermuxTrackingQuality.FAST to "Rápido",
                TermuxTrackingQuality.NORMAL to "Normal",
                TermuxTrackingQuality.PRECISE to "Preciso",
            ).forEach { (candidate, label) ->
                if (quality == candidate) {
                    FilledTonalButton(onClick = { quality = candidate }, enabled = !running) { Text(label) }
                } else {
                    OutlinedButton(onClick = { quality = candidate }, enabled = !running) { Text(label) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    val clipId = selectedClipId ?: return@FilledTonalButton
                    val selectedRoi = selection ?: return@FilledTonalButton
                    running = true
                    statusText = "Rastreando objeto…"
                    scope.launch {
                        statusText = termuxRuntime.client.getCapabilities().fold(
                            onSuccess = { capabilities ->
                                if (OBJECT_TRACKING_CAPABILITY !in capabilities.capabilities) {
                                    "Tracking indisponível • o Agent Engine não anuncia $OBJECT_TRACKING_CAPABILITY."
                                } else {
                                    workflow.buildCommand(
                                        clipId = clipId,
                                        selection = selectedRoi,
                                        viewportWidthPx = viewportSize.width,
                                        viewportHeightPx = viewportSize.height,
                                        playheadUs = playheadUs,
                                        quality = quality,
                                    ).fold(
                                        onSuccess = { command ->
                                            viewModel.dispatch(EditorAction.Execute(command))
                                            val appliedClip = viewModel.sessionState.value.project.findClip(clipId)
                                            if (
                                                appliedClip != null &&
                                                ObjectTrackingUiPolicy.hasGeneratedObjectTracking(appliedClip.propertyAnimations)
                                            ) {
                                                onApplied()
                                                "Tracking aplicado ao clipe."
                                            } else {
                                                "Falha no Tracking • a timeline rejeitou a aplicação."
                                            }
                                        },
                                        onFailure = { error -> objectTrackingFailureMessage(error.message) },
                                    )
                                }
                            },
                            onFailure = { error ->
                                "Falha no Tracking • ${error.message ?: "TERMUX_ENGINE_UNAVAILABLE"}"
                            },
                        )
                        running = false
                    }
                },
                enabled = videoSelected && playheadInsideClip && selection != null && hasViewport && !running,
            ) {
                Text(if (running) "Rastreando…" else "Rastrear objeto")
            }
            OutlinedButton(
                onClick = {
                    onClearSelection()
                    statusText = null
                },
                enabled = selection != null && !running,
            ) { Text("Limpar seleção") }
        }

        when {
            !videoSelected -> Text(
                "Selecione um clipe de vídeo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !playheadInsideClip -> Text(
                "Posicione o cursor dentro do clipe selecionado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selection == null -> Text(
                "Arraste sobre o objeto no preview para criar a área de rastreamento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        statusText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha") || text.startsWith("Tracking indisponível")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private const val OBJECT_TRACKING_CAPABILITY = "ANALYZE_OBJECT_TRACKING"

private fun objectTrackingFailureMessage(code: String?): String = when (code) {
    "OBJECT_TRACKING_RESULT_STALE",
    "OBJECT_TRACKING_VISUAL_STATE_STALE" ->
        "Tracking descartado • o clipe, o canvas, a câmera ou as camadas mudaram durante o processamento."
    "OBJECT_TRACKING_PLAYHEAD_OUTSIDE_CLIP" ->
        "Falha no Tracking • posicione o cursor dentro do clipe selecionado."
    "OBJECT_TRACKING_SELECTION_OUTSIDE_VIEWPORT",
    "OBJECT_TRACKING_SELECTION_OUTSIDE_MEDIA",
    "OBJECT_TRACKING_ROI_TOO_SMALL" ->
        "Falha no Tracking • selecione uma área válida diretamente sobre o vídeo."
    "OBJECT_TRACKING_CONFLICTS_WITH_STABILIZATION" ->
        "Falha no Tracking • remova a estabilização deste clipe antes de rastrear."
    "OBJECT_TRACKING_CONFLICTS_WITH_MOTION_PATH",
    "OBJECT_TRACKING_CONFLICTS_WITH_MANUAL_POSITION" ->
        "Falha no Tracking • remova a animação manual de posição antes de rastrear."
    "OBJECT_TRACKING_LEGACY_KEYFRAMES_UNSUPPORTED" ->
        "Falha no Tracking • este clipe possui keyframes legados de transformação incompatíveis nesta versão."
    "OBJECT_TRACKING_REVERSE_UNSUPPORTED",
    "OBJECT_TRACKING_FREEZE_UNSUPPORTED" ->
        "Falha no Tracking • Reverse/Freeze precisam ser removidos antes do rastreamento nesta versão."
    "OBJECT_TRACKING_SOURCE_GEOMETRY_MISMATCH" ->
        "Falha no Tracking • a orientação/tamanho decodificado do vídeo não corresponde à prévia."
    "OPENCV_UNAVAILABLE" ->
        "Tracking indisponível • OpenCV não está disponível no Agent Engine."
    "TERMUX_JOB_WAIT_TIMEOUT" ->
        "Falha no Tracking • o processamento excedeu o limite de espera."
    else -> "Falha no Tracking • ${code ?: "OBJECT_TRACKING_FAILED"}"
}
