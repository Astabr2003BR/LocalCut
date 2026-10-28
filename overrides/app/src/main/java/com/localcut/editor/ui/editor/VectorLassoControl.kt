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
import com.localcut.editor.domain.history.MoveDesignElementsCommand
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
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
    var gesture by remember(project.id, project.width, project.height) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var lassoPolygon by remember(project.id, project.width, project.height) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var message by remember(project.id) { mutableStateOf<String?>(null) }
    val selectedIds = remember(
        project.designElements,
        lassoPolygon,
        project.width,
        project.height,
        playheadUs,
    ) {
        selectCurrentPathIds(project, lassoPolygon, playheadUs)
    }

    fun moveSelection(dx: Double, dy: Double) {
        if (lassoPolygon.isEmpty()) return
        val result = runCatching {
            val currentSession = runtime.editorViewModel.sessionState.value
            val currentProject = currentSession.project
            val currentPlayheadUs = runtime.editorViewModel.previewState.value.positionUs.coerceAtLeast(0L)
            val ids = selectCurrentPathIds(currentProject, lassoPolygon, currentPlayheadUs)
            require(ids.isNotEmpty()) { "LASSO_SELECTION_EMPTY" }
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(MoveDesignElementsCommand(ids, dx, dy)),
            )
        }
        message = if (result.isSuccess) {
            "Seleção movida. Desfazer continua disponível."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                else -> "Falha ao mover seleção • ${result.exceptionOrNull()?.message ?: "LASSO_MOVE_FAILED"}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Laço vetorial", style = MaterialTheme.typography.labelMedium)
        Text(
            "Circule os traços no quadro abaixo. A seleção acompanha o estado atual do projeto e do playhead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onSizeChanged { surfaceSize = it }
                .pointerInput(project.id, project.width, project.height, surfaceSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            gesture = listOf(CanvasPointPx(offset.x.toDouble(), offset.y.toDouble()))
                            lassoPolygon = emptyList()
                            message = null
                        },
                        onDrag = { change, _ ->
                            if (gesture.size < 2048) {
                                gesture = gesture + CanvasPointPx(
                                    change.position.x.toDouble(),
                                    change.position.y.toDouble(),
                                )
                            }
                        },
                        onDragCancel = {
                            gesture = emptyList()
                            lassoPolygon = emptyList()
                        },
                        onDragEnd = {
                            if (gesture.size < 3 || surfaceSize.width <= 0 || surfaceSize.height <= 0) {
                                lassoPolygon = emptyList()
                                message = "Faça um laço fechado com pelo menos três pontos."
                            } else {
                                runCatching {
                                    VectorLassoUiPolicy.toProjectPolygon(
                                        points = gesture,
                                        surfaceWidthPx = surfaceSize.width.toDouble(),
                                        surfaceHeightPx = surfaceSize.height.toDouble(),
                                        projectWidthPx = project.width.toDouble(),
                                        projectHeightPx = project.height.toDouble(),
                                    )
                                }.onSuccess { polygon ->
                                    lassoPolygon = polygon
                                    message = "Laço aplicado. A seleção será recalculada se o projeto ou playhead mudar."
                                }.onFailure { error ->
                                    lassoPolygon = emptyList()
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
                        shape?.kind == ShapeKind.PATH && shape.path != null && isActiveAt(element, playheadUs)
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

        if (lassoPolygon.isNotEmpty()) {
            Text(
                "${selectedIds.size} traço(s) selecionado(s) no estado atual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { moveSelection(-40.0, 0.0) }) { Text("←") }
                OutlinedButton(onClick = { moveSelection(40.0, 0.0) }) { Text("→") }
                OutlinedButton(onClick = { moveSelection(0.0, -40.0) }) { Text("↑") }
                OutlinedButton(onClick = { moveSelection(0.0, 40.0) }) { Text("↓") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    gesture = emptyList()
                    lassoPolygon = emptyList()
                    message = null
                },
            ) { Text("Limpar") }
            OutlinedButton(
                onClick = {
                    if (lassoPolygon.isEmpty()) return@OutlinedButton
                    val result = runCatching {
                        val currentSession = runtime.editorViewModel.sessionState.value
                        val currentProject = currentSession.project
                        val currentPlayheadUs = runtime.editorViewModel.previewState.value.positionUs.coerceAtLeast(0L)
                        val ids = selectCurrentPathIds(currentProject, lassoPolygon, currentPlayheadUs)
                        require(ids.isNotEmpty()) { "LASSO_SELECTION_EMPTY" }
                        runtime.editorViewModel.dispatch(
                            EditorAction.Execute(DeleteDesignElementsCommand(ids)),
                        )
                    }
                    if (result.isSuccess) {
                        gesture = emptyList()
                        lassoPolygon = emptyList()
                        message = "Traços removidos. Desfazer continua disponível."
                    } else {
                        message = when (result.exceptionOrNull()?.message) {
                            "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                            else -> "Falha ao excluir seleção • ${result.exceptionOrNull()?.message ?: "LASSO_DELETE_FAILED"}"
                        }
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

private fun selectCurrentPathIds(
    project: Project,
    polygon: List<CanvasPointPx>,
    playheadUs: Long,
): List<String> {
    if (polygon.size < 3 || project.width <= 0 || project.height <= 0) return emptyList()
    return runCatching {
        VectorLassoSelectionPolicy.selectPathIds(
            elements = project.designElements,
            lassoPointsPx = polygon,
            canvasWidthPx = project.width.toDouble(),
            canvasHeightPx = project.height.toDouble(),
            playheadUs = playheadUs,
        )
    }.getOrDefault(emptyList())
}

private fun isActiveAt(element: DesignElement, playheadUs: Long): Boolean {
    if (playheadUs < element.startUs) return false
    val endUs = try {
        Math.addExact(element.startUs, element.durationUs)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
    return playheadUs < endUs
}
