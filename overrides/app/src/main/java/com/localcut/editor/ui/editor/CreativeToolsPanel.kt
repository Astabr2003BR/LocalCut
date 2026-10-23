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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap

@Composable
fun CreativeToolsPanel(
    project: Project,
    selectedClipId: String?,
    onSetEffects: (List<EffectInstance>) -> Unit,
    onSetTimeMap: (TimeMap) -> Unit,
    onSetAudioProcessing: (AudioProcessingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip) ?: return
    val asset = project.assets.firstOrNull { it.id == clip.assetId }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Ferramentas profissionais", style = MaterialTheme.typography.labelLarge)

            if (asset?.type != AssetType.AUDIO) {
                Text("Cor e efeitos", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(onClick = {
                        onSetEffects(CreativeToolsUiPolicy.setNumericEffect(clip.effects, "localcut.color.brightness", "amount", 0.2))
                    }) { Text("Brilho +") }
                    FilledTonalButton(onClick = {
                        onSetEffects(CreativeToolsUiPolicy.setNumericEffect(clip.effects, "localcut.color.contrast", "amount", 1.2))
                    }) { Text("Contraste +") }
                    FilledTonalButton(onClick = {
                        onSetEffects(CreativeToolsUiPolicy.setNumericEffect(clip.effects, "localcut.color.saturation", "amount", 1.25))
                    }) { Text("Saturação +") }
                    FilledTonalButton(onClick = {
                        onSetEffects(CreativeToolsUiPolicy.setNumericEffect(clip.effects, "localcut.blur.gaussian", "radius", 8.0))
                    }) { Text("Blur") }
                    OutlinedButton(onClick = { onSetEffects(emptyList()) }, enabled = clip.effects.isNotEmpty()) {
                        Text("Limpar FX")
                    }
                }
            }

            if (asset?.type != AssetType.IMAGE) {
                Text("Velocidade", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0.5 to "0,5×", 1.0 to "1×", 2.0 to "2×").forEach { (speed, label) ->
                        val selected = clip.timeMap.speedPoints.isEmpty() &&
                            clip.timeMap.freezeSegments.isEmpty() &&
                            kotlin.math.abs(clip.timeMap.baseSpeed - speed) < 1e-9
                        if (selected) {
                            FilledTonalButton(onClick = {
                                onSetTimeMap(CreativeToolsUiPolicy.constantSpeed(clip.timeMap, speed))
                            }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = {
                                onSetTimeMap(CreativeToolsUiPolicy.constantSpeed(clip.timeMap, speed))
                            }) { Text(label) }
                        }
                    }
                }

                Text("Áudio profissional", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(onClick = {
                        onSetAudioProcessing(CreativeToolsUiPolicy.voiceAudioPreset(clip.audioProcessing))
                    }) { Text("Voz clara") }
                    FilledTonalButton(onClick = {
                        onSetAudioProcessing(CreativeToolsUiPolicy.loudnessAudioPreset(clip.audioProcessing))
                    }) { Text("Loudness") }
                    OutlinedButton(
                        onClick = { onSetAudioProcessing(AudioProcessingSettings()) },
                        enabled = clip.audioProcessing != AudioProcessingSettings(),
                    ) { Text("Reset áudio") }
                }
            }

            Text(
                "Máscaras, blend e chroma: dados já suportados pelo projeto; render GPU ainda em implementação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
