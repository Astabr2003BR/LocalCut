package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.localcut.editor.domain.model.EffectInstance

@Composable
fun EffectAdvancedControls(
    effects: List<EffectInstance>,
    preset: EffectBrowserPreset,
    onSetEffects: (List<EffectInstance>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = EffectBrowserUiPolicy.adjustableParameters(effects, preset)
    if (controls.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(2f)),
    ) {
        Text("Ajustar ${preset.label}", style = MaterialTheme.typography.labelMedium)
        controls.forEach { control ->
            var value by remember(
                preset.typeId,
                control.name,
                control.value,
            ) {
                mutableFloatStateOf(control.value.toFloat())
            }
            val min = control.range.start.toFloat()
            val max = control.range.endInclusive.toFloat()
            Text(
                "${control.label} ${"%.2f".format(value)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { value = it },
                onValueChangeFinished = {
                    onSetEffects(
                        EffectBrowserUiPolicy.setParameter(
                            effects = effects,
                            preset = preset,
                            parameterName = control.name,
                            value = value.toDouble(),
                        ),
                    )
                },
                valueRange = min..max,
            )
        }
    }
}
