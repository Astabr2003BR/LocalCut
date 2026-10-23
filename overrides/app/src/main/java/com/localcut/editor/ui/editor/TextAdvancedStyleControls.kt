package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun TextAdvancedStyleControls(
    model: TextOverlayEditorModel,
    onModelChange: (TextOverlayEditorModel) -> Unit,
) {
    val draft = model.draft
    val background = draft.style.background
    val shadow = draft.style.shadows.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Estilo avançado", style = MaterialTheme.typography.labelLarge)

        TextStyleSlider(
            label = "Texto ${(draft.opacity * 100.0).roundToInt()}%",
            value = draft.opacity,
            range = 0.0..1.0,
            onValueChange = { onModelChange(model.withTextOpacity(it)) },
        )

        if (background != null) {
            Text("Fundo", style = MaterialTheme.typography.labelMedium)
            TextColorPresetRow(
                selectedArgb = background.argb,
                onSelected = { onModelChange(model.withBackgroundArgb(it)) },
            )
            TextStyleSlider(
                label = "Opacidade fundo ${(background.opacity * 100.0).roundToInt()}%",
                value = background.opacity,
                range = 0.0..1.0,
                onValueChange = { onModelChange(model.withBackgroundOpacity(it)) },
            )
        }

        if (shadow != null) {
            Text("Sombra", style = MaterialTheme.typography.labelMedium)
            TextColorPresetRow(
                selectedArgb = shadow.argb,
                onSelected = { onModelChange(model.withShadowArgb(it)) },
            )
            TextStyleSlider(
                label = "Raio ${shadow.radiusPx.roundToInt()} px",
                value = shadow.radiusPx,
                range = 0.0..60.0,
                onValueChange = { onModelChange(model.withShadowRadiusPx(it)) },
            )
            TextStyleSlider(
                label = "Sombra X ${shadow.offsetX.roundToInt()} px",
                value = shadow.offsetX,
                range = -80.0..80.0,
                onValueChange = { onModelChange(model.withShadowOffset(it, shadow.offsetY)) },
            )
            TextStyleSlider(
                label = "Sombra Y ${shadow.offsetY.roundToInt()} px",
                value = shadow.offsetY,
                range = -80.0..80.0,
                onValueChange = { onModelChange(model.withShadowOffset(shadow.offsetX, it)) },
            )
            TextStyleSlider(
                label = "Opacidade sombra ${(shadow.opacity * 100.0).roundToInt()}%",
                value = shadow.opacity,
                range = 0.0..1.0,
                onValueChange = { onModelChange(model.withShadowOpacity(it)) },
            )
        }
    }
}

@Composable
private fun TextStyleSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(132.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TextColorPresetRow(
    selectedArgb: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        textAdvancedColorPresets.forEach { preset ->
            if (preset.argb == selectedArgb) {
                FilledTonalButton(onClick = { onSelected(preset.argb) }) { Text(preset.label) }
            } else {
                OutlinedButton(onClick = { onSelected(preset.argb) }) { Text(preset.label) }
            }
        }
    }
}

private data class TextAdvancedColorPreset(val label: String, val argb: Int)

private val textAdvancedColorPresets = listOf(
    TextAdvancedColorPreset("Preto", 0xFF000000.toInt()),
    TextAdvancedColorPreset("Grafite", 0xFF1C1C1E.toInt()),
    TextAdvancedColorPreset("Branco", 0xFFFFFFFF.toInt()),
    TextAdvancedColorPreset("Vermelho", 0xFFFF3B30.toInt()),
    TextAdvancedColorPreset("Amarelo", 0xFFFFCC00.toInt()),
    TextAdvancedColorPreset("Azul", 0xFF0A84FF.toInt()),
)
