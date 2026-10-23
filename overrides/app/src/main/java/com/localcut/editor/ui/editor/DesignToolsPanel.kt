package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.design.SnapEngine
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind

@Composable
fun DesignToolsPanel(
    project: Project,
    playheadUs: Long,
    onAddElement: (DesignElement) -> Unit,
    onUpdateElement: (DesignElement) -> Unit,
    onDeleteElement: (String) -> Unit,
    onSetCanvas: (DesignCanvasSettings) -> Unit,
    onUpsertComponent: (ReusableComponent) -> Unit,
    onInstantiateComponent: (ReusableComponent, String, Long) -> Unit,
    onDeleteComponent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = project.designElements.filter { it.shape != null }
    val activeShape = shapes.lastOrNull()
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Design / Canva", style = MaterialTheme.typography.labelLarge)
            Text(
                "Formas ${shapes.size} • Componentes ${project.reusableComponents.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    ShapeKind.RECTANGLE to "Retângulo",
                    ShapeKind.ELLIPSE to "Elipse",
                    ShapeKind.LINE to "Linha",
                    ShapeKind.PATH to "Triângulo",
                ).forEach { (kind, label) ->
                    FilledTonalButton(
                        onClick = {
                            onAddElement(
                                DesignToolsUiPolicy.newShape(
                                    project = project,
                                    kind = kind,
                                    playheadUs = playheadUs.coerceAtLeast(0L),
                                ),
                            )
                        },
                    ) { Text(label) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (centerGuidesEnabled) {
                    FilledTonalButton(
                        onClick = { onSetCanvas(DesignToolsUiPolicy.toggleCenterGuides(project.designCanvas)) },
                    ) { Text("Guias ON") }
                } else {
                    OutlinedButton(
                        onClick = { onSetCanvas(DesignToolsUiPolicy.toggleCenterGuides(project.designCanvas)) },
                    ) { Text("Guias centro") }
                }
                FilledTonalButton(
                    onClick = {
                        DesignToolsUiPolicy.componentFromAllShapes(project)?.let(onUpsertComponent)
                    },
                    enabled = shapes.isNotEmpty(),
                ) { Text("Salvar componente") }
                project.reusableComponents.lastOrNull()?.let { component ->
                    FilledTonalButton(
                        onClick = {
                            onInstantiateComponent(
                                component,
                                DesignToolsUiPolicy.nextInstancePrefix(project, component.id),
                                playheadUs.coerceAtLeast(0L),
                            )
                        },
                    ) { Text("Aplicar ${component.name}") }
                    OutlinedButton(onClick = { onDeleteComponent(component.id) }) {
                        Text("Remover ${component.name}")
                    }
                }
            }

            activeShape?.let { element ->
                Text(
                    "Editar ${element.id}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(onClick = { onUpdateElement(nudge(project, element, -40.0, 0.0)) }) { Text("←") }
                    OutlinedButton(onClick = { onUpdateElement(nudge(project, element, 40.0, 0.0)) }) { Text("→") }
                    OutlinedButton(onClick = { onUpdateElement(nudge(project, element, 0.0, -40.0)) }) { Text("↑") }
                    OutlinedButton(onClick = { onUpdateElement(nudge(project, element, 0.0, 40.0)) }) { Text("↓") }
                    OutlinedButton(
                        onClick = {
                            onUpdateElement(
                                element.copy(transform = element.transform.copy(scale = (element.transform.scale - 0.1).coerceAtLeast(0.1))),
                            )
                        },
                    ) { Text("Menor") }
                    OutlinedButton(
                        onClick = {
                            onUpdateElement(element.copy(transform = element.transform.copy(scale = element.transform.scale + 0.1)))
                        },
                    ) { Text("Maior") }
                    OutlinedButton(
                        onClick = {
                            onUpdateElement(element.copy(transform = element.transform.copy(rotationDeg = element.transform.rotationDeg + 15.0)))
                        },
                    ) { Text("↻ 15°") }
                    Button(onClick = { onDeleteElement(element.id) }) { Text("Excluir forma") }
                }
            }
        }
    }
}

private fun nudge(
    project: Project,
    element: DesignElement,
    dx: Double,
    dy: Double,
): DesignElement {
    val requestedX = element.transform.x + dx
    val requestedY = element.transform.y + dy
    val normalizedX = 0.5 + requestedX / project.width.toDouble()
    val normalizedY = 0.5 + requestedY / project.height.toDouble()
    val snappedX = SnapEngine.snapX(normalizedX, project.designCanvas)
    val snappedY = SnapEngine.snapY(normalizedY, project.designCanvas)
    return element.copy(
        transform = element.transform.copy(
            x = (snappedX - 0.5) * project.width.toDouble(),
            y = (snappedY - 0.5) * project.height.toDouble(),
        ),
    )
}
