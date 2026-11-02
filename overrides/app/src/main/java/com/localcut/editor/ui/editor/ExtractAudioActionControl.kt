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
import com.localcut.editor.domain.time.AudioExtractApplyGuard
import com.localcut.editor.session.EditorAction
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import kotlinx.coroutines.launch

@Composable
fun ExtractAudioActionControl(
    modifier: Modifier = Modifier,
    engineState: TermuxEngineUiState? = null,
) {
    val context = LocalContext.current.applicationContext
    val editorRuntime = remember(context) { LocalCutProcessRuntime.get(context) }
    val termuxRuntime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val editorSession by editorRuntime.editorViewModel.sessionState.collectAsState()
    val selectedClip = editorSession.selectedClipId?.let(editorSession.project::findClip)
    val selectedAsset = selectedClip?.let { clip ->
        editorSession.project.assets.firstOrNull { it.id == clip.assetId }
    }
    val extractCompatible = selectedClip != null &&
        selectedAsset?.type == AssetType.VIDEO &&
        AudioExtractApplyGuard.capture(editorSession.project, selectedClip.id).isSuccess
    val knownEngineReady = engineState?.let { state ->
        state.phase == TermuxEngineUiPhase.CONNECTED && "EXTRACT_AUDIO" in state.capabilities
    } ?: true

    var running by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val coordinator = remember(editorRuntime, termuxRuntime) {
        ExtractAudioApplyCoordinator(
            currentProject = { editorRuntime.editorViewModel.sessionState.value.project },
            prepareAudio = { asset ->
                termuxRuntime.extractAudio(asset).map { prepared ->
                    PreparedExtractedAudioAsset(
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
                resultText = "Extraindo áudio…"
                scope.launch {
                    resultText = termuxRuntime.client.getCapabilities().fold(
                        onSuccess = { capabilities ->
                            if ("EXTRACT_AUDIO" !in capabilities.capabilities) {
                                "Extrair áudio indisponível • o Agent Engine não anuncia EXTRACT_AUDIO."
                            } else {
                                coordinator.extract(clipId).fold(
                                    onSuccess = { "Áudio extraído para uma faixa separada." },
                                    onFailure = { error -> extractAudioFailureMessage(error.message) },
                                )
                            }
                        },
                        onFailure = { error ->
                            "Falha ao extrair áudio • ${error.message ?: "TERMUX_ENGINE_UNAVAILABLE"}"
                        },
                    )
                    running = false
                }
            },
            enabled = extractCompatible && knownEngineReady && !running,
        ) {
            Text(if (running) "Extraindo…" else "Extrair áudio")
        }

        resultText?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha") || text.startsWith("Extrair áudio indisponível")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun extractAudioFailureMessage(code: String?): String = when (code) {
    "EXTRACT_AUDIO_RESULT_STALE" -> "Áudio descartado • o recorte ou os ajustes de áudio mudaram durante o processamento."
    "EXTRACT_AUDIO_APPLY_REJECTED" -> "Falha ao extrair áudio • a timeline rejeitou a nova faixa."
    "TERMUX_JOB_WAIT_TIMEOUT" -> "Falha ao extrair áudio • o processamento excedeu o limite de espera."
    else -> "Falha ao extrair áudio • ${code ?: "TERMUX_EXTRACT_AUDIO_FAILED"}"
}
