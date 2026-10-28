package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.history.ApplyReversedAssetCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.time.ReverseApplyGuard
import com.localcut.editor.session.EditorAction
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import kotlinx.coroutines.launch

@Composable
fun TermuxMediaAnalyzePanel(
    engineState: TermuxEngineUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val editorRuntime = remember(context) { LocalCutProcessRuntime.get(context) }
    val termuxRuntime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val editorSession by editorRuntime.editorViewModel.sessionState.collectAsState()
    val selectedClip = editorSession.selectedClipId?.let(editorSession.project::findClip)
    val selectedAsset = selectedClip?.let { clip ->
        editorSession.project.assets.firstOrNull { it.id == clip.assetId }
    }
    val availability = TermuxMediaAnalyzeUiPolicy.availability(engineState, selectedAsset != null)
    val reverseCompatible = selectedClip != null &&
        selectedAsset?.type == AssetType.VIDEO &&
        ReverseApplyGuard.capture(editorSession.project, selectedClip.id).isSuccess
    val reverseAvailable = engineState.phase == TermuxEngineUiPhase.CONNECTED &&
        "REVERSE_MEDIA" in engineState.capabilities &&
        reverseCompatible

    var running by rememberSaveable(selectedAsset?.id) { mutableStateOf(false) }
    var resultText by rememberSaveable(selectedAsset?.id) { mutableStateOf<String?>(null) }
    var reverseRunning by rememberSaveable(selectedClip?.id) { mutableStateOf(false) }
    var reverseText by rememberSaveable(selectedClip?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            availability.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (availability.enabled) Color(0xFF20D5D2) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val asset = selectedAsset ?: return@Button
                running = true
                resultText = "Analisando mídia…"
                scope.launch {
                    val result = termuxRuntime.mediaAnalyzeCoordinator.analyze(asset)
                    resultText = result.fold(
                        onSuccess = TermuxMediaAnalyzeUiPolicy::resultMessage,
                        onFailure = { error ->
                            "Falha na análise • ${error.message ?: "TERMUX_ENGINE_UNAVAILABLE"}"
                        },
                    )
                    running = false
                }
            },
            enabled = availability.enabled && !running && !reverseRunning,
        ) {
            Text(if (running) "Analisando…" else "Analisar mídia selecionada")
        }
        resultText?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Button(
            onClick = {
                val clip = selectedClip ?: return@Button
                val asset = selectedAsset ?: return@Button
                val snapshot = ReverseApplyGuard.capture(editorSession.project, clip.id).getOrElse { error ->
                    reverseText = "Reverse indisponível • ${error.message ?: "REVERSE_GUARD_FAILED"}"
                    return@Button
                }
                reverseRunning = true
                reverseText = "Invertendo vídeo…"
                scope.launch {
                    reverseText = termuxRuntime.reverseMedia(asset).fold(
                        onSuccess = { prepared ->
                            val currentProject = editorRuntime.editorViewModel.sessionState.value.project
                            if (!ReverseApplyGuard.isStillCurrent(currentProject, snapshot)) {
                                prepared.backingFile.delete()
                                "Reverse descartado • o recorte ou a mídia mudou durante o processamento."
                            } else {
                                editorRuntime.editorViewModel.dispatch(
                                    EditorAction.Execute(
                                        ApplyReversedAssetCommand(
                                            clipId = snapshot.clipId,
                                            reversedAsset = prepared.asset,
                                        ),
                                    ),
                                )
                                val appliedProject = editorRuntime.editorViewModel.sessionState.value.project
                                if (appliedProject.findClip(snapshot.clipId)?.assetId == prepared.asset.id) {
                                    "Reverse aplicado ao clipe selecionado."
                                } else {
                                    prepared.backingFile.delete()
                                    "Falha no Reverse • a alteração foi rejeitada pela timeline."
                                }
                            }
                        },
                        onFailure = { error ->
                            "Falha no Reverse • ${error.message ?: "TERMUX_REVERSE_FAILED"}"
                        },
                    )
                    reverseRunning = false
                }
            },
            enabled = reverseAvailable && !running && !reverseRunning,
        ) {
            Text(if (reverseRunning) "Invertendo…" else "Reverse do vídeo selecionado")
        }
        if (!reverseAvailable && selectedAsset?.type == AssetType.VIDEO) {
            Text(
                when {
                    engineState.phase != TermuxEngineUiPhase.CONNECTED -> "Reverse requer o Agent Engine conectado."
                    "REVERSE_MEDIA" !in engineState.capabilities -> "Este Agent Engine ainda não anuncia suporte a Reverse."
                    else -> "Este clipe possui um mapa de tempo incompatível com Reverse nesta versão."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reverseText?.let { text ->
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
