package com.localcut.editor.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TikTokPostDraftDialog(
    draft: TikTokPostDraft,
    onChange: (TikTokPostDraft) -> Unit,
    copyOnShare: Boolean,
    onCopy: (String) -> Unit,
    onCopyOnShareChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preparar publicação") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Edite os textos e copie para o TikTok. Nada é publicado automaticamente.")
                TextButton(onClick = {
                    val preset = TikTokPostDraft.mangaSuspense()
                    // Fill missing fields only; never overwrite the user's writing.
                    onChange(draft.copy(
                        title = draft.title.ifBlank { preset.title },
                        description = draft.description.ifBlank { preset.description },
                        hashtags = draft.hashtags.ifBlank { preset.hashtags },
                    ))
                }) { Text("Sugestão: mangá de suspense") }
                OutlinedTextField(
                    value = draft.title, onValueChange = { onChange(draft.copy(title = it)) },
                    label = { Text("Título / capítulo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description, onValueChange = { onChange(draft.copy(description = it)) },
                    label = { Text("Descrição") }, minLines = 3, maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.hashtags, onValueChange = { onChange(draft.copy(hashtags = it)) },
                    label = { Text("Hashtags") }, minLines = 2, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Sugestão editorial: até 5 hashtags relevantes. Não são tendências verificadas em tempo real.", style = MaterialTheme.typography.bodySmall)
                Text(draft.normalizedHashtags(), style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = copyOnShare, onCheckedChange = onCopyOnShareChange)
                    Text("Copiar tudo ao compartilhar as fotos", modifier = Modifier.padding(top = 12.dp))
                }
                TextButton(enabled = draft.title.isNotBlank(), onClick = { onCopy(draft.title.trim()) }) { Text("Copiar título") }
                TextButton(enabled = draft.caption().isNotBlank(), onClick = { onCopy(draft.caption()) }) { Text("Copiar descrição + hashtags") }
                TextButton(enabled = draft.fullText().isNotBlank(), onClick = { onCopy(draft.fullText()) }) { Text("Copiar tudo") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Concluir") } },
    )
}
