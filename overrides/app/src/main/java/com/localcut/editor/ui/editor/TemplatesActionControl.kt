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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.history.DeleteReusableComponentCommand
import com.localcut.editor.domain.history.InstantiateReusableComponentCommand
import com.localcut.editor.domain.history.UpsertReusableComponentCommand
import com.localcut.editor.session.EditorAction

@Composable
fun TemplatesActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val preview by runtime.previewController.state.collectAsState()
    val project = session.project
    val playheadUs = preview.positionUs.coerceAtLeast(0L)
    val candidate = remember(project.designElements, project.reusableComponents) {
        DesignToolsUiPolicy.componentFromAllShapes(project)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Modelos reutilizáveis", style = MaterialTheme.typography.labelLarge)
        Text(
            "Salve as formas atuais como modelo e aplique cópias no playhead com Desfazer/Refazer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilledTonalButton(
            onClick = {
                val component = candidate ?: return@FilledTonalButton
                runtime.editorViewModel.dispatch(
                    EditorAction.Execute(UpsertReusableComponentCommand(component)),
                )
            },
            enabled = candidate != null,
        ) {
            Text("Salvar formas como modelo")
        }

        if (project.reusableComponents.isEmpty()) {
            Text(
                "Nenhum modelo salvo ainda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            project.reusableComponents.forEach { component ->
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${component.name} • ${component.elementIds.size} elemento(s)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    FilledTonalButton(
                        onClick = {
                            runtime.editorViewModel.dispatch(
                                EditorAction.Execute(
                                    InstantiateReusableComponentCommand(
                                        componentId = component.id,
                                        instancePrefix = DesignToolsUiPolicy.nextInstancePrefix(project, component.id),
                                        startUs = playheadUs,
                                    ),
                                ),
                            )
                        },
                    ) { Text("Aplicar") }
                    OutlinedButton(
                        onClick = {
                            runtime.editorViewModel.dispatch(
                                EditorAction.Execute(DeleteReusableComponentCommand(component.id)),
                            )
                        },
                    ) { Text("Remover") }
                }
            }
        }
    }
}
