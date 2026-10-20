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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformValue
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
            if (fadeInDraft > maxFadeIn) fadeInDraft = maxFadeIn
            val maxFadeOut = max(0f, durationSeconds - fadeInDraft)
            if (fadeOutDraft > maxFadeOut) fadeOutDraft = maxFadeOut

            Text("Fade in ${"%.2f".format(fadeInDraft)}s", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fadeInDraft,
                onValueChange = { fadeInDraft = min(it, durationSeconds - fadeOutDraft) },
                onValueChangeFinished = {
                    onAudioFades((fadeInDraft * 1_000_000L).toLong(), (fadeOutDraft * 1_000_000L).toLong())
                },
                valueRange = 0f..max(0.001f, maxFadeIn),
            )
            Text("Fade out ${"%.2f".format(fadeOutDraft)}s", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fadeOutDraft,
                onValueChange = { fadeOutDraft = min(it, durationSeconds - fadeInDraft) },
                onValueChangeFinished = {
                    onAudioFades((fadeInDraft * 1_000_000L).toLong(), (fadeOutDraft * 1_000_000L).toLong())
                },
                valueRange = 0f..max(0.001f, maxFadeOut),
            )
        }
    }
}
