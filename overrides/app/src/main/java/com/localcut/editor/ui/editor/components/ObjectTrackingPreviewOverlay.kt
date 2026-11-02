package com.localcut.editor.ui.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.localcut.editor.ui.editor.ObjectTrackingSelectionGesturePolicy
import com.localcut.editor.ui.editor.PreviewSelectionRect

@Composable
fun ObjectTrackingPreviewOverlay(
    enabled: Boolean,
    selection: PreviewSelectionRect?,
    onSelectionChange: (PreviewSelectionRect?) -> Unit,
    onViewportSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember(enabled) { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                surfaceSize = size
                onViewportSizeChanged(size)
            }
            .pointerInput(enabled, surfaceSize) {
                if (!enabled || surfaceSize.width <= 0 || surfaceSize.height <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        dragStart = start
                        onSelectionChange(null)
                    },
                    onDrag = { change, _ ->
                        val start = dragStart ?: return@detectDragGestures
                        val rect = ObjectTrackingSelectionGesturePolicy.rect(
                            startX = start.x.toDouble(),
                            startY = start.y.toDouble(),
                            currentX = change.position.x.toDouble(),
                            currentY = change.position.y.toDouble(),
                            viewportWidthPx = surfaceSize.width.toDouble(),
                            viewportHeightPx = surfaceSize.height.toDouble(),
                        )
                        onSelectionChange(rect)
                    },
                    onDragEnd = { dragStart = null },
                    onDragCancel = {
                        dragStart = null
                        onSelectionChange(null)
                    },
                )
            },
    ) {
        if (!enabled) return@Canvas
        selection?.let { rect ->
            val left = rect.leftPx.toFloat()
            val top = rect.topPx.toFloat()
            val width = (rect.rightPx - rect.leftPx).toFloat()
            val height = (rect.bottomPx - rect.topPx).toFloat()
            drawRect(
                color = Color(0x3320D5D2),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
            )
            drawRect(
                color = Color(0xFF20D5D2),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                style = Stroke(width = 3f),
            )
        }
    }
}
