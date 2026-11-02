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
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.session.EditorAction

@Composable
fun CameraRigActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val preview by runtime.previewController.state.collectAsState()
    val graph = session.project.compositionGraph
    val cameras = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
    val playheadUs = preview.positionUs.coerceAtLeast(0L)
    val activeCameraId = runCatching { CameraCutUiPolicy.activeCameraId(graph, playheadUs) }.getOrNull()
        ?: cameras.singleOrNull()?.id
    val activeCamera = activeCameraId?.let { id -> cameras.firstOrNull { it.id == id } }
    var message by remember { mutableStateOf<String?>(null) }

    fun applyGraph(operation: () -> CompositionGraph) {
        runCatching(operation).fold(
            onSuccess = { updated ->
                runtime.editorViewModel.dispatch(EditorAction.Execute(SetCompositionGraphCommand(updated)))
                message = null
            },
            onFailure = { error -> message = error.message ?: "CAMERA_RIG_EDIT_FAILED" },
        )
    }

    if (activeCamera == null) return

    val parentId = runCatching { CameraRigUiPolicy.parentId(graph, activeCamera.id) }.getOrNull()
    val parentTargets = runCatching { CameraRigUiPolicy.parentTargets(graph, activeCamera.id) }.getOrDefault(emptyList())
    val currentSettings = activeCamera.camera

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Presets da câmera ativa", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CameraPresetUiPolicy.presets().forEach { preset ->
                val active = currentSettings == preset.settings
                if (active) {
                    FilledTonalButton(onClick = {}, enabled = false) { Text(preset.label) }
                } else {
                    OutlinedButton(
                        onClick = {
                            applyGraph { CameraPresetUiPolicy.apply(graph, activeCamera.id, preset.id) }
                        },
                        enabled = !activeCamera.locked,
                    ) { Text(preset.label) }
                }
            }
        }

        Text(
            "Parent da câmera: ${parentId ?: "Sem parent"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (parentId == null) {
                FilledTonalButton(onClick = {}, enabled = false) { Text("Sem parent") }
            } else {
                OutlinedButton(
                    onClick = { applyGraph { CameraRigUiPolicy.setParent(graph, activeCamera.id, null) } },
                    enabled = !activeCamera.locked,
                ) { Text("Desvincular") }
            }
            parentTargets.forEach { target ->
                if (target.id == parentId) {
                    FilledTonalButton(onClick = {}, enabled = false) { Text(target.id) }
                } else {
                    OutlinedButton(
                        onClick = { applyGraph { CameraRigUiPolicy.setParent(graph, activeCamera.id, target.id) } },
                        enabled = !activeCamera.locked,
                    ) { Text("Vincular ${target.id}") }
                }
            }
        }

        if (parentTargets.isEmpty() && parentId == null) {
            Text(
                "Crie um Null ou Grupo em Camadas para usar rig de câmera.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let { text ->
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
