package com.localcut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.design.DrawStrokeCapturePolicy
import com.localcut.editor.domain.design.DrawStrokePolicy
import com.localcut.editor.domain.design.OnionSkinCandidate
import com.localcut.editor.domain.design.OnionSkinPolicy
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import kotlin.math.roundToInt

private data class DrawColorPreset(val label: String, val argb: Int, val color: Color)

private val drawColorPresets = listOf(
    DrawColorPreset("Branco", 0xFFFFFFFF.toInt(), Color.White),
    DrawColorPreset("Preto", 0xFF000000.toInt(), Color.Black),
    DrawColorPreset("Vermelho", 0xFFFF3B30.toInt(), Color(0xFFFF3B30)),
    DrawColorPreset("Azul", 0xFF3B82F6.toInt(), Color(0xFF3B82F6)),
    DrawColorPreset("Verde", 0xFF22C55E.toInt(), Color(0xFF22C55E)),
)

@Composable
fun DrawStrokePad(
    project: Project,
    playheadUs: Long,
    onAddElement: (DesignElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    var capturedPoints by remember(project.id) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var padSize by remember(project.id) { mutableStateOf(IntSize.Zero) }
    var strokeWidthPx by remember(project.id) { mutableDoubleStateOf(DrawStrokePadPolicy.DEFAULT_STROKE_WIDTH_PX) }
    var strokeArgb by remember(project.id) { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var frameByFrameEnabled by remember(project.id) { mutableStateOf(false) }
    var onionSkinEnabled by remember(project.id) { mutableStateOf(true) }
    val previewColor = drawColorPresets.firstOrNull { it.argb == strokeArgb }?.color ?: Color(strokeArgb)

    val pathElements = project.designElements.filter { element ->
        element.shape?.kind == ShapeKind.PATH && element.shape.path != null
    }
    val pathElementsById = pathElements.associateBy { it.id }
    val onionGhosts = if (frameByFrameEnabled && onionSkinEnabled) {
        OnionSkinPolicy.select(
            candidates = pathElements.map { OnionSkinCandidate(it.id, it.startUs) },
            playheadUs = playheadUs.coerceAtLeast(0L),
            fps = project.fps,
            previousFrames = 1,
            nextFrames = 1,
        )
    } else {
        emptyList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Desenho livre", style = MaterialTheme.typography.labelMedium)
        Text(
            if (frameByFrameEnabled) {
                "Modo frame a frame: cada gesto ocupa um frame. Onion skin mostra o frame anterior e o próximo."
            } else {
                "Arraste o dedo no quadro. Cada gesto vira um PATH editável e pode ser desfeito."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (frameByFrameEnabled) {
                FilledTonalButton(onClick = { frameByFrameEnabled = false }) { Text("Frame a frame ON") }
            } else {
                OutlinedButton(onClick = { frameByFrameEnabled = true }) { Text("Frame a frame") }
            }
            if (frameByFrameEnabled) {
                if (onionSkinEnabled) {
                    FilledTonalButton(onClick = { onionSkinEnabled = false }) { Text("Onion skin ON") }
                } else {
                    OutlinedButton(onClick = { onionSkinEnabled = true }) { Text("Onion skin") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            drawColorPresets.forEach { preset ->
                if (preset.argb == strokeArgb) {
                    FilledTonalButton(onClick = { strokeArgb = preset.argb }) { Text(preset.label) }
                } else {
                    OutlinedButton(onClick = { strokeArgb = preset.argb }) { Text(preset.label) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Pincel ${strokeWidthPx.roundToInt()} px",
                modifier = Modifier.width(104.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = strokeWidthPx.toFloat(),
                onValueChange = { strokeWidthPx = it.toDouble() },
                valueRange = 2f..48f,
                modifier = Modifier.weight(1f),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .onSizeChanged { padSize = it }
                .pointerInput(project.id, playheadUs, strokeArgb, strokeWidthPx, padSize, frameByFrameEnabled, project.fps) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            capturedPoints = listOf(CanvasPointPx(offset.x.toDouble(), offset.y.toDouble()))
                        },
                        onDragCancel = { capturedPoints = emptyList() },
                        onDragEnd = {
                            val points = capturedPoints
                            val size = padSize
                            if (
                                size.width > 0 &&
                                size.height > 0 &&
                                DrawStrokeCapturePolicy.canCommit(points)
                            ) {
                                val frameWindow = if (frameByFrameEnabled) {
                                    OnionSkinPolicy.frameWindow(playheadUs.coerceAtLeast(0L), project.fps)
                                } else {
                                    null
                                }
                                val element = DrawStrokePolicy.createPathElement(
                                    id = DrawStrokePadPolicy.nextStrokeId(project.designElements.mapTo(hashSetOf()) { it.id }),
                                    startUs = frameWindow?.startUs ?: playheadUs.coerceAtLeast(0L),
                                    durationUs = frameWindow?.durationUs ?: DrawStrokePadPolicy.DEFAULT_DURATION_US,
                                    points = points,
                                    canvasWidthPx = size.width.toDouble(),
                                    canvasHeightPx = size.height.toDouble(),
                                    strokeArgb = strokeArgb,
                                    strokeWidthPx = strokeWidthPx,
                                    zIndex = DrawStrokePadPolicy.nextZIndex(project.designElements.map { it.zIndex }),
                                )
                                onAddElement(element)
                            }
                            capturedPoints = emptyList()
                        },
                        onDrag = { change, _ ->
                            capturedPoints = DrawStrokeCapturePolicy.append(
                                capturedPoints,
                                CanvasPointPx(change.position.x.toDouble(), change.position.y.toDouble()),
                            )
                        },
                    )
                },
        ) {
            onionGhosts.asReversed().forEach { ghost ->
                val element = pathElementsById[ghost.elementId] ?: return@forEach
                val shape = element.shape ?: return@forEach
                val path = shape.path ?: return@forEach
                val ghostColor = Color(shape.strokeArgb ?: 0xFFFFFFFF.toInt()).copy(alpha = ghost.alpha.toFloat())
                path.points.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = ghostColor,
                        start = Offset((start.x * size.width).toFloat(), (start.y * size.height).toFloat()),
                        end = Offset((end.x * size.width).toFloat(), (end.y * size.height).toFloat()),
                        strokeWidth = shape.strokeWidthPx.toFloat().coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                    )
                }
            }

            capturedPoints.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = previewColor,
                    start = Offset(start.x.toFloat(), start.y.toFloat()),
                    end = Offset(end.x.toFloat(), end.y.toFloat()),
                    strokeWidth = strokeWidthPx.toFloat(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
