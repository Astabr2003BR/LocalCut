package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformValue

@Composable
fun DesignElementTransformControls(
    project: Project,
    element: DesignElement,
    onUpdateElement: (DesignElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(element.id, element.transform) { mutableStateOf(element.transform) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Transformar", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text("X ${"%.0f".format(draft.x)} px", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = draft.x.toFloat().coerceIn(-project.width.toFloat(), project.width.toFloat()),
            onValueChange = { draft = draft.copy(x = it.toDouble()) },
            onValueChangeFinished = { onUpdateElement(element.copy(transform = draft)) },
            valueRange = -project.width.toFloat()..project.width.toFloat(),
        )
        Text("Y ${"%.0f".format(draft.y)} px", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = draft.y.toFloat().coerceIn(-project.height.toFloat(), project.height.toFloat()),
            onValueChange = { draft = draft.copy(y = it.toDouble()) },
            onValueChangeFinished = { onUpdateElement(element.copy(transform = draft)) },
            valueRange = -project.height.toFloat()..project.height.toFloat(),
        )
        Text("Escala ${"%.2f".format(draft.scale)}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = draft.scale.toFloat().coerceIn(0.1f, 4f),
            onValueChange = { draft = draft.copy(scale = it.toDouble()) },
            onValueChangeFinished = { onUpdateElement(element.copy(transform = draft)) },
            valueRange = 0.1f..4f,
        )
        Text("Rotação ${"%.0f".format(draft.rotationDeg)}°", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = draft.rotationDeg.toFloat().coerceIn(-180f, 180f),
            onValueChange = { draft = draft.copy(rotationDeg = it.toDouble()) },
            onValueChangeFinished = { onUpdateElement(element.copy(transform = draft)) },
            valueRange = -180f..180f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = {
                draft = TransformValue()
                onUpdateElement(element.copy(transform = draft))
            }) { Text("Resetar transformação") }
        }
    }
}
