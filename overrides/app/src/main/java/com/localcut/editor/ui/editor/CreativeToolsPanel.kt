package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.recipes.AnimeRecipe
import com.localcut.editor.recipes.AnimeRecipeApplicator
import kotlin.math.roundToInt

@Composable
fun CreativeToolsPanel(
    project: Project,
    selectedClipId: String?,
    onSetEffects: (List<EffectInstance>) -> Unit,
    onSetCompositing: (CompositingSettings) -> Unit,
    onSetTimeMap: (TimeMap) -> Unit,
    onSetAudioProcessing: (AudioProcessingSettings) -> Unit,
    onSetPropertyAnimations: (List<PropertyAnimation>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip) ?: return
    val asset = project.assets.firstOrNull { it.id == clip.assetId }

    if (asset?.type == AssetType.AUDIO) {
        AudioToolsPanel(
            project = project,
            selectedClipId = selectedClipId,
            onSetAudioProcessing = onSetAudioProcessing,
            modifier = modifier,
        )
        return
    }

    val availableSections = CreativeToolsUiPolicy.availableSections(asset?.type)
    var requestedSection by remember(clip.id) {
        mutableStateOf(CreativeToolsUiPolicy.defaultSection(asset?.type))
    }
    val activeSection = requestedSection.takeIf { it in availableSections } ?: availableSections.first()
    var customCurveExpanded by remember(clip.id) {
        mutableStateOf(!clip.timeMap.reverse && clip.timeMap.freezeSegments.isEmpty() && clip.timeMap.speedPoints.size == 5)
    }
    var customSpeeds by remember(clip.id, clip.timeMap) {
        mutableStateOf(CreativeToolsUiPolicy.customSpeedControlValues(clip.timeMap))
    }
    val primaryMask = clip.compositing.masks.firstOrNull { it.id == CreativeToolsUiPolicy.UI_MASK_ID }
    var maskFeather by remember(clip.id, primaryMask?.featherPx) {
        mutableStateOf(primaryMask?.featherPx ?: 0.0)
    }
    var maskExpansion by remember(clip.id, primaryMask?.expansionPx) {
        mutableStateOf(primaryMask?.expansionPx ?: 0.0)
    }
    var maskOpacity by remember(clip.id, primaryMask?.opacity) {
        mutableStateOf(primaryMask?.opacity ?: 1.0)
    }
    var chromaThreshold by remember(clip.id, clip.compositing.chromaKey.threshold) {
        mutableStateOf(clip.compositing.chromaKey.threshold)
    }
    var chromaSoftness by remember(clip.id, clip.compositing.chromaKey.softness) {
        mutableStateOf(clip.compositing.chromaKey.softness)
    }
    var chromaSpill by remember(clip.id, clip.compositing.chromaKey.spillSuppression) {
        mutableStateOf(clip.compositing.chromaKey.spillSuppression)
    }

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

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableSections.forEach { section ->
                    val label = when (section) {
                        CreativeToolsSection.VISUAL -> "Visual"
                        CreativeToolsSection.TIMING -> "Tempo"
                        CreativeToolsSection.AUDIO -> "Áudio"
                    }
                    if (section == activeSection) {
                        FilledTonalButton(onClick = { requestedSection = section }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { requestedSection = section }) { Text(label) }
                    }
                }
            }

            when (activeSection) {
                CreativeToolsSection.VISUAL -> {
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
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            Triple("localcut.sharpen", "amount", 1.0) to "Sharpen",
                            Triple("localcut.vignette", "amount", 0.55) to "Vignette",
                            Triple("localcut.grain", "amount", 0.18) to "Grain",
                            Triple("localcut.rgb_split", "amount", 8.0) to "RGB Split",
                            Triple("localcut.mosaic", "blockSize", 12.0) to "Mosaic",
                            Triple("localcut.glow", "radius", 10.0) to "Glow",
                            Triple("localcut.ripple", "amplitude", 10.0) to "Ripple",
                            Triple("localcut.displacement", "amount", 12.0) to "Displace",
                        ).forEach { (preset, label) ->
                            FilledTonalButton(onClick = {
                                onSetEffects(
                                    CreativeToolsUiPolicy.setNumericEffect(
                                        clip.effects,
                                        preset.first,
                                        preset.second,
                                        preset.third,
                                    ),
                                )
                            }) { Text(label) }
                        }
                        OutlinedButton(onClick = { onSetEffects(emptyList()) }, enabled = clip.effects.isNotEmpty()) {
                            Text("Limpar FX")
                        }
                    }

                    Text("Recorte e composição", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalButton(onClick = {
                            onSetCompositing(CreativeToolsUiPolicy.setMaskPreset(clip.compositing, MaskKind.RECTANGLE))
                        }) { Text("Máscara retângulo") }
                        FilledTonalButton(onClick = {
                            onSetCompositing(CreativeToolsUiPolicy.setMaskPreset(clip.compositing, MaskKind.ELLIPSE))
                        }) { Text("Máscara elipse") }
                        FilledTonalButton(onClick = {
                            onSetCompositing(CreativeToolsUiPolicy.setMaskPreset(clip.compositing, MaskKind.FREEFORM))
                        }) { Text("Máscara livre") }
                        OutlinedButton(
                            onClick = { onSetCompositing(CreativeToolsUiPolicy.clearMaskPreset(clip.compositing)) },
                            enabled = primaryMask != null,
                        ) { Text("Sem máscara") }
                        FilledTonalButton(onClick = {
                            onSetCompositing(CreativeToolsUiPolicy.greenScreen(clip.compositing, !clip.compositing.chromaKey.enabled))
                        }) { Text(if (clip.compositing.chromaKey.enabled) "Chroma ON" else "Chroma verde") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            CropMode.NONE to "Livre",
                            CropMode.FIT to "Fit",
                            CropMode.FILL to "Fill",
                            CropMode.STRETCH to "Stretch",
                        ).forEach { (mode, label) ->
                            val selected = clip.compositing.crop.mode == mode
                            if (selected) {
                                FilledTonalButton(onClick = {
                                    onSetCompositing(CreativeToolsUiPolicy.cropLayout(clip.compositing, mode))
                                }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = {
                                    onSetCompositing(CreativeToolsUiPolicy.cropLayout(clip.compositing, mode))
                                }) { Text(label) }
                            }
                        }
                    }
                    CropManualControls(
                        settings = clip.compositing,
                        onSetCompositing = onSetCompositing,
                    )

                    if (primaryMask != null) {
                        Text("Ajuste da máscara", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Suavizar ${maskFeather.roundToInt()} px",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = maskFeather.toFloat(),
                                onValueChange = { maskFeather = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setPrimaryMaskFeather(clip.compositing, maskFeather),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..200f,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Expandir ${maskExpansion.roundToInt()} px",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = maskExpansion.toFloat(),
                                onValueChange = { maskExpansion = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setPrimaryMaskExpansion(clip.compositing, maskExpansion),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = -200f..200f,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Opacidade ${(maskOpacity * 100.0).roundToInt()}%",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = maskOpacity.toFloat(),
                                onValueChange = { maskOpacity = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setPrimaryMaskOpacity(clip.compositing, maskOpacity),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..1f,
                            )
                        }
                        if (primaryMask.invert) {
                            FilledTonalButton(onClick = {
                                onSetCompositing(
                                    CreativeToolsUiPolicy.setPrimaryMaskInverted(clip.compositing, false),
                                )
                            }) { Text("Inverter máscara ON") }
                        } else {
                            OutlinedButton(onClick = {
                                onSetCompositing(
                                    CreativeToolsUiPolicy.setPrimaryMaskInverted(clip.compositing, true),
                                )
                            }) { Text("Inverter máscara") }
                        }
                    }

                    if (clip.compositing.chromaKey.enabled) {
                        Text("Ajuste do chroma", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Limiar ${(chromaThreshold * 100.0).roundToInt()}%",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = chromaThreshold.toFloat(),
                                onValueChange = { chromaThreshold = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setChromaThreshold(clip.compositing, chromaThreshold),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..1f,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Suavidade ${(chromaSoftness * 100.0).roundToInt()}%",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = chromaSoftness.toFloat(),
                                onValueChange = { chromaSoftness = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setChromaSoftness(clip.compositing, chromaSoftness),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..1f,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Spill ${(chromaSpill * 100.0).roundToInt()}%",
                                modifier = Modifier.width(116.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = chromaSpill.toFloat(),
                                onValueChange = { chromaSpill = it.toDouble() },
                                onValueChangeFinished = {
                                    onSetCompositing(
                                        CreativeToolsUiPolicy.setChromaSpillSuppression(clip.compositing, chromaSpill),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }

                CreativeToolsSection.TIMING -> {
                    Text("Anime Mode", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            AnimeRecipe.IMPACT_ZOOM to "Impacto",
                            AnimeRecipe.CAMERA_SHAKE to "Shake",
                            AnimeRecipe.PARALLAX_PAN to "Parallax",
                        ).forEach { (recipe, label) ->
                            FilledTonalButton(onClick = {
                                onSetPropertyAnimations(
                                    AnimeRecipeApplicator.apply(clip, recipe, seed = clip.id.hashCode().toLong()).propertyAnimations,
                                )
                            }) { Text(label) }
                        }
                        OutlinedButton(
                            onClick = { onSetPropertyAnimations(emptyList()) },
                            enabled = clip.propertyAnimations.isNotEmpty(),
                        ) { Text("Limpar animação") }
                    }

                    if (clip.propertyAnimations.isNotEmpty()) {
                        Text("Curvas / easing", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                EasingKind.LINEAR to "Linear",
                                EasingKind.EASE_IN_OUT to "Suave",
                                EasingKind.BOUNCE to "Bounce",
                                EasingKind.ELASTIC to "Elastic",
                                EasingKind.STEPS to "Steps",
                            ).forEach { (kind, label) ->
                                OutlinedButton(onClick = {
                                    onSetPropertyAnimations(CreativeToolsUiPolicy.setEasingPreset(clip.propertyAnimations, kind))
                                }) { Text(label) }
                            }
                        }
                    }

                    if (asset?.type != AssetType.IMAGE) {
                        val sourceRangeUs = clip.sourceOutUs - clip.sourceInUs
                        Text("Velocidade", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(0.5 to "0,5×", 1.0 to "1×", 2.0 to "2×").forEach { (speed, label) ->
                                val selected = !clip.timeMap.reverse &&
                                    clip.timeMap.speedPoints.isEmpty() &&
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
                            val rampSelected = !clip.timeMap.reverse &&
                                clip.timeMap.freezeSegments.isEmpty() &&
                                clip.timeMap.speedPoints.map { it.speed } == listOf(0.5, 2.0)
                            if (rampSelected) {
                                FilledTonalButton(onClick = {
                                    onSetTimeMap(CreativeToolsUiPolicy.accelerationRamp(sourceRangeUs))
                                }) { Text("Ramp 0,5→2×") }
                            } else {
                                OutlinedButton(onClick = {
                                    onSetTimeMap(CreativeToolsUiPolicy.accelerationRamp(sourceRangeUs))
                                }) { Text("Ramp 0,5→2×") }
                            }
                            val customSelected = !clip.timeMap.reverse &&
                                clip.timeMap.freezeSegments.isEmpty() &&
                                clip.timeMap.speedPoints.size == 5
                            if (customSelected) {
                                FilledTonalButton(onClick = { customCurveExpanded = !customCurveExpanded }) {
                                    Text(if (customCurveExpanded) "Curva ✓" else "Curva custom")
                                }
                            } else {
                                OutlinedButton(onClick = { customCurveExpanded = !customCurveExpanded }) {
                                    Text("Curva custom")
                                }
                            }
                        }

                        if (customCurveExpanded) {
                            Text(
                                "Curva customizada • 0,1×–10×",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            customSpeeds.forEachIndexed { index, speed ->
                                val rounded = (speed * 10.0).roundToInt() / 10.0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "P${index + 1} ${rounded}×",
                                        modifier = Modifier.width(82.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Slider(
                                        value = speed.toFloat(),
                                        onValueChange = { value ->
                                            customSpeeds = CreativeToolsUiPolicy.updateCustomSpeedControl(
                                                customSpeeds,
                                                index,
                                                value.toDouble(),
                                            )
                                        },
                                        onValueChangeFinished = {
                                            onSetTimeMap(
                                                CreativeToolsUiPolicy.customSpeedCurve(
                                                    sourceRangeUs = sourceRangeUs,
                                                    speeds = customSpeeds,
                                                ),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        valueRange = 0.1f..10f,
                                        steps = 98,
                                    )
                                }
                            }
                        }
                    }
                }

                CreativeToolsSection.AUDIO -> {
                    AudioToolsPanel(
                        project = project,
                        selectedClipId = selectedClipId,
                        onSetAudioProcessing = onSetAudioProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(
                "Reverse, freeze, motion blur temporal e blend avançado ainda exigem backends dedicados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
