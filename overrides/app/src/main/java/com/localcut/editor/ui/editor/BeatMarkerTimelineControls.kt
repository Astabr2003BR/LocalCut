package com.localcut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.audio.AndroidBeatAnalyzer
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BeatMarkerTimelineControls(
    project: Project,
    selectedClipId: String?,
    playheadUs: Long,
    onAddMarker: (BeatMarker) -> Unit,
    onDeleteMarker: (String) -> Unit,
    onApplyDetectedMarkers: (String, List<BeatMarker>) -> Unit,
    onClearDetectedMarkers: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationUs = TimelineMath.durationUs(project)
    val nearby = BeatMarkerUiPolicy.nearestMarker(project.beatMarkers, playheadUs)
    val candidate = BeatMarkerUiPolicy.manualMarker(project, playheadUs)
    val selectedClip = selectedClipId?.let(project::findClip)
    val selectedAsset = selectedClip?.let { clip -> project.assets.firstOrNull { it.id == clip.assetId } }
    val canAnalyze = BeatMarkerUiPolicy.canAutoAnalyzeSelectedClip(project, selectedClipId) && selectedAsset != null
    val hasAutoMarkers = BeatMarkerUiPolicy.hasAutoMarkersForClip(project.beatMarkers, selectedClipId)
    val applicationContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var analyzingClipId by remember { mutableStateOf<String?>(null) }
    var sensitivity by remember(selectedClipId) { mutableStateOf(BeatDetectionSensitivity.STANDARD) }
    var analysisMessage by remember(selectedClipId) { mutableStateOf<String?>(null) }
    var analysisError by remember(selectedClipId) { mutableStateOf<String?>(null) }
    val markerColor = MaterialTheme.colorScheme.tertiary
    val strongMarkerColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error
    val rulerColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (nearby != null) {
                FilledTonalButton(onClick = { onDeleteMarker(nearby.id) }) {
                    Text("Remover batida")
                }
            } else {
                OutlinedButton(
                    onClick = { candidate?.let(onAddMarker) },
                    enabled = candidate != null,
                ) {
                    Text("Marcar batida")
                }
            }
            OutlinedButton(
                onClick = {
                    val clip = selectedClip ?: return@OutlinedButton
                    val asset = selectedAsset ?: return@OutlinedButton
                    if (analyzingClipId != null) return@OutlinedButton
                    val requestedClipId = clip.id
                    val requestedUri = asset.uri
                    val requestedSourceInUs = clip.sourceInUs
                    val requestedSourceOutUs = clip.sourceOutUs
                    val requestedSettings = BeatDetectionUiPolicy.settings(sensitivity)
                    analyzingClipId = requestedClipId
                    analysisMessage = null
                    analysisError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            AndroidBeatAnalyzer(applicationContext.contentResolver).analyze(
                                uri = requestedUri,
                                sourceInUs = requestedSourceInUs,
                                sourceOutUs = requestedSourceOutUs,
                                settings = requestedSettings,
                            )
                        }
                        result.fold(
                            onSuccess = { markers ->
                                onApplyDetectedMarkers(requestedClipId, markers)
                                analysisMessage = if (markers.isEmpty()) {
                                    "Nenhuma batida detectada"
                                } else {
                                    "${markers.size} batidas detectadas"
                                }
                            },
                            onFailure = { error ->
                                analysisError = error.message ?: "Falha ao analisar batidas"
                            },
                        )
                        analyzingClipId = null
                    }
                },
                enabled = canAnalyze && analyzingClipId == null,
            ) {
                Text(if (analyzingClipId != null) "Analisando…" else "Detectar batidas")
            }
            OutlinedButton(
                onClick = { selectedClipId?.let(onClearDetectedMarkers) },
                enabled = selectedClipId != null && hasAutoMarkers && analyzingClipId == null,
            ) {
                Text("Limpar automáticas")
            }
            Text(
                "${project.beatMarkers.size} marcas",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedClip != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Sensibilidade",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BeatDetectionSensitivity.entries.forEach { option ->
                    val label = BeatDetectionUiPolicy.label(option)
                    if (option == sensitivity) {
                        FilledTonalButton(onClick = { sensitivity = option }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { sensitivity = option }) { Text(label) }
                    }
                }
            }
        }

        analysisMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        analysisError?.let { message ->
            Text(
                "Batidas: $message",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (selectedClip != null && !canAnalyze && analyzingClipId == null) {
            Text(
                "Detecção automática requer áudio/vídeo em velocidade 1× sem reverse/freeze/ramp.",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(rulerColor, MaterialTheme.shapes.extraSmall)
                .drawBehind {
                    if (durationUs > 0L) {
                        project.beatMarkers.forEach { marker ->
                            val fraction = (marker.timeUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0)
                            val x = size.width * fraction.toFloat()
                            val strength = marker.strength.coerceIn(0.0, 1.0).toFloat()
                            drawLine(
                                color = if (strength >= 0.85f) strongMarkerColor else markerColor,
                                start = Offset(x, size.height * (1f - 0.35f * strength)),
                                end = Offset(x, size.height),
                                strokeWidth = if (strength >= 0.85f) 3f else 2f,
                            )
                        }
                        val playheadFraction = (playheadUs.coerceIn(0L, durationUs).toDouble() / durationUs.toDouble())
                            .coerceIn(0.0, 1.0)
                        val playheadX = size.width * playheadFraction.toFloat()
                        drawLine(
                            color = playheadColor,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, size.height),
                            strokeWidth = 2f,
                        )
                    }
                },
        )
    }
}
