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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var workspace by remember { mutableStateOf(EditorWorkspace.EDIT) }
    var expanded by remember(selection, workspace) { mutableStateOf(false) }
    val tools = EditorWorkspacePolicy.toolsFor(workspace, selection)
    val layout = EditorBottomToolRailPolicy.layout(
        tools = tools,
        expanded = expanded,
        activeTool = activeTool,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0D0D0F),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            WorkspaceRail(
                current = workspace,
                onSelected = { selected ->
                    if (selected == workspace) return@WorkspaceRail
                    val nextActiveTool = EditorWorkspacePolicy.activeToolAfterWorkspaceChange(
                        currentWorkspace = workspace,
                        nextWorkspace = selected,
                        activeTool = activeTool,
                    )
                    if (activeTool != null && nextActiveTool == null) {
                        onToolSelected(activeTool)
                    }
                    workspace = selected
                    expanded = false
                },
            )

            if (activeTool == EditorToolId.SPEED && selection == EditorSelectionKind.VIDEO) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ReverseMediaActionControl(
                        buttonLabel = "Reverse",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FreezeFrameActionControl(modifier = Modifier.fillMaxWidth())
                }
            }

            if (activeTool == EditorToolId.LAYERS) {
                CameraCutActionControl(modifier = Modifier.fillMaxWidth())
            }

            if (activeTool == EditorToolId.DRAW) {
                VectorLassoControl(modifier = Modifier.fillMaxWidth())
            }

            if (
                activeTool == EditorToolId.ANIMATION &&
                selection in setOf(EditorSelectionKind.VIDEO, EditorSelectionKind.IMAGE)
            ) {
                MotionPathActionControl(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                layout.visible.forEach { tool ->
                    val target = EditorToolCatalog.actionTarget(tool.id)
                    val active = target == activeTool
                    ToolRailItem(
                        glyph = toolGlyph(tool.id),
                        label = tool.label,
                        active = active,
                        onClick = { onToolSelected(target) },
                    )
                }

                if (layout.hasMore) {
                    ToolRailItem(
                        glyph = "⋯",
                        label = "Mais +${layout.hiddenCount}",
                        active = false,
                        onClick = { expanded = true },
                    )
                } else if (expanded && tools.size > EditorBottomToolRailPolicy.COLLAPSED_LIMIT) {
                    ToolRailItem(
                        glyph = "‹",
                        label = "Menos",
                        active = false,
                        onClick = { expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRail(
    current: EditorWorkspace,
    onSelected: (EditorWorkspace) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorWorkspace.entries.forEach { workspace ->
            val selected = workspace == current
            Text(
                text = workspace.label,
                color = if (selected) Color.Black else Color(0xFFD0D0D5),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(
                        color = if (selected) Color(0xFF20D5D2) else Color(0xFF1A1A1E),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelected(workspace) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToolRailItem(
    glyph: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val itemColor = if (active) Color(0xFF26262B) else Color.Transparent
    val contentColor = if (active) Color.White else Color(0xFFE7E7EA)

    Column(
        modifier = Modifier
            .width(68.dp)
            .background(itemColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = glyph,
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 1,
        )
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
    EditorToolId.DRAW -> "✎"
    EditorToolId.CANVAS -> "▢"
    EditorToolId.CAPTIONS -> "CC"
    EditorToolId.TRACKING -> "⌖"
    EditorToolId.STABILIZE -> "≋"
    EditorToolId.CAMERA -> "◎"
    EditorToolId.TEMPLATES -> "▤"
}
