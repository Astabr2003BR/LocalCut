package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.history.SetCompositionGraphCommand
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.session.EditorAction

@Composable
fun CameraCutActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val preview by runtime.previewController.state.collectAsState()
    val project = session.project
    val graph = project.compositionGraph
    val cameras = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
    val playheadUs = preview.positionUs.coerceAtLeast(0L)
    val activeCameraId = runCatching { CameraCutUiPolicy.activeCameraId(graph, playheadUs) }.getOrNull()
    val activeCamera = activeCameraId?.let { id -> cameras.firstOrNull { it.id == id } }
        ?: cameras.singleOrNull()
    val exactCut = runCatching { CameraCutUiPolicy.exactCutAt(graph, playheadUs) }.getOrNull()
    var message by remember { mutableStateOf<String?>(null) }

    fun applyGraph(operation: () -> com.localcut.editor.domain.model.CompositionGraph) {
        runCatching(operation).fold(
            onSuccess = { updated ->
                runtime.editorViewModel.dispatch(EditorAction.Execute(SetCompositionGraphCommand(updated)))
                message = null
            },
            onFailure = { error ->
                message = error.message ?: "CAMERA_EDIT_FAILED"
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (cameras.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        applyGraph { CameraCutUiPolicy.addCamera(graph).graph }
                    },
                ) { Text("+ Câmera") }
            } else {
                Text(
                    if (cameras.size == 1 && graph.cameraCuts.isEmpty()) {
                        "Câmera estática: ${cameras.single().id}"
                    } else {
                        "Ativa: ${activeCameraId ?: "sem câmera"} • ${formatCameraTime(playheadUs)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                OutlinedButton(
                    onClick = {
                        applyGraph { CameraCutUiPolicy.addCamera(graph).graph }
                    },
                ) { Text("+ Câmera") }
                if (activeCamera != null) {
                    OutlinedButton(
                        onClick = {
                            applyGraph { CameraCutUiPolicy.duplicateActiveCameraAt(graph, playheadUs).graph }
                        },
                    ) { Text("Duplicar ativa") }
                }
            }
        }

        if (cameras.size >= 2) {
            Text("Cortar para câmera no playhead", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cameras.forEach { camera ->
                    val active = camera.id == activeCameraId
                    if (active && exactCut?.cameraNodeId == camera.id) {
                        FilledTonalButton(onClick = {}, enabled = false) { Text(camera.id) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                applyGraph {
                                    CameraCutUiPolicy.upsertCut(
                                        graph = graph,
                                        startUs = playheadUs,
                                        cameraNodeId = camera.id,
                                    )
                                }
                            },
                        ) {
                            Text(if (active) "${camera.id} ✓" else camera.id)
                        }
                    }
                }
            }
        }

        if (graph.cameraCuts.isNotEmpty()) {
            Text("Camera cuts", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                graph.cameraCuts.forEach { cut ->
                    FilledTonalButton(
                        onClick = { runtime.previewController.seekToUs(cut.startUs) },
                    ) {
                        Text("${formatCameraTime(cut.startUs)} → ${cut.cameraNodeId}")
                    }
                    if (cut.startUs > 0L) {
                        OutlinedButton(
                            onClick = {
                                applyGraph { CameraCutUiPolicy.removeCut(graph, cut.startUs) }
                            },
                        ) { Text("Remover") }
                    }
                }
            }
        }

        if (activeCamera != null) {
            Text("Ajuste da câmera ativa", style = MaterialTheme.typography.labelLarge)
            CompositionControllerControls(
                project = project,
                node = activeCamera,
                onSetTransform = { transform ->
                    applyGraph {
                        LayerPanelUiPolicy.setControllerTransform(graph, activeCamera.id, transform)
                    }
                },
                onSetCameraSettings = { settings ->
                    applyGraph {
                        LayerPanelUiPolicy.setCameraSettings(graph, activeCamera.id, settings)
                    }
                },
                onRemove = {
                    applyGraph {
                        LayerPanelUiPolicy.removeControllerNode(graph, activeCamera.id)
                    }
                },
                canRemove = graph.cameraCuts.none { it.cameraNodeId == activeCamera.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatCameraTime(timeUs: Long): String = "%.2fs".format(timeUs / 1_000_000.0)
