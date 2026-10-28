package com.localcut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.design.DesignElementGeometryPolicy
import com.localcut.editor.domain.design.VectorLassoSelectionPolicy
import com.localcut.editor.domain.history.DeleteDesignElementsCommand
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.session.EditorAction

@Composable
fun VectorLassoControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val preview by runtime.editorViewModel.previewState.collectAsState()
    val project = session.project
    val playheadUs = preview.positionUs.coerceAtLeast(0L)

    var surfaceSize by remember(project.id) { mutableStateOf(IntSize.Zero) }
    var gesture by remember(project.id) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var selectedIds by remember(project.id) { mutableStateOf(emptyList<String>()) }
    var message by remember(project.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Laço vetorial", style = MaterialTheme.typography.labelMedium)
        Text(
            "Circule os traços no quadro abaixo. A seleção respeita posição, escala, rotação e o playhead atual.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onSizeChanged { surfaceSize = it }
                .pointerInput(project.id, playheadUs, surfaceSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            gesture = listOf(CanvasPointPx(offset.x.toDouble(), offset.y.toDouble()))
                            selectedIds = emptyList()
                            message = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (gesture.size < 2048) {
                                gesture = gesture + CanvasPointPx(
                                    change.position.x.toDouble(),
                                    change.position.y.toDouble(),
                                )
                            }
                        },
                        onDragCancel = {
                            gesture = emptyList()
                            selectedIds = emptyList()
                        },
                        onDragEnd = {
                            if (gesture.size < 3 || surfaceSize.width <= 0 || surfaceSize.height <= 0) {
                                selectedIds = emptyList()
                                message = "Faça um laço fechado com pelo menos três pontos."
                            } else {
                                runCatching {
                                    val polygon = VectorLassoUiPolicy.toProjectPolygon(
                                        points = gesture,
                                        surfaceWidthPx = surfaceSize.width.toDouble(),
                                        surfaceHeightPx = surfaceSize.height.toDouble(),
                                        projectWidthPx = project.width.toDouble(),
                                        projectHeightPx = project.height.toDouble(),
                                    )
                                    VectorLassoSelectionPolicy.selectPathIds(
                                        elements = project.designElements,
                                        lassoPointsPx = polygon,
                                        canvasWidthPx = project.width.toDouble(),
                                        canvasHeightPx = project.height.toDouble(),
                                        playheadUs = playheadUs,
                                    )
                                }.onSuccess { ids ->
                                    selectedIds = ids
                                    message = if (ids.isEmpty()) "Nenhum traço dentro do laço." else "${ids.size} traço(s) selecionado(s)."
                                }.onFailure { error ->
                                    selectedIds = emptyList()
                                    message = "Laço inválido • ${error.message ?: "LASSO_INVALID"}"
                                }
                            }
                        },
                    )
                },
        ) {
            drawRect(Color(0xFF151519))
            if (size.width > 0f && size.height > 0f && project.width > 0 && project.height > 0) {
                project.designElements
                    .asSequence()
                    .filter { element ->
                        val shape = element.shape
                        shape?.kind == ShapeKind.PATH && shape.path != null &&
                            playheadUs >= element.startUs &&
                            playheadUs < element.startUs + element.durationUs
                    }
                    .forEach { element ->
                        val points = runCatching {
                            DesignElementGeometryPolicy.transformedPathPointsPx(
                                element = element,
                                canvasWidthPx = project.width.toDouble(),
                                canvasHeightPx = project.height.toDouble(),
                            )
                        }.getOrDefault(emptyList())
                        val color = if (element.id in selectedIds) Color(0xFF20D5D2) else Color(0xFFE7E7EA)
                        points.zipWithNext().forEach { (left, right) ->
                            drawLine(
                                color = color,
                                start = Offset(
                                    (left.x / project.width * size.width).toFloat(),
                                    (left.y / project.height * size.height).toFloat(),
                                ),
                                end = Offset(
                                    (right.x / project.width * size.width).toFloat(),
                                    (right.y / project.height * size.height).toFloat(),
                                ),
                                strokeWidth = if (element.id in selectedIds) 4f else 2f,
                            )
                        }
                    }
            }

            if (gesture.size >= 2) {
                gesture.zipWithNext().forEach { (left, right) ->
                    drawLine(
                        color = Color(0xFFFFCC00),
                        start = Offset(left.x.toFloat(), left.y.toFloat()),
                        end = Offset(right.x.toFloat(), right.y.toFloat()),
                        strokeWidth = 3f,
                    )
                }
                if (gesture.size >= 3) {
                    val first = gesture.first()
                    val last = gesture.last()
                    drawLine(
                        color = Color(0x88FFCC00),
                        start = Offset(last.x.toFloat(), last.y.toFloat()),
                        end = Offset(first.x.toFloat(), first.y.toFloat()),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    gesture = emptyList()
                    selectedIds = emptyList()
                    message = null
                },
            ) { Text("Limpar") }
            OutlinedButton(
                onClick = {
                    val ids = selectedIds
                    if (ids.isEmpty()) return@OutlinedButton
                    val result = runCatching {
                        runtime.editorViewModel.dispatch(
                            EditorAction.Execute(DeleteDesignElementsCommand(ids)),
                        )
                    }
                    if (result.isSuccess) {
                        gesture = emptyList()
                        selectedIds = emptyList()
                        message = "Traços removidos. Desfazer continua disponível."
                    } else {
                        message = "Falha ao excluir seleção • ${result.exceptionOrNull()?.message ?: "LASSO_DELETE_FAILED"}"
                    }
                },
                enabled = selectedIds.isNotEmpty(),
            ) { Text("Excluir ${selectedIds.size}") }
        }

        message?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha") || text.startsWith("Laço inválido")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
