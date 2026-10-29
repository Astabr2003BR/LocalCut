package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.Project

@Composable
fun CompositionControllerControls(
    project: Project,
    node: CompositionNode,
    onSetTransform: (LayerTransform3D) -> Unit,
    onSetCameraSettings: (CameraSettings) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    modifier: Modifier = Modifier,
    parentId: String? = null,
    parentTargets: List<CompositionNode> = emptyList(),
    onSetParent: (String?) -> Unit = {},
) {
    require(
        node.kind == CompositionNodeKind.NULL ||
            node.kind == CompositionNodeKind.GROUP ||
            node.kind == CompositionNodeKind.CAMERA,
    ) { "composition controller controls require NULL, GROUP or CAMERA" }

    var transform by remember(node.id, node.transform) { mutableStateOf(node.transform) }
    var camera by remember(node.id, node.camera) { mutableStateOf(node.camera ?: CameraSettings()) }
    val isCamera = node.kind == CompositionNodeKind.CAMERA

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "${controllerLabel(node.kind)} • ${node.id}",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )

        if (!isCamera) {
            Text("Parent do controlador", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (parentId == null) {
                    FilledTonalButton(onClick = {}, enabled = false) { Text("Sem parent") }
                } else {
                    OutlinedButton(onClick = { onSetParent(null) }) { Text("Desvincular") }
                }
                parentTargets.forEach { target ->
                    if (target.id == parentId) {
                        FilledTonalButton(onClick = {}, enabled = false) { Text(target.id) }
                    } else {
                        OutlinedButton(onClick = { onSetParent(target.id) }) { Text("Vincular ${target.id}") }
                    }
                }
            }
        }

        Text("X ${"%.0f".format(transform.x)} px", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = transform.x.toFloat().coerceIn(-project.width.toFloat(), project.width.toFloat()),
            onValueChange = { transform = transform.copy(x = it.toDouble()) },
            onValueChangeFinished = { onSetTransform(transform) },
            valueRange = -project.width.toFloat()..project.width.toFloat(),
        )

        Text("Y ${"%.0f".format(transform.y)} px", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = transform.y.toFloat().coerceIn(-project.height.toFloat(), project.height.toFloat()),
            onValueChange = { transform = transform.copy(y = it.toDouble()) },
            onValueChangeFinished = { onSetTransform(transform) },
            valueRange = -project.height.toFloat()..project.height.toFloat(),
        )

        Text("Z ${"%.0f".format(transform.z)}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = transform.z.toFloat().coerceIn(-SAFE_DEPTH_LIMIT, SAFE_DEPTH_LIMIT),
            onValueChange = { transform = transform.copy(z = it.toDouble()) },
            onValueChangeFinished = { onSetTransform(transform) },
            valueRange = -SAFE_DEPTH_LIMIT..SAFE_DEPTH_LIMIT,
        )

        Text("Escala ${"%.2f".format(transform.scale)}×", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = transform.scale.toFloat().coerceIn(0.1f, 4f),
            onValueChange = { transform = transform.copy(scale = it.toDouble()) },
            onValueChangeFinished = { onSetTransform(transform) },
            valueRange = 0.1f..4f,
        )

        Text("Rotação Z ${"%.0f".format(transform.rotationZDeg)}°", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = transform.rotationZDeg.toFloat().coerceIn(-180f, 180f),
            onValueChange = { transform = transform.copy(rotationZDeg = it.toDouble()) },
            onValueChangeFinished = { onSetTransform(transform) },
            valueRange = -180f..180f,
        )

        if (!isCamera) {
            Text("Opacidade ${"%.0f".format(transform.opacity * 100.0)}%", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Slider(
                value = transform.opacity.toFloat().coerceIn(0f, 1f),
                onValueChange = { transform = transform.copy(opacity = it.toDouble()) },
                onValueChangeFinished = { onSetTransform(transform) },
                valueRange = 0f..1f,
            )
        } else {
            Text("Distância focal ${"%.0f".format(camera.focalLength)}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Slider(
                value = camera.focalLength.toFloat().coerceIn(MIN_FOCAL_LENGTH, MAX_FOCAL_LENGTH),
                onValueChange = { camera = camera.copy(focalLength = it.toDouble()) },
                onValueChangeFinished = { onSetCameraSettings(camera) },
                valueRange = MIN_FOCAL_LENGTH..MAX_FOCAL_LENGTH,
                enabled = !camera.orthographic,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Câmera ortográfica", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Switch(
                    checked = camera.orthographic,
                    onCheckedChange = { enabled ->
                        camera = camera.copy(orthographic = enabled)
                        onSetCameraSettings(camera)
                    },
                )
            }
        }

        Text(
            "Rotação X/Y permanece desativada até o renderer de plano 3D.",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = {
                    transform = LayerTransform3D()
                    onSetTransform(transform)
                },
            ) { Text("Resetar") }
            OutlinedButton(
                onClick = onRemove,
                enabled = canRemove,
            ) { Text("Remover") }
        }
    }
}

private fun controllerLabel(kind: CompositionNodeKind): String = when (kind) {
    CompositionNodeKind.NULL -> "Null"
    CompositionNodeKind.GROUP -> "Grupo"
    CompositionNodeKind.CAMERA -> "Câmera"
    CompositionNodeKind.LAYER -> "Camada"
}

private const val SAFE_DEPTH_LIMIT = 250f
private const val MIN_FOCAL_LENGTH = 750f
private const val MAX_FOCAL_LENGTH = 3000f
