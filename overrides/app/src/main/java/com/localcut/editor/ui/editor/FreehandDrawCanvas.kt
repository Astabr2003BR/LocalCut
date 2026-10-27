package com.localcut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import com.localcut.editor.domain.model.PathPoint

@Composable
fun FreehandDrawCanvas(
    projectWidth: Int,
    projectHeight: Int,
    brushArgb: Int,
    brushWidthPx: Double,
    onStrokeFinished: (List<PathPoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(projectWidth > 0 && projectHeight > 0) { "project dimensions must be positive" }
    val points = remember { mutableStateListOf<PathPoint>() }
    val background = MaterialTheme.colorScheme.surface
    val aspectRatio = projectWidth.toFloat() / projectHeight.toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(background)
            .pointerInput(projectWidth, projectHeight, brushArgb, brushWidthPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        points.clear()
                        points += offset.normalized(size.width, size.height)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val next = change.position.normalized(size.width, size.height)
                        if (points.size < MAX_CAPTURE_POINTS) {
                            points += next
                        } else if (points.isNotEmpty()) {
                            points[points.lastIndex] = next
                        }
                    },
                    onDragCancel = { points.clear() },
                    onDragEnd = {
                        if (points.size >= 2) onStrokeFinished(points.toList())
                        points.clear()
                    },
                )
            },
    ) {
        if (points.size < 2) return@Canvas
        val color = Color(brushArgb)
        val previewWidth = brushWidthPx.toFloat().coerceAtLeast(2f)
        for (index in 1 until points.size) {
            val from = points[index - 1]
            val to = points[index]
            drawLine(
                color = color,
                start = Offset((from.x * size.width).toFloat(), (from.y * size.height).toFloat()),
                end = Offset((to.x * size.width).toFloat(), (to.y * size.height).toFloat()),
                strokeWidth = previewWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun Offset.normalized(width: Int, height: Int): PathPoint {
    require(width > 0 && height > 0) { "draw surface must have positive size" }
    return PathPoint(
        x = (x / width.toFloat()).toDouble().coerceIn(0.0, 1.0),
        y = (y / height.toFloat()).toDouble().coerceIn(0.0, 1.0),
    )
}

private const val MAX_CAPTURE_POINTS = 1_024
