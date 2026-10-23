package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay

@Composable
internal fun TextOverlayManagerDialog(
    project: Project,
    playheadUs: Long,
    onEditRequested: (TextOverlayEditorModel) -> Unit,
    onDeleteRequested: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val canCreate = TextOverlayEditorModel.forNew(project, playheadUs) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Textos e legendas") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            TextOverlayEditorModel.forNew(project, playheadUs)?.let(onEditRequested)
                        },
                        enabled = canCreate,
                    ) {
                        Text("Novo texto")
                    }
                    FilledTonalButton(
                        onClick = {
                            TextOverlayEditorModel.forNewCaption(project, playheadUs)?.let(onEditRequested)
                        },
                        enabled = canCreate,
                    ) {
                        Text("Nova legenda")
                    }
                }
                if (!canCreate) {
                    Text(
                        "Adicione uma mídia visual antes de criar texto ou legenda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (project.textOverlays.isEmpty()) {
                    Text(
                        "Nenhum texto ou legenda no projeto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                project.textOverlays.sortedByDescending { it.zIndex }.forEach { overlay ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (overlay.caption != null) "Legenda • ${overlay.text}" else overlay.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${formatSeconds(overlay.startUs)} s → ${formatSeconds(overlay.startUs + overlay.durationUs)} s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onEditRequested(TextOverlayEditorModel.forEdit(overlay)) },
                                ) { Text("Editar") }
                                Button(onClick = { onDeleteRequested(overlay.id) }) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

@Composable
internal fun TextOverlayEditorDialog(
    project: Project,
    model: TextOverlayEditorModel,
    onModelChange: (TextOverlayEditorModel) -> Unit,
    onSave: (TextOverlay, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = model.draft
    val saveOverlay = model.overlayForSave()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    model.isNew && model.isCaption -> "Nova legenda"
                    model.isNew -> "Novo texto"
                    model.isCaption -> "Editar legenda"
                    else -> "Editar texto"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = { onModelChange(model.withText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (model.isCaption) "Legenda" else "Texto") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    "Início: ${formatSeconds(draft.startUs)} s • Duração: ${formatSeconds(draft.durationUs)} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextEditorChoiceRow(
                    label = "Duração",
                    options = listOf(500_000L, 1_000_000L, 3_000_000L, 5_000_000L),
                    selected = draft.durationUs,
                    optionLabel = { "${formatSeconds(it)} s" },
                    onSelect = { onModelChange(model.withDuration(project, it)) },
                )
                TextEditorChoiceRow(
                    label = "Tamanho",
                    options = listOf(32.0, 48.0, 64.0, 80.0, 96.0),
                    selected = draft.style.fontSizePx,
                    optionLabel = { "${it.toInt()} px" },
                    onSelect = { onModelChange(model.withFontSizePx(it)) },
                )
                TextEditorChoiceRow(
                    label = "Alinhamento",
                    options = TextHorizontalAlignment.entries,
                    selected = draft.style.alignment,
                    optionLabel = {
                        when (it) {
                            TextHorizontalAlignment.START -> "Esquerda"
                            TextHorizontalAlignment.CENTER -> "Centro"
                            TextHorizontalAlignment.END -> "Direita"
                        }
                    },
                    onSelect = { onModelChange(model.withAlignment(it)) },
                )
                TextEditorChoiceRow(
                    label = "Cor",
                    options = listOf(
                        "Branco" to 0xFFFFFFFF.toInt(),
                        "Amarelo" to 0xFFFFFF00.toInt(),
                        "Vermelho" to 0xFFFF4040.toInt(),
                        "Azul" to 0xFF40A0FF.toInt(),
                        "Preto" to 0xFF000000.toInt(),
                    ),
                    selected = colorOptionsSelected(draft.style.fillArgb),
                    optionLabel = { it.first },
                    onSelect = { onModelChange(model.withFillArgb(it.second)) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { saveOverlay?.let { onSave(it, model.isNew) } },
                enabled = saveOverlay != null,
            ) { Text("Salvar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun <T> TextEditorChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                if (option == selected) {
                    FilledTonalButton(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
                } else {
                    OutlinedButton(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
                }
            }
        }
    }
}

private fun colorOptionsSelected(fillArgb: Int): Pair<String, Int> = when (fillArgb) {
    0xFFFFFF00.toInt() -> "Amarelo" to fillArgb
    0xFFFF4040.toInt() -> "Vermelho" to fillArgb
    0xFF40A0FF.toInt() -> "Azul" to fillArgb
    0xFF000000.toInt() -> "Preto" to fillArgb
    else -> "Branco" to 0xFFFFFFFF.toInt()
}

private fun formatSeconds(timeUs: Long): String {
    val hundredths = (timeUs.coerceAtLeast(0L) + 5_000L) / 10_000L
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
