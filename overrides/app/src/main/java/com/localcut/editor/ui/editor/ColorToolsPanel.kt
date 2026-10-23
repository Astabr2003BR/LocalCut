package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.Project

@Composable
fun ColorToolsPanel(
    project: Project,
    selectedClipId: String?,
    onSetEffects: (List<EffectInstance>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (clip == null || asset?.type == AssetType.AUDIO) {
            Text(
                "Selecione um clipe de vídeo ou imagem para ajustar a cor.",
                modifier = Modifier.padding(12.dp),
            )
            return@Surface
        }

        var effectCategory by remember(clip.id) {
            mutableStateOf(EffectBrowserCategory.COLOR)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Cor", style = MaterialTheme.typography.titleSmall)
            Text("Filtros LocalCut", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ColorFilterPreset.entries.forEach { preset ->
                    OutlinedButton(
                        onClick = { onSetEffects(ColorFilterUiPolicy.applyPreset(clip.effects, preset)) },
                    ) { Text(preset.label) }
                }
            }

            Text("Efeitos rápidos", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EffectBrowserUiPolicy.categories().forEach { category ->
                    val label = EffectBrowserUiPolicy.label(category)
                    if (category == effectCategory) {
                        FilledTonalButton(onClick = { effectCategory = category }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { effectCategory = category }) { Text(label) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EffectBrowserUiPolicy.presets(effectCategory).forEach { preset ->
                    FilledTonalButton(
                        onClick = {
                            onSetEffects(
                                CreativeToolsUiPolicy.setNumericEffect(
                                    effects = clip.effects,
                                    typeId = preset.typeId,
                                    parameterName = preset.parameterName,
                                    value = preset.value,
                                ),
                            )
                        },
                    ) { Text(preset.label) }
                }
                OutlinedButton(
                    onClick = {
                        onSetEffects(EffectBrowserUiPolicy.clearCategory(clip.effects, effectCategory))
                    },
                    enabled = EffectBrowserUiPolicy.hasEffectsInCategory(clip.effects, effectCategory),
                ) { Text("Limpar categoria") }
            }

            Text("Ajustes", style = MaterialTheme.typography.labelLarge)
            ColorCommitSlider(
                label = "Brilho",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.brightness", "amount", 0.0),
                range = -1f..1f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.brightness", "amount", value))
            }
            ColorCommitSlider(
                label = "Contraste",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.contrast", "amount", 1.0),
                range = 0f..2f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.contrast", "amount", value))
            }
            ColorCommitSlider(
                label = "Exposição",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.exposure", "stops", 0.0),
                range = -5f..5f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.exposure", "stops", value))
            }
            ColorCommitSlider(
                label = "Saturação",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.saturation", "amount", 1.0),
                range = 0f..2f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.saturation", "amount", value))
            }
            ColorCommitSlider(
                label = "Temperatura",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.temperature", "amount", 0.0),
                range = -1f..1f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.temperature", "amount", value))
            }
            ColorCommitSlider(
                label = "Tint",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.tint", "amount", 0.0),
                range = -1f..1f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.tint", "amount", value))
            }

            Text("HSL", style = MaterialTheme.typography.labelLarge)
            ColorCommitSlider(
                label = "Matiz",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.hsl", "hue", 0.0),
                range = -180f..180f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.hsl", "hue", value))
            }
            ColorCommitSlider(
                label = "HSL Saturação",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.hsl", "saturation", 0.0),
                range = -100f..100f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.hsl", "saturation", value))
            }
            ColorCommitSlider(
                label = "Luminosidade",
                initial = ColorToolsUiPolicy.number(clip.effects, "localcut.color.hsl", "lightness", 0.0),
                range = -100f..100f,
            ) { value ->
                onSetEffects(ColorToolsUiPolicy.setNumber(clip.effects, "localcut.color.hsl", "lightness", value))
            }

            FilledTonalButton(
                onClick = { onSetEffects(ColorToolsUiPolicy.resetColorEffects(clip.effects)) },
                enabled = clip.effects.any { it.typeId.startsWith("localcut.color.") },
            ) { Text("Resetar cor") }
        }
    }
}

@Composable
private fun ColorCommitSlider(
    label: String,
    initial: Double,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Double) -> Unit,
) {
    var value by remember(label, initial) { mutableFloatStateOf(initial.toFloat().coerceIn(range.start, range.endInclusive)) }
    Text("$label ${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall)
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onCommit(value.toDouble()) },
        valueRange = range,
    )
}
