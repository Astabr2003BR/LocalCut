package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.localcut.editor.mobile.RemoteMediaUrlPolicy

/** Manual fallback when ChatGPT mobile cannot expose the authenticated attachment tool. */
@Composable
fun RemoteMediaLinkImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val trimmed = url.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 4_096 &&
        RemoteMediaUrlPolicy.parseAndValidate(trimmed).isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar mídia por link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cole um link HTTPS direto para uma imagem, áudio ou vídeo.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(4_097) },
                    label = { Text("Endereço HTTPS da mídia") },
                    placeholder = { Text("https://exemplo.com/arquivo.mp3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = trimmed.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text("O arquivo será baixado para o armazenamento privado do LocalCut. " +
                    "Links internos do ChatGPT (sandbox:) e páginas de visualização não são arquivos diretos.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = valid) {
                Text("Importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
