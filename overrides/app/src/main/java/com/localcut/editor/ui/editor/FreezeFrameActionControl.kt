package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import com.localcut.editor.domain.history.SetClipTimeMapCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.session.EditorAction

@Composable
fun FreezeFrameActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val editorRuntime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by editorRuntime.editorViewModel.sessionState.collectAsState()
    val preview by editorRuntime.editorViewModel.previewState.collectAsState()
    val clipId = session.selectedClipId ?: return
    val clip = session.project.findClip(clipId) ?: return
    val asset = session.project.assets.firstOrNull { it.id == clip.assetId } ?: return
    if (asset.type != AssetType.VIDEO) return

    val hasFreeze = clip.timeMap.freezeSegments.isNotEmpty()
    val presentationOffsetUs = preview.positionUs - clip.startUs
    val playheadInsideClip = presentationOffsetUs >= 0L && presentationOffsetUs < clip.durationUs
    val canCreateFreeze = !hasFreeze && !clip.timeMap.reverse &&
        clip.timeMap.speedPoints.isEmpty() && playheadInsideClip
    var message by remember(clip.id) { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Congelar frame", style = MaterialTheme.typography.labelSmall)
        if (hasFreeze) {
            OutlinedButton(
                onClick = {
                    val current = editorRuntime.editorViewModel.sessionState.value
                    val currentClip = current.project.findClip(clip.id) ?: return@OutlinedButton
                    val cleared = CreativeToolsUiPolicy.clearFreeze(currentClip.timeMap)
                    val result = runCatching {
                        editorRuntime.editorViewModel.dispatch(
                            EditorAction.Execute(SetClipTimeMapCommand(currentClip.id, cleared)),
                        )
                    }
                    message = if (result.isSuccess) "Freeze removido." else {
                        "Falha ao remover Freeze • ${result.exceptionOrNull()?.message ?: "FREEZE_CLEAR_FAILED"}"
                    }
                },
            ) { Text("Remover Freeze") }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    500_000L to "0,5 s",
                    1_000_000L to "1 s",
                    2_000_000L to "2 s",
                ).forEach { (durationUs, label) ->
                    OutlinedButton(
                        onClick = {
                            val currentSession = editorRuntime.editorViewModel.sessionState.value
                            val currentPreview = editorRuntime.editorViewModel.previewState.value
                            val currentClip = currentSession.project.findClip(clip.id) ?: return@OutlinedButton
                            val localOffsetUs = currentPreview.positionUs - currentClip.startUs
                            val sourceRangeUs = currentClip.sourceOutUs - currentClip.sourceInUs
                            val result = runCatching {
                                val frozen = CreativeToolsUiPolicy.freezeCurrentFrame(
                                    source = currentClip.timeMap,
                                    presentationOffsetUs = localOffsetUs,
                                    sourceRangeUs = sourceRangeUs,
                                    freezeDurationUs = durationUs,
                                )
                                editorRuntime.editorViewModel.dispatch(
                                    EditorAction.Execute(SetClipTimeMapCommand(currentClip.id, frozen)),
                                )
                            }
                            message = if (result.isSuccess) {
                                "Freeze de $label aplicado no frame atual."
                            } else {
                                "Falha no Freeze • ${result.exceptionOrNull()?.message ?: "FREEZE_APPLY_FAILED"}"
                            }
                        },
                        enabled = canCreateFreeze,
                    ) { Text(label) }
                }
            }
        }

        if (!hasFreeze && !canCreateFreeze) {
            Text(
                when {
                    clip.timeMap.reverse -> "Freeze não é combinado com Reverse lógico nesta versão."
                    clip.timeMap.speedPoints.isNotEmpty() -> "Freeze com curva de velocidade ainda é bloqueado nesta versão."
                    !playheadInsideClip -> "Posicione o playhead dentro do clipe para congelar o frame atual."
                    else -> "Freeze indisponível para este clipe."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
