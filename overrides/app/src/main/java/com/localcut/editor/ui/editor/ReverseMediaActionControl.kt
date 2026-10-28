package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.time.ReverseApplyGuard
import com.localcut.editor.session.EditorAction
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import kotlinx.coroutines.launch

@Composable
fun ReverseMediaActionControl(
    modifier: Modifier = Modifier,
    buttonLabel: String = "Reverse",
    engineState: TermuxEngineUiState? = null,
    showUnavailableReason: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val editorRuntime = remember(context) { LocalCutProcessRuntime.get(context) }
    val termuxRuntime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val editorSession by editorRuntime.editorViewModel.sessionState.collectAsState()
    val selectedClip = editorSession.selectedClipId?.let(editorSession.project::findClip)
    val selectedAsset = selectedClip?.let { clip ->
        editorSession.project.assets.firstOrNull { it.id == clip.assetId }
    }
    val reverseCompatible = selectedClip != null &&
        selectedAsset?.type == AssetType.VIDEO &&
        ReverseApplyGuard.capture(editorSession.project, selectedClip.id).isSuccess
    val knownEngineReady = engineState?.let { state ->
        state.phase == TermuxEngineUiPhase.CONNECTED && "REVERSE_MEDIA" in state.capabilities
    } ?: true

    var running by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val coordinator = remember(editorRuntime, termuxRuntime) {
        ReverseMediaApplyCoordinator(
            currentProject = { editorRuntime.editorViewModel.sessionState.value.project },
            prepareReverse = { asset ->
                termuxRuntime.reverseMedia(asset).map { prepared ->
                    PreparedReverseAsset(
                        asset = prepared.asset,
                        backingFile = prepared.backingFile,
                    )
                }
            },
            execute = { command ->
                editorRuntime.editorViewModel.dispatch(EditorAction.Execute(command))
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                val clipId = selectedClip?.id ?: return@OutlinedButton
                running = true
                resultText = "Invertendo vídeo…"
                scope.launch {
                    resultText = termuxRuntime.client.getCapabilities().fold(
                        onSuccess = { capabilities ->
                            if ("REVERSE_MEDIA" !in capabilities.capabilities) {
                                "Reverse indisponível • o Agent Engine não anuncia REVERSE_MEDIA."
                            } else {
                                coordinator.reverse(clipId).fold(
                                    onSuccess = { "Reverse aplicado ao clipe." },
                                    onFailure = { error -> reverseFailureMessage(error.message) },
                                )
                            }
                        },
                        onFailure = { error ->
                            "Falha no Reverse • ${error.message ?: "TERMUX_ENGINE_UNAVAILABLE"}"
                        },
                    )
                    running = false
                }
            },
            enabled = reverseCompatible && knownEngineReady && !running,
        ) {
            Text(if (running) "Invertendo…" else buttonLabel)
        }

        if (showUnavailableReason && selectedAsset?.type == AssetType.VIDEO && !reverseCompatible) {
            Text(
                "Este clipe possui um mapa de tempo incompatível com Reverse nesta versão.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (showUnavailableReason && engineState != null && !knownEngineReady) {
            Text(
                if (engineState.phase != TermuxEngineUiPhase.CONNECTED) {
                    "Reverse requer o Agent Engine conectado."
                } else {
                    "Este Agent Engine ainda não anuncia suporte a Reverse."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        resultText?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha") || text.startsWith("Reverse indisponível")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun reverseFailureMessage(code: String?): String = when (code) {
    "REVERSE_RESULT_STALE" -> "Reverse descartado • o recorte ou a mídia mudou durante o processamento."
    "REVERSE_APPLY_REJECTED" -> "Falha no Reverse • a timeline rejeitou a substituição."
    "TERMUX_JOB_WAIT_TIMEOUT" -> "Falha no Reverse • o processamento excedeu o limite de espera."
    else -> "Falha no Reverse • ${code ?: "TERMUX_REVERSE_FAILED"}"
}
