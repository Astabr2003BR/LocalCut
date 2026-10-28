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
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import kotlin.math.roundToInt

@Composable
fun FocusedEffectsPanel(
    project: Project,
    selectedClipId: String?,
    onSetEffects: (List<EffectInstance>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }

    FocusedToolSurface(modifier) {
        if (clip == null || asset?.type == AssetType.AUDIO) {
            Text("Selecione um vídeo ou imagem para usar efeitos.")
            return@FocusedToolSurface
        }

        val categories = EffectBrowserUiPolicy.categories().filterNot { it == EffectBrowserCategory.COLOR }
        var category by remember(clip.id) { mutableStateOf(categories.first()) }
        var selectedTypeId by remember(clip.id) { mutableStateOf<String?>(null) }
        val presets = EffectBrowserUiPolicy.presets(category)
        val selectedPreset = presets.firstOrNull { it.typeId == selectedTypeId } ?: presets.firstOrNull()

        Text("Categoria", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            categories.forEach { candidate ->
                val click = {
                    category = candidate
                    selectedTypeId = EffectBrowserUiPolicy.presets(candidate).firstOrNull()?.typeId
                }
                if (candidate == category) {
                    FilledTonalButton(onClick = click) { Text(EffectBrowserUiPolicy.label(candidate)) }
                } else {
                    OutlinedButton(onClick = click) { Text(EffectBrowserUiPolicy.label(candidate)) }
                }
            }
        }

        Text("Efeitos", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                val active = clip.effects.any { it.typeId == preset.typeId }
                val click = {
                    selectedTypeId = preset.typeId
                    onSetEffects(
                        CreativeToolsUiPolicy.setNumericEffect(
                            clip.effects,
                            preset.typeId,
                            preset.parameterName,
                            preset.value,
                        ),
                    )
                }
                if (active) {
                    FilledTonalButton(onClick = click) { Text(preset.label) }
                } else {
                    OutlinedButton(onClick = click) { Text(preset.label) }
                }
            }
            OutlinedButton(
                onClick = { onSetEffects(EffectBrowserUiPolicy.clearCategory(clip.effects, category)) },
                enabled = EffectBrowserUiPolicy.hasEffectsInCategory(clip.effects, category),
            ) { Text("Limpar") }
        }

        selectedPreset?.let { preset ->
            EffectBrowserUiPolicy.adjustableParameters(clip.effects, preset).forEach { control ->
                var value by remember(clip.id, preset.typeId, control.name, control.value) {
                    mutableFloatStateOf(control.value.toFloat())
                }
                Text(
                    "${control.label}: ${formatToolValue(value)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = value.coerceIn(control.range.start.toFloat(), control.range.endInclusive.toFloat()),
                    onValueChange = { value = it },
                    onValueChangeFinished = {
                        onSetEffects(
                            EffectBrowserUiPolicy.setParameter(
                                clip.effects,
                                preset,
                                control.name,
                                value.toDouble(),
                            ),
                        )
                    },
                    valueRange = control.range.start.toFloat()..control.range.endInclusive.toFloat(),
                )
            }
        }
    }
}

@Composable
fun FocusedSpeedPanel(
    project: Project,
    selectedClipId: String?,
    onSetTimeMap: (TimeMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }

    FocusedToolSurface(modifier) {
        if (clip == null || asset?.type == AssetType.IMAGE) {
            Text("Selecione um vídeo ou áudio para ajustar a velocidade.")
            return@FocusedToolSurface
        }

        val sourceRangeUs = clip.sourceOutUs - clip.sourceInUs
        var customExpanded by remember(clip.id) {
            mutableStateOf(!clip.timeMap.reverse && clip.timeMap.freezeSegments.isEmpty() && clip.timeMap.speedPoints.size == 5)
        }
        var customSpeeds by remember(clip.id, clip.timeMap) {
            mutableStateOf(CreativeToolsUiPolicy.customSpeedControlValues(clip.timeMap))
        }

        Text("Velocidade", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(0.25 to "0,25×", 0.5 to "0,5×", 1.0 to "1×", 2.0 to "2×", 4.0 to "4×").forEach { (speed, label) ->
                val active = !clip.timeMap.reverse &&
                    clip.timeMap.speedPoints.isEmpty() &&
                    clip.timeMap.freezeSegments.isEmpty() &&
                    kotlin.math.abs(clip.timeMap.baseSpeed - speed) < 1e-9
                val click = { onSetTimeMap(CreativeToolsUiPolicy.constantSpeed(clip.timeMap, speed)) }
                if (active) FilledTonalButton(onClick = click) { Text(label) }
                else OutlinedButton(onClick = click) { Text(label) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (sourceRangeUs > 0L) onSetTimeMap(CreativeToolsUiPolicy.accelerationRamp(sourceRangeUs))
                },
                enabled = sourceRangeUs > 0L,
            ) { Text("Rampa 0,5→2×") }
            if (customExpanded) {
                FilledTonalButton(onClick = { customExpanded = false }) { Text("Curva ✓") }
            } else {
                OutlinedButton(onClick = { customExpanded = true }) { Text("Curva") }
            }
        }

        if (customExpanded && sourceRangeUs > 0L) {
            Text("Curva personalizada", style = MaterialTheme.typography.labelLarge)
            customSpeeds.forEachIndexed { index, speed ->
                var local by remember(clip.id, index, speed) { mutableFloatStateOf(speed.toFloat()) }
                Text("P${index + 1}: ${"%.1f".format(local)}×", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = local,
                    onValueChange = { value ->
                        local = value
                        customSpeeds = CreativeToolsUiPolicy.updateCustomSpeedControl(
                            customSpeeds,
                            index,
                            value.toDouble(),
                        )
                    },
                    onValueChangeFinished = {
                        onSetTimeMap(CreativeToolsUiPolicy.customSpeedCurve(sourceRangeUs, customSpeeds))
                    },
                    valueRange = 0.1f..10f,
                    steps = 98,
                )
            }
        }

        if (clip.timeMap.reverse) {
            Text(
                "Este clipe usa Reverse; remova o Reverse antes de editar velocidade ou Freeze por este painel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (clip.timeMap.freezeSegments.isNotEmpty()) {
            Text(
                "Freeze ativo. Use o controle abaixo para removê-lo antes de aplicar outra curva de velocidade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FreezeFrameActionControl(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun FocusedMaskPanel(
    project: Project,
    selectedClipId: String?,
    onSetCompositing: (CompositingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }

    FocusedToolSurface(modifier) {
        if (clip == null || asset?.type == AssetType.AUDIO) {
            Text("Selecione um vídeo ou imagem para usar máscara.")
            return@FocusedToolSurface
        }

        val mask = clip.compositing.masks.firstOrNull { it.id == CreativeToolsUiPolicy.UI_MASK_ID }
        var feather by remember(clip.id, mask?.featherPx) { mutableFloatStateOf((mask?.featherPx ?: 0.0).toFloat()) }
        var expansion by remember(clip.id, mask?.expansionPx) { mutableFloatStateOf((mask?.expansionPx ?: 0.0).toFloat()) }
        var opacity by remember(clip.id, mask?.opacity) { mutableFloatStateOf((mask?.opacity ?: 1.0).toFloat()) }

        Text("Formato", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                MaskKind.RECTANGLE to "Retângulo",
                MaskKind.ELLIPSE to "Elipse",
                MaskKind.FREEFORM to "Livre",
            ).forEach { (kind, label) ->
                val active = mask?.kind == kind
                val click = { onSetCompositing(CreativeToolsUiPolicy.setMaskPreset(clip.compositing, kind)) }
                if (active) FilledTonalButton(onClick = click) { Text(label) }
                else OutlinedButton(onClick = click) { Text(label) }
            }
            OutlinedButton(
                onClick = { onSetCompositing(CreativeToolsUiPolicy.clearMaskPreset(clip.compositing)) },
                enabled = mask != null,
            ) { Text("Remover") }
        }

        if (mask != null) {
            FocusedCommitSlider(
                label = "Suavizar ${feather.roundToInt()} px",
                value = feather,
                range = 0f..200f,
                onValueChange = { feather = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setPrimaryMaskFeather(clip.compositing, feather.toDouble())) }
            FocusedCommitSlider(
                label = "Expandir ${expansion.roundToInt()} px",
                value = expansion,
                range = -200f..200f,
                onValueChange = { expansion = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setPrimaryMaskExpansion(clip.compositing, expansion.toDouble())) }
            FocusedCommitSlider(
                label = "Opacidade ${(opacity * 100f).roundToInt()}%",
                value = opacity,
                range = 0f..1f,
                onValueChange = { opacity = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setPrimaryMaskOpacity(clip.compositing, opacity.toDouble())) }

            if (mask.invert) {
                FilledTonalButton(
                    onClick = { onSetCompositing(CreativeToolsUiPolicy.setPrimaryMaskInverted(clip.compositing, false)) },
                ) { Text("Inverter ✓") }
            } else {
                OutlinedButton(
                    onClick = { onSetCompositing(CreativeToolsUiPolicy.setPrimaryMaskInverted(clip.compositing, true)) },
                ) { Text("Inverter") }
            }
        }
    }
}

@Composable
fun FocusedChromaPanel(
    project: Project,
    selectedClipId: String?,
    onSetCompositing: (CompositingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }

    FocusedToolSurface(modifier) {
        if (clip == null || asset?.type == AssetType.AUDIO) {
            Text("Selecione um vídeo ou imagem para usar chroma key.")
            return@FocusedToolSurface
        }

        val chroma = clip.compositing.chromaKey
        if (chroma.enabled) {
            FilledTonalButton(
                onClick = { onSetCompositing(CreativeToolsUiPolicy.greenScreen(clip.compositing, false)) },
            ) { Text("Chroma verde ✓") }
        } else {
            OutlinedButton(
                onClick = { onSetCompositing(CreativeToolsUiPolicy.greenScreen(clip.compositing, true)) },
            ) { Text("Ativar chroma verde") }
        }

        if (chroma.enabled) {
            var threshold by remember(clip.id, chroma.threshold) { mutableFloatStateOf(chroma.threshold.toFloat()) }
            var softness by remember(clip.id, chroma.softness) { mutableFloatStateOf(chroma.softness.toFloat()) }
            var spill by remember(clip.id, chroma.spillSuppression) { mutableFloatStateOf(chroma.spillSuppression.toFloat()) }

            FocusedCommitSlider(
                label = "Limiar ${(threshold * 100f).roundToInt()}%",
                value = threshold,
                range = 0f..1f,
                onValueChange = { threshold = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setChromaThreshold(clip.compositing, threshold.toDouble())) }
            FocusedCommitSlider(
                label = "Suavidade ${(softness * 100f).roundToInt()}%",
                value = softness,
                range = 0f..1f,
                onValueChange = { softness = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setChromaSoftness(clip.compositing, softness.toDouble())) }
            FocusedCommitSlider(
                label = "Remover verde ${(spill * 100f).roundToInt()}%",
                value = spill,
                range = 0f..1f,
                onValueChange = { spill = it },
            ) { onSetCompositing(CreativeToolsUiPolicy.setChromaSpillSuppression(clip.compositing, spill.toDouble())) }
        }
    }
}

@Composable
private fun FocusedToolSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun FocusedCommitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelSmall)
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onCommit,
        valueRange = range,
    )
}

private fun formatToolValue(value: Float): String =
    if (kotlin.math.abs(value) >= 10f) "%.0f".format(value) else "%.2f".format(value)