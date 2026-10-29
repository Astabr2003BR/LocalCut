package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

@Composable
fun LayerPanel(
    project: Project,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit,
    onSetCompositionGraph: (CompositionGraph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualTracks = project.tracks.filter { it.type == TrackType.VISUAL }.sortedByDescending { it.order }
    val visualClips = visualTracks.flatMap { track -> track.clips.map { clip -> track to clip } }
    val graph = project.compositionGraph
    val parentTargets = LayerPanelUiPolicy.parentTargets(graph, selectedClipId)
    val selectedParentId = selectedClipId?.let { LayerPanelUiPolicy.layerParentId(graph, it) }
    val cameraNodes = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
    val staticCamera = cameraNodes.singleOrNull()
    val canAddStaticCamera = cameraNodes.isEmpty() && graph.cameraCuts.isEmpty()
    val controllerNodes = graph.nodes.filter {
        it.kind == CompositionNodeKind.NULL ||
            it.kind == CompositionNodeKind.GROUP ||
            it.kind == CompositionNodeKind.CAMERA
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Camadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Vídeo/imagem ${visualClips.size} • Texto ${project.textOverlays.size} • Design ${project.designElements.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onSetCompositionGraph(LayerPanelUiPolicy.addNull(graph).graph)
                    },
                ) {
                    Text("+ Null")
                }
                OutlinedButton(
                    onClick = {
                        onSetCompositionGraph(LayerPanelUiPolicy.addGroup(graph).graph)
                    },
                ) {
                    Text("+ Grupo")
                }
                OutlinedButton(
                    onClick = {
                        onSetCompositionGraph(LayerPanelUiPolicy.addStaticCamera(graph).graph)
                    },
                    enabled = canAddStaticCamera,
                ) {
                    Text(
                        when {
                            cameraNodes.isEmpty() -> "+ Câmera"
                            cameraNodes.size == 1 -> "Câmera ativa"
                            else -> "${cameraNodes.size} câmeras"
                        },
                    )
                }
            }

            Text(
                text = when {
                    staticCamera != null -> "Câmera estática: ${staticCamera.id} • preview/export compartilham a mesma composição."
                    cameraNodes.size > 1 -> "Projeto com múltiplas câmeras: criação estática desativada; camera cuts não são editáveis neste painel."
                    else -> "Parenting: crie um Null ou Grupo para controlar camadas em conjunto."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            selectedClipId?.let { clipId ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Parent da camada selecionada", style = MaterialTheme.typography.labelMedium)
                        Text(
                            selectedParentId ?: "Sem parent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (selectedParentId == null) {
                                FilledTonalButton(onClick = {}, enabled = false) { Text("Sem parent") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onSetCompositionGraph(
                                            LayerPanelUiPolicy.setClipParent(graph, clipId, null),
                                        )
                                    },
                                ) { Text("Desvincular") }
                            }

                            parentTargets.forEach { target ->
                                if (selectedParentId == target.id) {
                                    FilledTonalButton(onClick = {}, enabled = false) { Text(target.id) }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            onSetCompositionGraph(
                                                LayerPanelUiPolicy.setClipParent(graph, clipId, target.id),
                                            )
                                        },
                                    ) { Text("Vincular ${target.id}") }
                                }
                            }

                            if (selectedParentId == null) {
                                OutlinedButton(
                                    onClick = {
                                        onSetCompositionGraph(
                                            LayerPanelUiPolicy.addNullParentForClip(graph, clipId).graph,
                                        )
                                    },
                                ) { Text("+ Null pai") }
                                OutlinedButton(
                                    onClick = {
                                        onSetCompositionGraph(
                                            LayerPanelUiPolicy.addGroupParentForClip(graph, clipId).graph,
                                        )
                                    },
                                ) { Text("+ Grupo pai") }
                            }
                        }
                    }
                }
            }

            if (controllerNodes.isNotEmpty()) {
                Text("Controladores", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                controllerNodes.forEach { node ->
                    val canParentController =
                        node.kind == CompositionNodeKind.NULL || node.kind == CompositionNodeKind.GROUP
                    val controllerParentId = if (canParentController) {
                        LayerPanelUiPolicy.controllerParentId(graph, node.id)
                    } else {
                        null
                    }
                    val controllerParentTargets = if (canParentController) {
                        LayerPanelUiPolicy.controllerParentTargets(graph, node.id)
                    } else {
                        emptyList()
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        CompositionControllerControls(
                            project = project,
                            node = node,
                            onSetTransform = { transform ->
                                onSetCompositionGraph(
                                    LayerPanelUiPolicy.setControllerTransform(graph, node.id, transform),
                                )
                            },
                            onSetCameraSettings = { settings ->
                                onSetCompositionGraph(
                                    LayerPanelUiPolicy.setCameraSettings(graph, node.id, settings),
                                )
                            },
                            onRemove = {
                                onSetCompositionGraph(
                                    LayerPanelUiPolicy.removeControllerNode(graph, node.id),
                                )
                            },
                            canRemove = node.kind != CompositionNodeKind.CAMERA ||
                                graph.cameraCuts.none { it.cameraNodeId == node.id },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            parentId = controllerParentId,
                            parentTargets = controllerParentTargets,
                            onSetParent = { parentId ->
                                onSetCompositionGraph(
                                    LayerPanelUiPolicy.setControllerParent(graph, node.id, parentId),
                                )
                            },
                        )
                    }
                }
            }

            if (visualClips.isEmpty()) {
                Text(
                    "Nenhuma camada visual no projeto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visualClips.forEach { (track, clip) ->
                val asset = project.assets.firstOrNull { it.id == clip.assetId }
                val visible = LayerPanelUiPolicy.isClipVisible(graph, clip.id)
                val locked = LayerPanelUiPolicy.isClipLocked(graph, clip.id)
                val parentId = LayerPanelUiPolicy.layerParentId(graph, clip.id)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(asset?.displayName ?: clip.id, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append("Track ${track.order} • ${if (visible) "visível" else "oculta"} • ${if (locked) "bloqueada" else "desbloqueada"}")
                                parentId?.let { append(" • parent $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (selectedClipId == clip.id) {
                                FilledTonalButton(onClick = {}, enabled = false) { Text("Selecionada") }
                            } else {
                                OutlinedButton(onClick = { onSelectClip(clip.id) }) { Text("Selecionar") }
                            }
                            OutlinedButton(
                                onClick = {
                                    onSetCompositionGraph(
                                        LayerPanelUiPolicy.setClipVisible(
                                            graph,
                                            clip.id,
                                            visible = !visible,
                                        ),
                                    )
                                },
                            ) {
                                Text(if (visible) "Ocultar" else "Mostrar")
                            }
                            if (locked) {
                                FilledTonalButton(
                                    onClick = {
                                        onSetCompositionGraph(
                                            LayerPanelUiPolicy.setClipLocked(
                                                graph,
                                                clip.id,
                                                locked = false,
                                            ),
                                        )
                                    },
                                ) { Text("Desbloquear") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onSetCompositionGraph(
                                            LayerPanelUiPolicy.setClipLocked(
                                                graph,
                                                clip.id,
                                                locked = true,
                                            ),
                                        )
                                    },
                                ) { Text("Bloquear") }
                            }
                        }
                    }
                }
            }
        }
    }
}
