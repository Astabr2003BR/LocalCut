package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.Project

@Composable
fun CanvasToolsPanel(
    project: Project,
    onSetCanvas: (DesignCanvasSettings) -> Unit,
    onSetProjectCanvasSize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerGuidesEnabled = project.designCanvas.guides.any {
        it.axis == GuideAxis.VERTICAL && it.positionNormalized == 0.5
    } && project.designCanvas.guides.any {
        it.axis == GuideAxis.HORIZONTAL && it.positionNormalized == 0.5
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Formato do projeto", style = MaterialTheme.typography.labelLarge)
            Text(
                "${project.width}×${project.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DesignToolsUiPolicy.canvasPresets().forEach { preset ->
                    val selected = DesignToolsUiPolicy.isCanvasPresetActive(project, preset)
                    if (selected) {
                        FilledTonalButton(
                            onClick = { onSetProjectCanvasSize(preset.width, preset.height) },
                        ) { Text("${preset.label} ✓") }
                    } else {
                        OutlinedButton(
                            onClick = { onSetProjectCanvasSize(preset.width, preset.height) },
                        ) { Text(preset.label) }
                    }
                }
            }

            Text("Guias e alinhamento", style = MaterialTheme.typography.labelMedium)
            if (centerGuidesEnabled) {
                FilledTonalButton(
                    onClick = { onSetCanvas(DesignToolsUiPolicy.toggleCenterGuides(project.designCanvas)) },
                ) { Text("Guias ON") }
            } else {
                OutlinedButton(
                    onClick = { onSetCanvas(DesignToolsUiPolicy.toggleCenterGuides(project.designCanvas)) },
                ) { Text("Guias centro") }
            }
        }
    }
}
