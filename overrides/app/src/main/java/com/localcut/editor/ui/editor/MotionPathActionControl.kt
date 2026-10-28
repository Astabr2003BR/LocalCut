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
import com.localcut.editor.domain.animation.MotionPathPlanner
import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.history.SetClipPropertyAnimationsCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.session.EditorAction

@Composable
fun MotionPathActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val project = session.project
    val clip = session.selectedClipId?.let(project::findClip)
    val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.assetId } }
    val isVisual = asset?.type == AssetType.VIDEO || asset?.type == AssetType.IMAGE
    if (clip == null || !isVisual) return

    var surfaceSize by remember(clip.id) { mutableStateOf(IntSize.Zero) }
    var gesture by remember(clip.id) { mutableStateOf(emptyList<CanvasPointPx>()) }
    var message by remember(clip.id) { mutableStateOf<String?>(null) }
    val hasGeneratedPath = MotionPathUiPolicy.hasGeneratedMotionPath(clip.propertyAnimations)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Motion path", style = MaterialTheme.typography.labelMedium)
        Text(
            "Desenhe o movimento relativo. O primeiro ponto mantém a posição atual do clipe; o último coincide com o fim do clipe.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .onSizeChanged { surfaceSize = it }
                .pointerInput(clip.id, surfaceSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            gesture = listOf(CanvasPointPx(offset.x.toDouble(), offset.y.toDouble()))
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
                        onDragCancel = { gesture = emptyList() },
                    )
                },
        ) {
            drawRect(Color(0xFF151519))
            drawLine(
                color = Color(0x334DFFFFFF),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color(0x334DFFFFFF),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1f,
            )
            gesture.zipWithNext().forEach { (left, right) ->
                drawLine(
                    color = Color(0xFF20D5D2),
                    start = Offset(left.x.toFloat(), left.y.toFloat()),
                    end = Offset(right.x.toFloat(), right.y.toFloat()),
                    strokeWidth = 4f,
                )
            }
            gesture.firstOrNull()?.let { first ->
                drawCircle(Color(0xFFFFFFFF), radius = 5f, center = Offset(first.x.toFloat(), first.y.toFloat()))
            }
            gesture.lastOrNull()?.let { last ->
                drawCircle(Color(0xFFFFCC00), radius = 5f, center = Offset(last.x.toFloat(), last.y.toFloat()))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    gesture = emptyList()
                    message = null
                },
            ) { Text("Limpar") }
            OutlinedButton(
                onClick = {
                    val currentSession = runtime.editorViewModel.sessionState.value
                    require(currentSession.selectedClipId == clip.id) { "MOTION_PATH_SELECTION_CHANGED" }
                    val currentClip = currentSession.project.findClip(clip.id)
                        ?: error("MOTION_PATH_CLIP_CHANGED")
                    require(currentClip.assetId == clip.assetId) { "MOTION_PATH_CLIP_CHANGED" }
                    val result = runCatching {
                        require(surfaceSize.width > 0 && surfaceSize.height > 0) { "MOTION_PATH_SURFACE_UNAVAILABLE" }
                        val points = MotionPathUiPolicy.toMotionPoints(
                            gesture = gesture,
                            surfaceWidthPx = surfaceSize.width.toDouble(),
                            surfaceHeightPx = surfaceSize.height.toDouble(),
                            projectWidthPx = currentSession.project.width.toDouble(),
                            projectHeightPx = currentSession.project.height.toDouble(),
                            durationUs = currentClip.durationUs,
                            baseX = currentClip.transform.x,
                            baseY = currentClip.transform.y,
                        )
                        val path = MotionPathPlanner.plan(points)
                        val merged = MotionPathUiPolicy.merge(currentClip.propertyAnimations, path)
                        runtime.editorViewModel.dispatch(
                            EditorAction.Execute(SetClipPropertyAnimationsCommand(currentClip.id, merged)),
                        )
                    }
                    message = if (result.isSuccess) {
                        "Motion path aplicado. Desfazer continua disponível."
                    } else {
                        when (result.exceptionOrNull()?.message) {
                            "MOTION_PATH_CONFLICTS_WITH_STABILIZATION" ->
                                "Remova a estabilização antes de aplicar um motion path."
                            "MOTION_PATH_SELECTION_CHANGED", "MOTION_PATH_CLIP_CHANGED" ->
                                "A seleção mudou; a trajetória não foi aplicada."
                            else -> "Falha no motion path • ${result.exceptionOrNull()?.message ?: "MOTION_PATH_FAILED"}"
                        }
                    }
                },
                enabled = gesture.size >= 2 && clip.durationUs in 1L..MotionPathPlanner.MAX_OFFSET_US,
            ) { Text("Aplicar") }

            if (hasGeneratedPath) {
                OutlinedButton(
                    onClick = {
                        val currentSession = runtime.editorViewModel.sessionState.value
                        val currentClip = currentSession.project.findClip(clip.id) ?: return@OutlinedButton
                        val cleaned = MotionPathUiPolicy.removeGeneratedMotionPath(currentClip.propertyAnimations)
                        val result = runCatching {
                            runtime.editorViewModel.dispatch(
                                EditorAction.Execute(SetClipPropertyAnimationsCommand(currentClip.id, cleaned)),
                            )
                        }
                        message = if (result.isSuccess) "Motion path removido." else {
                            "Falha ao remover motion path • ${result.exceptionOrNull()?.message ?: "MOTION_PATH_REMOVE_FAILED"}"
                        }
                    },
                ) { Text("Remover") }
            }
        }

        if (clip.durationUs > MotionPathPlanner.MAX_OFFSET_US) {
            Text(
                "Motion path manual está limitado a 10 minutos por clipe nesta versão.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("Falha")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
