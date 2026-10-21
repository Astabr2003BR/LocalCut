package com.localcut.editor.ui.editor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.ui.editor.KeyframeEditorPolicy
import kotlin.math.max
import kotlin.math.min

@Composable
fun InspectorPane(
    project: Project,
    selectedClipId: String?,
    playheadUs: Long,
    onDelete: () -> Unit,
    onSplit: (Long) -> Unit,
    onTransform: (TransformValue) -> Unit,
    onSetKeyframes: (List<TransformKeyframe>) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Double) -> Unit,
    onAudioFades: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { c -> project.assets.firstOrNull { it.id == c.assetId } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (clip == null) {
            Text("Nenhum clipe selecionado", style = MaterialTheme.typography.titleSmall)
            Text(
                "Importe uma mídia ou toque em um clipe na linha do tempo para editar suas propriedades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(asset?.displayName ?: "Clipe", style = MaterialTheme.typography.titleSmall)
        Text(
            "Início ${"%.2f".format(clip.startUs / 1_000_000.0)}s • Duração ${"%.2f".format(clip.durationUs / 1_000_000.0)}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            Button(onClick = onDelete) { Text("Excluir") }
            FilledTonalButton(
                onClick = { onSplit(playheadUs) },
                enabled = playheadUs > clip.startUs && playheadUs < clip.startUs + clip.durationUs,
            ) { Text("Dividir") }
        }

        if (asset?.type != AssetType.AUDIO) {
            Text("Escala ${"%.2f".format(clip.transform.scale)}", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                FilledTonalButton(onClick = { onTransform(clip.transform.copy(scale = (clip.transform.scale - 0.05).coerceAtLeast(0.05))) }) { Text("Zoom −") }
                FilledTonalButton(onClick = { onTransform(clip.transform.copy(scale = clip.transform.scale + 0.05)) }) { Text("Zoom +") }
                FilledTonalButton(onClick = { onTransform(clip.transform.copy(rotationDeg = clip.transform.rotationDeg - 5.0)) }) { Text("↺ 5°") }
                FilledTonalButton(onClick = { onTransform(clip.transform.copy(rotationDeg = clip.transform.rotationDeg + 5.0)) }) { Text("↻ 5°") }
            }

            val localOffsetUs = KeyframeEditorPolicy.localOffsetUs(clip, playheadUs)
            val valueAtCursor = KeyframeEditorPolicy.valueAtProjectTime(clip, playheadUs)
            var keyframeDraft by remember(clip.id, playheadUs, clip.keyframes, clip.transform) {
                mutableStateOf(valueAtCursor ?: clip.transform)
            }
            val exactKeyframe = localOffsetUs != null && clip.keyframes.any { it.offsetUs == localOffsetUs }
            val previousUs = KeyframeEditorPolicy.previousProjectTimeUs(clip, playheadUs)
            val nextUs = KeyframeEditorPolicy.nextProjectTimeUs(clip, playheadUs)
            val insideClip = localOffsetUs != null

            Text(
                "Keyframes ${clip.keyframes.size}${localOffsetUs?.let { " • cursor ${"%.2f".format(it / 1_000_000.0)}s" } ?: " • cursor fora do clipe"}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text("Posição X ${"%.0f".format(keyframeDraft.x)} px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = keyframeDraft.x.toFloat().coerceIn(-project.width.toFloat(), project.width.toFloat()),
                onValueChange = { keyframeDraft = keyframeDraft.copy(x = it.toDouble()) },
                valueRange = -project.width.toFloat()..project.width.toFloat(),
                enabled = insideClip,
            )
            Text("Posição Y ${"%.0f".format(keyframeDraft.y)} px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = keyframeDraft.y.toFloat().coerceIn(-project.height.toFloat(), project.height.toFloat()),
                onValueChange = { keyframeDraft = keyframeDraft.copy(y = it.toDouble()) },
                valueRange = -project.height.toFloat()..project.height.toFloat(),
                enabled = insideClip,
            )
            Text("Escala KF ${"%.2f".format(keyframeDraft.scale)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = keyframeDraft.scale.toFloat().coerceIn(0.05f, 4f),
                onValueChange = { keyframeDraft = keyframeDraft.copy(scale = it.toDouble()) },
                valueRange = 0.05f..4f,
                enabled = insideClip,
            )
            Text("Rotação KF ${"%.0f".format(keyframeDraft.rotationDeg)}°", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = keyframeDraft.rotationDeg.toFloat().coerceIn(-180f, 180f),
                onValueChange = { keyframeDraft = keyframeDraft.copy(rotationDeg = it.toDouble()) },
                valueRange = -180f..180f,
                enabled = insideClip,
            )
            Text("Opacidade KF ${"%.0f".format(keyframeDraft.opacity * 100)}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = keyframeDraft.opacity.toFloat().coerceIn(0f, 1f),
                onValueChange = { keyframeDraft = keyframeDraft.copy(opacity = it.toDouble()) },
                valueRange = 0f..1f,
                enabled = insideClip,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                FilledTonalButton(
                    onClick = {
                        KeyframeEditorPolicy.upsertAtProjectTime(clip, playheadUs, keyframeDraft)?.let(onSetKeyframes)
                    },
                    enabled = insideClip,
                ) { Text(if (exactKeyframe) "Atualizar KF" else "Adicionar KF") }
                OutlinedButton(
                    onClick = { previousUs?.let(onSeek) },
                    enabled = previousUs != null,
                ) { Text("◀ Anterior") }
                OutlinedButton(
                    onClick = { nextUs?.let(onSeek) },
                    enabled = nextUs != null,
                ) { Text("Próximo ▶") }
                Button(
                    onClick = {
                        KeyframeEditorPolicy.deleteAtProjectTime(clip, playheadUs)?.let(onSetKeyframes)
                    },
                    enabled = exactKeyframe,
                ) { Text("Excluir KF") }
            }
        }

        var volumeDraft by remember(clip.id, clip.volume) { mutableFloatStateOf(clip.volume.coerceIn(0.0, 2.0).toFloat()) }
        Text("Volume ${"%.0f".format(volumeDraft * 100)}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = volumeDraft,
            onValueChange = { volumeDraft = it },
            onValueChangeFinished = { onVolume(volumeDraft.toDouble()) },
            valueRange = 0f..2f,
        )

        if (asset?.type != AssetType.IMAGE) {
            val durationSeconds = max(0.001f, clip.durationUs / 1_000_000f)
            var fadeInDraft by remember(clip.id, clip.fadeInUs) {
                mutableFloatStateOf((clip.fadeInUs / 1_000_000f).coerceIn(0f, durationSeconds))
            }
            var fadeOutDraft by remember(clip.id, clip.fadeOutUs) {
                mutableFloatStateOf((clip.fadeOutUs / 1_000_000f).coerceIn(0f, durationSeconds))
            }
            val maxFadeIn = max(0f, durationSeconds - fadeOutDraft)
            val maxFadeOut = max(0f, durationSeconds - fadeInDraft)

            Text("Fade in ${"%.2f".format(fadeInDraft)}s", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fadeInDraft.coerceIn(0f, max(0.001f, maxFadeIn)),
                onValueChange = { fadeInDraft = min(it, durationSeconds - fadeOutDraft) },
                onValueChangeFinished = {
                    onAudioFades((fadeInDraft * 1_000_000f).toLong(), (fadeOutDraft * 1_000_000f).toLong())
                },
                valueRange = 0f..max(0.001f, maxFadeIn),
            )
            Text("Fade out ${"%.2f".format(fadeOutDraft)}s", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fadeOutDraft.coerceIn(0f, max(0.001f, maxFadeOut)),
                onValueChange = { fadeOutDraft = min(it, durationSeconds - fadeInDraft) },
                onValueChangeFinished = {
                    onAudioFades((fadeInDraft * 1_000_000f).toLong(), (fadeOutDraft * 1_000_000f).toLong())
                },
                valueRange = 0f..max(0.001f, maxFadeOut),
            )
        }
    }
}
