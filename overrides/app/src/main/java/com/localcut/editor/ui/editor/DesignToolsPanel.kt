package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.design.SnapEngine
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import kotlin.math.roundToInt

@Composable
fun DesignToolsPanel(
    project: Project,
    playheadUs: Long,
    onAddElement: (DesignElement) -> Unit,
    onUpdateElement: (DesignElement) -> Unit,
    onDeleteElement: (String) -> Unit,
    onSetCanvas: (DesignCanvasSettings) -> Unit,
    onSetProjectCanvasSize: (Int, Int) -> Unit,
    onUpsertComponent: (ReusableComponent) -> Unit,
    onInstantiateComponent: (ReusableComponent, String, Long) -> Unit = { component, prefix, startUs ->
        DesignToolsUiPolicy.instantiateComponentElements(project, component, prefix, startUs).forEach(onAddElement)
    },
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
    val sections = DesignToolsUiPolicy.mobileSections()
    var activeSection by remember(project.id) { mutableStateOf(DesignToolsSection.ELEMENTS) }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Design", style = MaterialTheme.typography.labelLarge)
            Text(
                "Formas ${shapes.size} • Componentes ${project.reusableComponents.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEach { section ->
                    val label = when (section) {
                        DesignToolsSection.ELEMENTS -> "Elementos"
                        DesignToolsSection.CANVAS -> "Canvas"
                        DesignToolsSection.COMPONENTS -> "Componentes"
                    }
                    if (section == activeSection) {
                        FilledTonalButton(onClick = { activeSection = section }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { activeSection = section }) { Text(label) }
                    }
                }
            }

            when (activeSection) {
                DesignToolsSection.ELEMENTS -> {
                    DrawStrokePad(
                        project = project,
                        playheadUs = playheadUs,
                        onAddElement = onAddElement,
                        onDeleteElement = onDeleteElement,
                        modifier = Modifier.fillMaxWidth(),
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
                        DesignToolsUiPolicy.shapePresets().forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    onAddElement(
                                        DesignToolsUiPolicy.newShapePreset(
                                            project = project,
                                            preset = preset,
                                            playheadUs = playheadUs.coerceAtLeast(0L),
                                        ),
                                    )
                                },
                            ) { Text(preset.label) }
                        }
                    }

                    activeShape?.let { element ->
                        val shape = requireNotNull(element.shape)
                        var strokeWidth by remember(element.id, shape.strokeWidthPx) {
                            mutableStateOf(shape.strokeWidthPx.coerceIn(0.0, DesignToolsUiPolicy.MAX_STROKE_WIDTH_PX))
                        }
                        var cornerRadius by remember(element.id, shape.cornerRadiusPx) {
                            mutableStateOf(shape.cornerRadiusPx.coerceIn(0.0, DesignToolsUiPolicy.MAX_CORNER_RADIUS_PX))
                        }
                        var elementOpacity by remember(element.id, element.opacity) {
                            mutableStateOf(element.opacity.coerceIn(0.0, 1.0))
                        }

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
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.alignCenterX(element)) },
                            ) { Text("Centro X") }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.alignCenterY(element)) },
                            ) { Text("Centro Y") }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.alignCenter(element)) },
                            ) { Text("Centro") }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.bringToFront(project, element)) },
                            ) { Text("Frente") }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.sendToBack(project, element)) },
                            ) { Text("Trás") }
                            FilledTonalButton(
                                onClick = { onAddElement(DesignToolsUiPolicy.duplicateElement(project, element)) },
                            ) { Text("Duplicar forma") }
                            Button(onClick = { onDeleteElement(element.id) }) { Text("Excluir forma") }
                        }

                        DesignElementTransformControls(
                            project = project,
                            element = element,
                            onUpdateElement = onUpdateElement,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Opacidade ${(elementOpacity * 100.0).roundToInt()}%",
                                modifier = Modifier.width(104.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = elementOpacity.toFloat(),
                                onValueChange = { elementOpacity = it.toDouble() },
                                onValueChangeFinished = {
                                    onUpdateElement(DesignToolsUiPolicy.setElementOpacity(element, elementOpacity))
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..1f,
                            )
                        }

                        Text("Preenchimento", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            designColorPresets.forEach { preset ->
                                val selected = shape.fillArgb == preset.argb
                                if (selected) {
                                    FilledTonalButton(
                                        onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeFill(element, preset.argb)) },
                                    ) { Text(preset.label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeFill(element, preset.argb)) },
                                    ) { Text(preset.label) }
                                }
                            }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeFill(element, null)) },
                            ) { Text("Sem fill") }
                        }

                        Text("Contorno", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            designColorPresets.forEach { preset ->
                                val selected = shape.strokeArgb == preset.argb
                                if (selected) {
                                    FilledTonalButton(
                                        onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeStrokeColor(element, preset.argb)) },
                                    ) { Text(preset.label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeStrokeColor(element, preset.argb)) },
                                    ) { Text(preset.label) }
                                }
                            }
                            OutlinedButton(
                                onClick = { onUpdateElement(DesignToolsUiPolicy.setShapeStrokeColor(element, null)) },
                            ) { Text("Sem contorno") }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Borda ${strokeWidth.roundToInt()} px",
                                modifier = Modifier.width(104.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = strokeWidth.toFloat(),
                                onValueChange = { strokeWidth = it.toDouble() },
                                onValueChangeFinished = {
                                    onUpdateElement(DesignToolsUiPolicy.setShapeStrokeWidth(element, strokeWidth))
                                },
                                modifier = Modifier.weight(1f),
                                valueRange = 0f..DesignToolsUiPolicy.MAX_STROKE_WIDTH_PX.toFloat(),
                            )
                        }

                        if (shape.kind == ShapeKind.RECTANGLE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Cantos ${cornerRadius.roundToInt()} px",
                                    modifier = Modifier.width(104.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = cornerRadius.toFloat(),
                                    onValueChange = { cornerRadius = it.toDouble() },
                                    onValueChangeFinished = {
                                        onUpdateElement(DesignToolsUiPolicy.setShapeCornerRadius(element, cornerRadius))
                                    },
                                    modifier = Modifier.weight(1f),
                                    valueRange = 0f..DesignToolsUiPolicy.MAX_CORNER_RADIUS_PX.toFloat(),
                                )
                            }
                        }
                    }
                }

                DesignToolsSection.CANVAS -> {
                    Text("Formato do projeto", style = MaterialTheme.typography.labelMedium)
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
                    Text(
                        "${project.width}×${project.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text("Guias e alinhamento", style = MaterialTheme.typography.labelMedium)
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
                    }
                }

                DesignToolsSection.COMPONENTS -> {
                    Text("Componentes reutilizáveis", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                }
            }
        }
    }
}

private data class DesignColorPreset(val label: String, val argb: Int)

private val designColorPresets = listOf(
    DesignColorPreset("Branco", 0xFFFFFFFF.toInt()),
    DesignColorPreset("Preto", 0xFF111111.toInt()),
    DesignColorPreset("Vermelho", 0xFFFF3B30.toInt()),
    DesignColorPreset("Amarelo", 0xFFFFCC00.toInt()),
    DesignColorPreset("Verde", 0xFF34C759.toInt()),
    DesignColorPreset("Azul", 0xFF0A84FF.toInt()),
)

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
