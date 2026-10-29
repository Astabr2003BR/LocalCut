package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.CompositingSettings
import kotlin.math.roundToInt

@Composable
fun CropManualControls(
    settings: CompositingSettings,
    onSetCompositing: (CompositingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var left by remember(settings.crop.left) { mutableFloatStateOf(settings.crop.left.toFloat()) }
    var top by remember(settings.crop.top) { mutableFloatStateOf(settings.crop.top.toFloat()) }
    var right by remember(settings.crop.right) { mutableFloatStateOf(settings.crop.right.toFloat()) }
    var bottom by remember(settings.crop.bottom) { mutableFloatStateOf(settings.crop.bottom.toFloat()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Recorte manual", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        CropEdgeSlider("Esquerda", left) { value, finished ->
            left = value
            if (finished) onSetCompositing(CropToolsUiPolicy.setLeft(settings, value.toDouble()))
        }
        CropEdgeSlider("Topo", top) { value, finished ->
            top = value
            if (finished) onSetCompositing(CropToolsUiPolicy.setTop(settings, value.toDouble()))
        }
        CropEdgeSlider("Direita", right) { value, finished ->
            right = value
            if (finished) onSetCompositing(CropToolsUiPolicy.setRight(settings, value.toDouble()))
        }
        CropEdgeSlider("Baixo", bottom) { value, finished ->
            bottom = value
            if (finished) onSetCompositing(CropToolsUiPolicy.setBottom(settings, value.toDouble()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { onSetCompositing(CropToolsUiPolicy.resetBounds(settings)) },
                enabled = settings.crop.left != 0.0 || settings.crop.top != 0.0 ||
                    settings.crop.right != 1.0 || settings.crop.bottom != 1.0,
            ) { Text("Resetar recorte") }
        }
    }
}

@Composable
private fun CropEdgeSlider(
    label: String,
    value: Float,
    onValue: (Float, Boolean) -> Unit,
) {
    Text("$label ${(value * 100f).roundToInt()}%", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = { onValue(it, false) },
        onValueChangeFinished = { onValue(value, true) },
        valueRange = 0f..1f,
    )
}
