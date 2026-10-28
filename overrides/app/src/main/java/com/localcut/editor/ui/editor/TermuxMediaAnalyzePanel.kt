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
import com.localcut.editor.termux.TermuxAgentEngineRuntime
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
    var running by rememberSaveable(selectedAsset?.id) { mutableStateOf(false) }
    var resultText by rememberSaveable(selectedAsset?.id) { mutableStateOf<String?>(null) }
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
            enabled = availability.enabled && !running,
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

        ReverseMediaActionControl(
            engineState = engineState,
            buttonLabel = "Reverse do vídeo selecionado",
            showUnavailableReason = true,
        )
    }
}
