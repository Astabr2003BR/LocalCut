package com.localcut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        color = Color(0xFF0D0D0F),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tools.forEach { tool ->
                val target = EditorToolCatalog.actionTarget(tool.id)
                val active = tool.id == activeTool && tool.enabled
                val itemColor = when {
                    !tool.enabled -> Color.Transparent
                    active -> Color(0xFF26262B)
                    else -> Color.Transparent
                }
                val contentColor = when {
                    !tool.enabled -> Color(0xFF66666B)
                    active -> Color.White
                    else -> Color(0xFFE7E7EA)
                }

                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .background(itemColor, RoundedCornerShape(10.dp))
                        .clickable(enabled = tool.enabled) { onToolSelected(target) }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = toolGlyph(tool.id),
                        color = contentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = tool.label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun toolGlyph(id: EditorToolId): String = when (id) {
    EditorToolId.MEDIA -> "▣"
    EditorToolId.AUDIO -> "♫"
    EditorToolId.TEXT -> "T"
    EditorToolId.OVERLAY -> "◫"
    EditorToolId.EDIT -> "✂"
    EditorToolId.EFFECTS -> "✦"
    EditorToolId.COLOR -> "◐"
    EditorToolId.SPEED -> "⏱"
    EditorToolId.MASK -> "◉"
    EditorToolId.CHROMA -> "◆"
    EditorToolId.ANIMATION -> "◇"
    EditorToolId.LAYERS -> "▱"
    EditorToolId.DESIGN -> "+"
    EditorToolId.CANVAS -> "▢"
    EditorToolId.CAPTIONS -> "CC"
    EditorToolId.TRACKING -> "⌖"
    EditorToolId.STABILIZE -> "≋"
    EditorToolId.CAMERA -> "◎"
    EditorToolId.TEMPLATES -> "▤"
}
