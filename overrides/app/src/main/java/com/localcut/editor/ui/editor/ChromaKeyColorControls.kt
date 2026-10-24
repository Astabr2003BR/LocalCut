package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.CompositingSettings

@Composable
fun ChromaKeyColorControls(
    settings: CompositingSettings,
    onSetCompositing: (CompositingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!settings.chromaKey.enabled) return
    val selected = ChromaToolsUiPolicy.selectedPreset(settings)
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Cor chave")
        ChromaKeyColorPreset.entries.forEach { preset ->
            val label = ChromaToolsUiPolicy.label(preset)
            if (selected == preset) {
                FilledTonalButton(
                    onClick = { onSetCompositing(ChromaToolsUiPolicy.setKeyColor(settings, preset)) },
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSetCompositing(ChromaToolsUiPolicy.setKeyColor(settings, preset)) },
                ) { Text(label) }
            }
        }
    }
}
