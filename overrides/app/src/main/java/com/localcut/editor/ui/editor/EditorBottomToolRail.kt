package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun EditorBottomToolRail(
    selection: EditorSelectionKind,
    activeTool: EditorToolId?,
    onToolSelected: (EditorToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = EditorToolCatalog.primaryToolsFor(selection)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tools.forEach { tool ->
                if (tool.id == activeTool && tool.enabled) {
                    FilledTonalButton(onClick = { onToolSelected(tool.id) }) {
                        Text(tool.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onToolSelected(tool.id) },
                        enabled = tool.enabled,
                    ) {
                        Text(tool.label)
                    }
                }
            }
        }
    }
}
