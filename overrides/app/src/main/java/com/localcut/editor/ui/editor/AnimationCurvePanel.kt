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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation

data class StabilizationUiState(
    val clipId: String? = null,
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@Composable
fun AnimationCurvePanel(
    project: Project,
    selectedClipId: String?,
    onSetPropertyAnimations: (List<PropertyAnimation>) -> Unit,
    stabilizationState: StabilizationUiState = StabilizationUiState(),
    onStabilizeRequested: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (clip == null) {
            Text(
                "Selecione um clipe para editar curvas.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Surface
        }

        val asset = project.assets.firstOrNull { it.id == clip.assetId }
        val animations = clip.propertyAnimations
        var x1 by remember(clip.id) { mutableFloatStateOf(0.42f) }
        var y1 by remember(clip.id) { mutableFloatStateOf(0f) }
        var x2 by remember(clip.id) { mutableFloatStateOf(0.58f) }
        var y2 by remember(clip.id) { mutableFloatStateOf(1f) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (asset?.type == AssetType.VIDEO) {
                val runningThisClip = stabilizationState.running && stabilizationState.clipId == clip.id
                Text("Estabilização", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Analisa o movimento no UserLAnd/Termux com OpenCV e gera keyframes de correção.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = { onStabilizeRequested(clip.id) },
                    enabled = !stabilizationState.running,
                ) {
                    Text(if (runningThisClip) "Analisando…" else "Estabilizar vídeo")
                }
                if (stabilizationState.clipId == clip.id && stabilizationState.message != null) {
                    Text(
                        stabilizationState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stabilizationState.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (animations.isEmpty()) {
                Text(
                    "Este clipe ainda não possui animações de propriedade. Aplique uma animação/preset ou estabilização primeiro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text("Curvas / easing", style = MaterialTheme.typography.titleSmall)
            Text(
                "${animations.size} propriedade(s) animada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    EasingKind.HOLD to "Hold",
                    EasingKind.LINEAR to "Linear",
                    EasingKind.EASE_IN to "Ease In",
                    EasingKind.EASE_OUT to "Ease Out",
                    EasingKind.EASE_IN_OUT to "Suave",
                    EasingKind.BOUNCE to "Bounce",
                    EasingKind.ELASTIC to "Elastic",
                    EasingKind.CYCLIC to "Cíclico",
                    EasingKind.RANDOM_SEEDED to "Random",
                ).forEach { (kind, label) ->
                    OutlinedButton(
                        onClick = { onSetPropertyAnimations(AnimationCurveUiPolicy.applyKind(animations, kind)) },
                    ) { Text(label) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(2, 4, 8, 12).forEach { steps ->
                    OutlinedButton(
                        onClick = { onSetPropertyAnimations(AnimationCurveUiPolicy.applySteps(animations, steps)) },
                    ) { Text("Steps $steps") }
                }
            }

            Text("Bézier personalizado", style = MaterialTheme.typography.labelLarge)
            BezierSlider("X1", x1, 0f..1f) { x1 = it }
            BezierSlider("Y1", y1, -1f..2f) { y1 = it }
            BezierSlider("X2", x2, 0f..1f) { x2 = it }
            BezierSlider("Y2", y2, -1f..2f) { y2 = it }
            FilledTonalButton(
                onClick = {
                    onSetPropertyAnimations(
                        AnimationCurveUiPolicy.applyCubicBezier(
                            animations,
                            CubicBezierCurve(
                                x1 = x1.toDouble(),
                                y1 = y1.toDouble(),
                                x2 = x2.toDouble(),
                                y2 = y2.toDouble(),
                            ),
                        ),
                    )
                },
            ) { Text("Aplicar Bézier") }
        }
    }
}

@Composable
private fun BezierSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$label ${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
