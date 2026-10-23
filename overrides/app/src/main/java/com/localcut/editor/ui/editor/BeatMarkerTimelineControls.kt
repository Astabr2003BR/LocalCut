package com.localcut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath

@Composable
internal fun BeatMarkerTimelineControls(
    project: Project,
    playheadUs: Long,
    onAddMarker: (BeatMarker) -> Unit,
    onDeleteMarker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationUs = TimelineMath.durationUs(project)
    val nearby = BeatMarkerUiPolicy.nearestMarker(project.beatMarkers, playheadUs)
    val candidate = BeatMarkerUiPolicy.manualMarker(project, playheadUs)
    val markerColor = MaterialTheme.colorScheme.tertiary
    val strongMarkerColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error
    val rulerColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
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
            Text(
                "${project.beatMarkers.size} marcas",
                modifier = Modifier.padding(vertical = 12.dp),
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
