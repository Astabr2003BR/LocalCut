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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.analysis.StabilizationApplyPolicy
import com.localcut.editor.analysis.TermuxGlobalMotionAdapter
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxTrackingQuality
import com.localcut.editor.termux.TermuxTrackingQualityPolicy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class StabilizationUiState(
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@Composable
fun AnimationCurvePanel(
    project: Project,
    selectedClipId: String?,
    onSetPropertyAnimations: (List<PropertyAnimation>) -> Unit,
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
        val appContext = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val latestProject by rememberUpdatedState(project)
        val latestSelectedClipId by rememberUpdatedState(selectedClipId)
        val latestOnSetPropertyAnimations by rememberUpdatedState(onSetPropertyAnimations)
        var stabilization by remember(clip.id) { mutableStateOf(StabilizationUiState()) }
        var stabilizationStrength by remember(clip.id) { mutableFloatStateOf(1f) }
        var trackingQuality by remember(clip.id) { mutableStateOf(TermuxTrackingQuality.NORMAL) }
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
                Text("Estabilização", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Analisa o movimento no UserLAnd/Termux com OpenCV e gera keyframes de correção.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Qualidade da análise", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        TermuxTrackingQuality.FAST to "Rápido",
                        TermuxTrackingQuality.NORMAL to "Normal",
                        TermuxTrackingQuality.PRECISE to "Preciso",
                    ).forEach { (quality, label) ->
                        if (trackingQuality == quality) {
                            FilledTonalButton(
                                onClick = { trackingQuality = quality },
                                enabled = !stabilization.running,
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { trackingQuality = quality },
                                enabled = !stabilization.running,
                            ) { Text(label) }
                        }
                    }
                }
                Text(
                    when (trackingQuality) {
                        TermuxTrackingQuality.FAST -> "Rápido: menor uso de CPU e menos amostras."
                        TermuxTrackingQuality.NORMAL -> "Normal: equilíbrio entre velocidade e precisão."
                        TermuxTrackingQuality.PRECISE -> "Preciso: mais frames e pontos; exige mais processamento."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Força ${(stabilizationStrength * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = stabilizationStrength,
                    onValueChange = { stabilizationStrength = it },
                    valueRange = 0.1f..1f,
                    enabled = !stabilization.running,
                )
                FilledTonalButton(
                    onClick = {
                        if (!stabilization.running) {
                            val snapshotClip = clip
                            val snapshotAsset = asset
                            val snapshotStrength = stabilizationStrength.toDouble()
                            val snapshotTrackingOptions = TermuxTrackingQualityPolicy.options(trackingQuality)
                            val snapshotCanvasWidth = project.width
                            val snapshotCanvasHeight = project.height
                            stabilization = StabilizationUiState(running = true, message = "Analisando movimento…")
                            scope.launch {
                                runCatching {
                                    val motion = TermuxAgentEngineRuntime.get(appContext)
                                        .mediaTrackingCoordinator
                                        .analyze(snapshotAsset, snapshotTrackingOptions)
                                        .getOrThrow()
                                    val correction = TermuxGlobalMotionAdapter.toStabilizationAnimations(
                                        result = motion,
                                        strength = snapshotStrength,
                                        sourceInUs = snapshotClip.sourceInUs,
                                        sourceOutUs = snapshotClip.sourceOutUs,
                                        baseTransform = snapshotClip.transform,
                                        canvasWidth = snapshotCanvasWidth,
                                        canvasHeight = snapshotCanvasHeight,
                                    )
                                    require(correction.isNotEmpty()) { "STABILIZATION_NO_USABLE_MOTION" }

                                    val currentProject = latestProject
                                    val currentClip = currentProject.findClip(snapshotClip.id)
                                        ?: error("STABILIZATION_CLIP_CHANGED")
                                    val currentAsset = currentProject.assets.firstOrNull { it.id == currentClip.assetId }
                                        ?: error("STABILIZATION_CLIP_CHANGED")
                                    require(latestSelectedClipId == snapshotClip.id) { "STABILIZATION_SELECTION_CHANGED" }
                                    require(
                                        currentClip.assetId == snapshotClip.assetId &&
                                            currentClip.sourceInUs == snapshotClip.sourceInUs &&
                                            currentClip.sourceOutUs == snapshotClip.sourceOutUs &&
                                            currentClip.transform == snapshotClip.transform &&
                                            currentProject.width == snapshotCanvasWidth &&
                                            currentProject.height == snapshotCanvasHeight &&
                                            currentAsset.fingerprint == snapshotAsset.fingerprint
                                    ) { "STABILIZATION_CLIP_CHANGED" }

                                    StabilizationApplyPolicy.merge(
                                        existing = currentClip.propertyAnimations,
                                        stabilization = correction,
                                    )
                                }.onSuccess { merged ->
                                    latestOnSetPropertyAnimations(merged)
                                    stabilization = StabilizationUiState(
                                        message = "Estabilização aplicada com compensação automática de bordas. Desfazer continua disponível.",
                                    )
                                }.onFailure { error ->
                                    stabilization = StabilizationUiState(
                                        message = stabilizationErrorMessage(error),
                                        isError = true,
                                    )
                                }
                            }
                        }
                    },
                    enabled = !stabilization.running,
                ) {
                    Text(if (stabilization.running) "Analisando…" else "Estabilizar vídeo")
                }
                stabilization.message?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stabilization.isError) {
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

private fun stabilizationErrorMessage(error: Throwable): String {
    val message = error.message
    return when {
        message == "OPENCV_UNAVAILABLE" -> "OpenCV não está disponível no UserLAnd/Termux."
        message == "TERMUX_ENGINE_NOT_PAIRED" -> "Pareie o LocalCut com o UserLAnd/Termux antes de estabilizar."
        message == "STABILIZATION_NO_USABLE_MOTION" -> "Não houve movimento confiável suficiente para estabilizar este trecho."
        message == "STABILIZATION_SELECTION_CHANGED" -> "A seleção mudou durante a análise; o resultado não foi aplicado."
        message == "STABILIZATION_CLIP_CHANGED" -> "O clipe, o transform ou o canvas mudou durante a análise; o resultado antigo foi descartado."
        message == "STABILIZATION_EDGE_COMPENSATION_TOO_LARGE" -> "O movimento exigiria zoom excessivo para esconder as bordas; a estabilização não foi aplicada."
        message == "TERMUX_TRACKING_REQUIRES_VIDEO" -> "A estabilização requer um clipe de vídeo."
        message?.contains("Connection refused", ignoreCase = true) == true -> "O motor UserLAnd/Termux não está acessível."
        else -> message ?: "Falha ao analisar o movimento no UserLAnd/Termux."
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
