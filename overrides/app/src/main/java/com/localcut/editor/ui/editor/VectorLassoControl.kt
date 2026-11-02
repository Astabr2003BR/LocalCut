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
import com.localcut.editor.domain.design.DesignGroupSelectionPolicy
import com.localcut.editor.domain.design.DesignSelectionLayoutPolicy
import com.localcut.editor.domain.design.DesignSelectionTransformPolicy
import com.localcut.editor.domain.history.DeleteDesignElementsCommand
import com.localcut.editor.domain.history.GroupDesignElementsCommand
import com.localcut.editor.domain.history.MoveDesignElementsCommand
import com.localcut.editor.domain.history.TransformDesignElementsCommand
import com.localcut.editor.domain.history.UngroupDesignElementsCommand
import com.localcut.editor.domain.history.UpdateDesignElementsCommand
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.session.EditorAction
import java.util.UUID

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
    val liveSelectedIds = remember(project.designElements, project.designGroups, selectedIds) {
        val existing = VectorLassoPersistentSelectionPolicy.existing(selectedIds, project.designElements)
        DesignGroupSelectionPolicy.expand(
            selectedIds = existing,
            groups = project.designGroups,
            existingElementIds = project.designElements.map { it.id },
        )
    }
    val exactSelectedGroup = remember(project.designGroups, liveSelectedIds) {
        DesignGroupSelectionPolicy.exactGroup(liveSelectedIds, project.designGroups)
    }
    val canGroupSelection = remember(project.designGroups, liveSelectedIds) {
        DesignGroupSelectionPolicy.canCreateGroup(liveSelectedIds, project.designGroups)
    }
    val selectionContainsGroupedMember = remember(project.designGroups, liveSelectedIds) {
        DesignGroupSelectionPolicy.containsGroupedMember(liveSelectedIds, project.designGroups)
    }

    fun currentSelection(): Pair<Project, List<String>> {
        val currentProject = runtime.editorViewModel.sessionState.value.project
        val existing = VectorLassoPersistentSelectionPolicy.existing(selectedIds, currentProject.designElements)
        val ids = DesignGroupSelectionPolicy.expand(
            selectedIds = existing,
            groups = currentProject.designGroups,
            existingElementIds = currentProject.designElements.map { it.id },
        )
        require(ids.isNotEmpty()) { "LASSO_SELECTION_EMPTY" }
        return currentProject to ids
    }

    fun moveSelection(dx: Double, dy: Double) {
        val result = runCatching {
            val (_, ids) = currentSelection()
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(MoveDesignElementsCommand(ids, dx, dy)),
            )
        }
        message = if (result.isSuccess) {
            "Seleção movida. Os mesmos traços continuam selecionados."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                else -> "Falha ao mover seleção • ${result.exceptionOrNull()?.message ?: "LASSO_MOVE_FAILED"}"
            }
        }
    }

    fun transformSelection(scaleFactor: Double, rotationDeltaDeg: Double) {
        val result = runCatching {
            val (currentProject, ids) = currentSelection()
            val pivot = DesignSelectionTransformPolicy.pathBoundsCenterPx(
                elements = currentProject.designElements,
                selectedIds = ids,
                canvasWidthPx = currentProject.width.toDouble(),
                canvasHeightPx = currentProject.height.toDouble(),
            )
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(
                    TransformDesignElementsCommand(
                        elementIds = ids,
                        pivotX = pivot.x,
                        pivotY = pivot.y,
                        scaleFactor = scaleFactor,
                        rotationDeltaDeg = rotationDeltaDeg,
                    ),
                ),
            )
        }
        message = if (result.isSuccess) {
            "Seleção transformada. Desfazer continua disponível."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                else -> "Falha ao transformar seleção • ${result.exceptionOrNull()?.message ?: "LASSO_TRANSFORM_FAILED"}"
            }
        }
    }

    fun layoutSelection(action: String) {
        val result = runCatching {
            val (currentProject, ids) = currentSelection()
            require(!DesignGroupSelectionPolicy.containsGroupedMember(ids, currentProject.designGroups)) {
                "LASSO_LAYOUT_GROUPED"
            }
            val byId = currentProject.designElements.associateBy { it.id }
            val selected = ids.map { id -> byId[id] ?: error("LASSO_ELEMENT_MISSING") }
            val width = currentProject.width.toDouble()
            val height = currentProject.height.toDouble()
            val updated = when (action) {
                "LEFT" -> DesignSelectionLayoutPolicy.alignLeft(selected, width, height)
                "CENTER_X" -> DesignSelectionLayoutPolicy.alignCenterX(selected, width, height)
                "RIGHT" -> DesignSelectionLayoutPolicy.alignRight(selected, width, height)
                "TOP" -> DesignSelectionLayoutPolicy.alignTop(selected, width, height)
                "CENTER_Y" -> DesignSelectionLayoutPolicy.alignCenterY(selected, width, height)
                "BOTTOM" -> DesignSelectionLayoutPolicy.alignBottom(selected, width, height)
                "DISTRIBUTE_X" -> DesignSelectionLayoutPolicy.distributeHorizontally(selected, width, height)
                "DISTRIBUTE_Y" -> DesignSelectionLayoutPolicy.distributeVertically(selected, width, height)
                else -> error("LASSO_LAYOUT_ACTION_INVALID")
            }
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(UpdateDesignElementsCommand(updated)),
            )
        }
        message = if (result.isSuccess) {
            "Layout aplicado em uma única operação. Desfazer restaura a seleção anterior."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                "LASSO_LAYOUT_GROUPED" -> "Desagrupe antes de alinhar ou distribuir para preservar a geometria interna do grupo."
                else -> "Falha no layout • ${result.exceptionOrNull()?.message ?: "LASSO_LAYOUT_FAILED"}"
            }
        }
    }

    fun groupSelection() {
        val result = runCatching {
            val (currentProject, ids) = currentSelection()
            require(DesignGroupSelectionPolicy.canCreateGroup(ids, currentProject.designGroups)) {
                "LASSO_GROUP_UNAVAILABLE"
            }
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(
                    GroupDesignElementsCommand(
                        groupId = "group-${UUID.randomUUID()}",
                        elementIds = ids,
                    ),
                ),
            )
        }
        message = if (result.isSuccess) {
            "Seleção agrupada. Mover, redimensionar e girar agora usam o grupo inteiro."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                "LASSO_GROUP_UNAVAILABLE" -> "Seleção não pode ser agrupada: use dois ou mais traços soltos."
                else -> "Falha ao agrupar seleção • ${result.exceptionOrNull()?.message ?: "LASSO_GROUP_FAILED"}"
            }
        }
    }

    fun ungroupSelection() {
        val result = runCatching {
            val (currentProject, ids) = currentSelection()
            val group = DesignGroupSelectionPolicy.exactGroup(ids, currentProject.designGroups)
                ?: error("LASSO_UNGROUP_UNAVAILABLE")
            runtime.editorViewModel.dispatch(
                EditorAction.Execute(UngroupDesignElementsCommand(group.id)),
            )
        }
        message = if (result.isSuccess) {
            "Grupo desfeito. Os traços continuam selecionados individualmente."
        } else {
            when (result.exceptionOrNull()?.message) {
                "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                "LASSO_UNGROUP_UNAVAILABLE" -> "A seleção atual não corresponde a um único grupo."
                else -> "Falha ao desagrupar seleção • ${result.exceptionOrNull()?.message ?: "LASSO_UNGROUP_FAILED"}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Laço vetorial", style = MaterialTheme.typography.labelMedium)
        Text(
            "Circule os traços no quadro abaixo. Depois do laço, a seleção fica fixa por IDs; se tocar em um membro de grupo, o grupo inteiro entra na seleção.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onSizeChanged { surfaceSize = it }
                .pointerInput(project.id, surfaceSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            gesture = listOf(CanvasPointPx(offset.x.toDouble(), offset.y.toDouble()))
                            selectedIds = emptyList()
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
                            selectedIds = emptyList()
                        },
                        onDragEnd = {
                            if (gesture.size < 3 || surfaceSize.width <= 0 || surfaceSize.height <= 0) {
                                selectedIds = emptyList()
                                message = "Faça um laço fechado com pelo menos três pontos."
                            } else {
                                runCatching {
                                    val currentProject = runtime.editorViewModel.sessionState.value.project
                                    val currentPlayheadUs = runtime.editorViewModel.previewState.value.positionUs.coerceAtLeast(0L)
                                    VectorLassoCapturePolicy.capture(
                                        project = currentProject,
                                        playheadUs = currentPlayheadUs,
                                        gesturePoints = gesture,
                                        surfaceWidthPx = surfaceSize.width.toDouble(),
                                        surfaceHeightPx = surfaceSize.height.toDouble(),
                                    )
                                }.onSuccess { ids ->
                                    selectedIds = ids
                                    message = if (ids.isEmpty()) {
                                        "Nenhum traço dentro do laço."
                                    } else {
                                        "${ids.size} traço(s) selecionado(s). Grupos são selecionados por inteiro."
                                    }
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
                        val selected = element.id in liveSelectedIds
                        val color = if (selected) Color(0xFF20D5D2) else Color(0xFFE7E7EA)
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
                                strokeWidth = if (selected) 4f else 2f,
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

        if (selectedIds.isNotEmpty()) {
            Text(
                "${liveSelectedIds.size} traço(s) na seleção persistente${if (exactSelectedGroup != null) " • grupo" else ""}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (liveSelectedIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { moveSelection(-40.0, 0.0) }) { Text("←") }
                OutlinedButton(onClick = { moveSelection(40.0, 0.0) }) { Text("→") }
                OutlinedButton(onClick = { moveSelection(0.0, -40.0) }) { Text("↑") }
                OutlinedButton(onClick = { moveSelection(0.0, 40.0) }) { Text("↓") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { transformSelection(0.9, 0.0) }) { Text("Menor") }
                OutlinedButton(onClick = { transformSelection(1.1, 0.0) }) { Text("Maior") }
                OutlinedButton(onClick = { transformSelection(1.0, -15.0) }) { Text("↺ 15°") }
                OutlinedButton(onClick = { transformSelection(1.0, 15.0) }) { Text("↻ 15°") }
            }

            if (liveSelectedIds.size >= 2 && !selectionContainsGroupedMember) {
                Text("Alinhar", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { layoutSelection("LEFT") }) { Text("Esq.") }
                    OutlinedButton(onClick = { layoutSelection("CENTER_X") }) { Text("Centro X") }
                    OutlinedButton(onClick = { layoutSelection("RIGHT") }) { Text("Dir.") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { layoutSelection("TOP") }) { Text("Topo") }
                    OutlinedButton(onClick = { layoutSelection("CENTER_Y") }) { Text("Meio Y") }
                    OutlinedButton(onClick = { layoutSelection("BOTTOM") }) { Text("Base") }
                }
                if (liveSelectedIds.size >= 3) {
                    Text("Distribuir", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { layoutSelection("DISTRIBUTE_X") }) { Text("Horizontal") }
                        OutlinedButton(onClick = { layoutSelection("DISTRIBUTE_Y") }) { Text("Vertical") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { groupSelection() },
                    enabled = canGroupSelection,
                ) { Text("Agrupar") }
                OutlinedButton(
                    onClick = { ungroupSelection() },
                    enabled = exactSelectedGroup != null,
                ) { Text("Desagrupar") }
            }
            if (selectionContainsGroupedMember) {
                Text(
                    "Elementos agrupados são transformados juntos. Desagrupe antes de alinhar, distribuir ou excluir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    val result = runCatching {
                        val (_, ids) = currentSelection()
                        runtime.editorViewModel.dispatch(
                            EditorAction.Execute(DeleteDesignElementsCommand(ids)),
                        )
                    }
                    if (result.isSuccess) {
                        gesture = emptyList()
                        selectedIds = emptyList()
                        message = "Traços removidos. Desfazer continua disponível."
                    } else {
                        message = when (result.exceptionOrNull()?.message) {
                            "LASSO_SELECTION_EMPTY" -> "Nenhum traço selecionado no estado atual."
                            else -> "Falha ao excluir seleção • ${result.exceptionOrNull()?.message ?: "LASSO_DELETE_FAILED"}"
                        }
                    }
                },
                enabled = liveSelectedIds.isNotEmpty() && !selectionContainsGroupedMember,
            ) { Text("Excluir ${liveSelectedIds.size}") }
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

private fun isActiveAt(element: DesignElement, playheadUs: Long): Boolean {
    if (playheadUs < element.startUs) return false
    val endUs = try {
        Math.addExact(element.startUs, element.durationUs)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
    return playheadUs < endUs
}
