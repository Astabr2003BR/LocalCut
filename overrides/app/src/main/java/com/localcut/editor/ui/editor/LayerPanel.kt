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
    val parentTargets = LayerPanelUiPolicy.parentTargets(graph)
    val selectedParentId = selectedClipId?.let { LayerPanelUiPolicy.layerParentId(graph, it) }
    val staticCamera = graph.nodes.singleOrNull { it.kind == CompositionNodeKind.CAMERA }
    val canAddStaticCamera = staticCamera == null && graph.cameraCuts.isEmpty()

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
                        onSetCompositionGraph(LayerPanelUiPolicy.addStaticCamera(graph).graph)
                    },
                    enabled = canAddStaticCamera,
                ) {
                    Text(if (staticCamera == null) "+ Câmera" else "Câmera ativa")
                }
            }

            Text(
                text = if (staticCamera != null) {
                    "Câmera estática: ${staticCamera.id} • parenting 2D/2.5D ativo"
                } else {
                    "Parenting: use Null para mover/rotacionar/escalar camadas em conjunto."
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
                                FilledTonalButton(onClick = {}) { Text("Sem parent") }
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
                                    FilledTonalButton(onClick = {}) { Text(target.id) }
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
                        }
                        if (parentTargets.isEmpty()) {
                            Text(
                                "Crie um Null para usar parenting nesta camada.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                FilledTonalButton(onClick = { onSelectClip(clip.id) }) { Text("Selecionada") }
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
