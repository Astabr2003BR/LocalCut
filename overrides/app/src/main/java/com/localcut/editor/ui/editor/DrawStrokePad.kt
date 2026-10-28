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
import com.localcut.editor.domain.design.BrushPresetPolicy
import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.design.DrawStrokeCapturePolicy
import com.localcut.editor.domain.design.DrawStrokePolicy
import com.localcut.editor.domain.design.FrameExposurePolicy
import com.localcut.editor.domain.design.OnionSkinCandidate
import com.localcut.editor.domain.design.OnionSkinPolicy
import com.localcut.editor.domain.design.StrokeAssistMode
import com.localcut.editor.domain.design.StrokeAssistPolicy
import com.localcut.editor.domain.design.StrokeSymmetryMode
import com.localcut.editor.domain.design.StrokeSymmetryPolicy
import com.localcut.editor.domain.design.VectorEraserCandidate
import com.localcut.editor.domain.design.VectorEraserPolicy
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
    onDeleteElement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var capturedPoints by remember(project.id) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var padSize by remember(project.id) { mutableStateOf(IntSize.Zero) }
    var strokeWidthPx by remember(project.id) { mutableDoubleStateOf(DrawStrokePadPolicy.DEFAULT_STROKE_WIDTH_PX) }
    var brushOpacity by remember(project.id) { mutableDoubleStateOf(1.0) }
    var selectedBrushId by remember(project.id) { mutableStateOf<String?>(null) }
    var eraserRadiusPx by remember(project.id) { mutableDoubleStateOf(24.0) }
    var strokeArgb by remember(project.id) { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var frameByFrameEnabled by remember(project.id) { mutableStateOf(false) }
    var onionSkinEnabled by remember(project.id) { mutableStateOf(true) }
    var exposureFrames by remember(project.id) { mutableIntStateOf(1) }
    var eraserEnabled by remember(project.id) { mutableStateOf(false) }
    var stabilizationStrength by remember(project.id) {
        mutableIntStateOf(DrawStrokePadPolicy.DEFAULT_STABILIZATION_STRENGTH)
    }
    var strokeAssistMode by remember(project.id) { mutableStateOf(StrokeAssistMode.FREEHAND) }
    var symmetryMode by remember(project.id) { mutableStateOf(StrokeSymmetryMode.NONE) }
    val previewColor = drawColorPresets.firstOrNull { it.argb == strokeArgb }?.color ?: Color(strokeArgb)
    val gestureColor = if (eraserEnabled) Color(0xFFFF6B6B) else previewColor.copy(alpha = brushOpacity.toFloat())

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
            when {
                eraserEnabled -> "Borracha vetorial: arraste sobre um traço ativo para removê-lo. Desfazer restaura o traço."
                strokeAssistMode == StrokeAssistMode.LINE -> "Linha reta: arraste do ponto inicial ao final; o preview mostra a linha antes de soltar."
                symmetryMode != StrokeSymmetryMode.NONE -> "Simetria ativa: o traço espelhado é salvo como PATH separado e editável."
                frameByFrameEnabled -> "Modo frame a frame: cada gesto ocupa a exposição escolhida. Onion skin mostra frames vizinhos."
                else -> "Arraste o dedo no quadro. Cada gesto vira um PATH editável e pode ser desfeito."
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
            if (eraserEnabled) {
                FilledTonalButton(onClick = { eraserEnabled = false }) { Text("Borracha ON") }
            } else {
                OutlinedButton(onClick = { eraserEnabled = true }) { Text("Borracha") }
            }
        }

        if (frameByFrameEnabled && !eraserEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(1, 2, 3, 4).forEach { frames ->
                    if (exposureFrames == frames) {
                        FilledTonalButton(onClick = { exposureFrames = frames }) { Text("${frames}f") }
                    } else {
                        OutlinedButton(onClick = { exposureFrames = frames }) { Text("${frames}f") }
                    }
                }
            }
        }

        if (!eraserEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (strokeAssistMode == StrokeAssistMode.FREEHAND) {
                    FilledTonalButton(onClick = { strokeAssistMode = StrokeAssistMode.FREEHAND }) { Text("Livre") }
                } else {
                    OutlinedButton(onClick = { strokeAssistMode = StrokeAssistMode.FREEHAND }) { Text("Livre") }
                }
                if (strokeAssistMode == StrokeAssistMode.LINE) {
                    FilledTonalButton(onClick = { strokeAssistMode = StrokeAssistMode.LINE }) { Text("Linha reta") }
                } else {
                    OutlinedButton(onClick = { strokeAssistMode = StrokeAssistMode.LINE }) { Text("Linha reta") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (symmetryMode == StrokeSymmetryMode.NONE) {
                    FilledTonalButton(onClick = { symmetryMode = StrokeSymmetryMode.NONE }) { Text("Simetria OFF") }
                } else {
                    OutlinedButton(onClick = { symmetryMode = StrokeSymmetryMode.NONE }) { Text("Simetria OFF") }
                }
                if (symmetryMode == StrokeSymmetryMode.VERTICAL_AXIS) {
                    FilledTonalButton(onClick = { symmetryMode = StrokeSymmetryMode.VERTICAL_AXIS }) { Text("Vertical") }
                } else {
                    OutlinedButton(onClick = { symmetryMode = StrokeSymmetryMode.VERTICAL_AXIS }) { Text("Vertical") }
                }
                if (symmetryMode == StrokeSymmetryMode.HORIZONTAL_AXIS) {
                    FilledTonalButton(onClick = { symmetryMode = StrokeSymmetryMode.HORIZONTAL_AXIS }) { Text("Horizontal") }
                } else {
                    OutlinedButton(onClick = { symmetryMode = StrokeSymmetryMode.HORIZONTAL_AXIS }) { Text("Horizontal") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BrushPresetPolicy.presets().forEach { preset ->
                    val select = {
                        selectedBrushId = preset.id
                        strokeWidthPx = preset.strokeWidthPx
                        brushOpacity = preset.opacity
                    }
                    if (selectedBrushId == preset.id) {
                        FilledTonalButton(onClick = select) { Text(preset.label) }
                    } else {
                        OutlinedButton(onClick = select) { Text(preset.label) }
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
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (eraserEnabled) "Borracha ${eraserRadiusPx.roundToInt()} px" else "Pincel ${strokeWidthPx.roundToInt()} px",
                modifier = Modifier.width(112.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            if (eraserEnabled) {
                Slider(
                    value = eraserRadiusPx.toFloat(),
                    onValueChange = { eraserRadiusPx = it.toDouble() },
                    valueRange = 4f..48f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Slider(
                    value = strokeWidthPx.toFloat(),
                    onValueChange = {
                        selectedBrushId = null
                        strokeWidthPx = it.toDouble()
                    },
                    valueRange = 2f..48f,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!eraserEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Opacidade ${(brushOpacity * 100.0).roundToInt()}%",
                    modifier = Modifier.width(112.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = brushOpacity.toFloat(),
                    onValueChange = {
                        selectedBrushId = null
                        brushOpacity = it.toDouble()
                    },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Estabilização $stabilizationStrength%",
                    modifier = Modifier.width(112.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = stabilizationStrength.toFloat(),
                    onValueChange = { stabilizationStrength = it.roundToInt().coerceIn(0, 100) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .onSizeChanged { padSize = it }
                .pointerInput(
                    project.id,
                    playheadUs,
                    strokeArgb,
                    strokeWidthPx,
                    brushOpacity,
                    eraserRadiusPx,
                    padSize,
                    frameByFrameEnabled,
                    exposureFrames,
                    eraserEnabled,
                    stabilizationStrength,
                    strokeAssistMode,
                    symmetryMode,
                    project.fps,
                ) {
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
                                if (eraserEnabled) {
                                    val normalizedGesture = runCatching {
                                        DrawStrokePolicy.normalize(
                                            points = points,
                                            canvasWidthPx = size.width.toDouble(),
                                            canvasHeightPx = size.height.toDouble(),
                                        )
                                    }.getOrNull()
                                    if (normalizedGesture != null) {
                                        val minSidePx = minOf(size.width, size.height).toDouble().coerceAtLeast(1.0)
                                        val radiusNormalized = (eraserRadiusPx / minSidePx)
                                            .coerceIn(0.0001, VectorEraserPolicy.MAX_RADIUS_NORMALIZED)
                                        val candidates = pathElements
                                            .filter { isActiveAt(it, playheadUs.coerceAtLeast(0L)) }
                                            .mapNotNull { element ->
                                                val path = element.shape?.path ?: return@mapNotNull null
                                                VectorEraserCandidate(
                                                    elementId = element.id,
                                                    zIndex = element.zIndex,
                                                    points = path.points,
                                                )
                                            }
                                        VectorEraserPolicy.findTopmostHit(
                                            candidates = candidates,
                                            eraserPoints = normalizedGesture,
                                            radiusNormalized = radiusNormalized,
                                        )?.let(onDeleteElement)
                                    }
                                } else {
                                    val exposureWindow = if (frameByFrameEnabled) {
                                        FrameExposurePolicy.window(
                                            playheadUs = playheadUs.coerceAtLeast(0L),
                                            fps = project.fps,
                                            frameCount = exposureFrames,
                                        )
                                    } else {
                                        null
                                    }
                                    val stabilizedPoints = DrawStrokePadPolicy.prepareStrokePoints(
                                        points = points,
                                        stabilizationStrength = stabilizationStrength,
                                    )
                                    val preparedPoints = StrokeAssistPolicy.apply(stabilizedPoints, strokeAssistMode)
                                    val expandedStrokes = StrokeSymmetryPolicy.expand(
                                        points = preparedPoints,
                                        canvasWidthPx = size.width.toDouble(),
                                        canvasHeightPx = size.height.toDouble(),
                                        mode = symmetryMode,
                                    )
                                    val reservedIds = project.designElements.mapTo(mutableSetOf()) { it.id }
                                    val reservedZIndexes = project.designElements.mapTo(mutableListOf()) { it.zIndex }
                                    expandedStrokes.forEach { strokePoints ->
                                        val id = DrawStrokePadPolicy.nextStrokeId(reservedIds)
                                        reservedIds += id
                                        val zIndex = DrawStrokePadPolicy.nextZIndex(reservedZIndexes)
                                        reservedZIndexes += zIndex
                                        val element = DrawStrokePolicy.createPathElement(
                                            id = id,
                                            startUs = exposureWindow?.startUs ?: playheadUs.coerceAtLeast(0L),
                                            durationUs = exposureWindow?.durationUs ?: DrawStrokePadPolicy.DEFAULT_DURATION_US,
                                            points = strokePoints,
                                            canvasWidthPx = size.width.toDouble(),
                                            canvasHeightPx = size.height.toDouble(),
                                            strokeArgb = strokeArgb,
                                            strokeWidthPx = strokeWidthPx,
                                            zIndex = zIndex,
                                        ).copy(opacity = brushOpacity)
                                        onAddElement(element)
                                    }
                                }
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
                val ghostColor = Color(shape.strokeArgb ?: 0xFFFFFFFF.toInt()).copy(
                    alpha = (ghost.alpha * element.opacity).toFloat().coerceIn(0f, 1f),
                )
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

            val previewBasePoints = if (
                !eraserEnabled &&
                strokeAssistMode == StrokeAssistMode.LINE &&
                capturedPoints.size >= 2
            ) {
                listOf(capturedPoints.first(), capturedPoints.last())
            } else {
                capturedPoints
            }
            val previewStrokes = if (
                !eraserEnabled &&
                previewBasePoints.size >= 2 &&
                size.width > 0f &&
                size.height > 0f
            ) {
                StrokeSymmetryPolicy.expand(
                    points = previewBasePoints,
                    canvasWidthPx = size.width.toDouble(),
                    canvasHeightPx = size.height.toDouble(),
                    mode = symmetryMode,
                )
            } else {
                listOf(previewBasePoints)
            }
            previewStrokes.forEach { previewPoints ->
                previewPoints.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = gestureColor,
                        start = Offset(start.x.toFloat(), start.y.toFloat()),
                        end = Offset(end.x.toFloat(), end.y.toFloat()),
                        strokeWidth = if (eraserEnabled) eraserRadiusPx.toFloat().coerceAtLeast(1f) else strokeWidthPx.toFloat(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun isActiveAt(element: DesignElement, playheadUs: Long): Boolean {
    if (playheadUs < element.startUs) return false
    val endUs = if (element.durationUs > Long.MAX_VALUE - element.startUs) Long.MAX_VALUE else element.startUs + element.durationUs
    return playheadUs < endUs
}
