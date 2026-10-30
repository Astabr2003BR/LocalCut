package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
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
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.session.EditorAction

@Composable
fun FlipActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val clip = session.selectedClipId?.let(session.project::findClip) ?: return
    val asset = session.project.assets.firstOrNull { it.id == clip.assetId } ?: return
    if (asset.type == AssetType.AUDIO) return

    val horizontal = FlipToolsUiPolicy.isHorizontalEnabled(clip.effects)
    val vertical = FlipToolsUiPolicy.isVerticalEnabled(clip.effects)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (horizontal) {
            FilledTonalButton(
                onClick = {
                    runtime.editorViewModel.dispatch(
                        EditorAction.SetSelectedEffects(FlipToolsUiPolicy.toggleHorizontal(clip.effects)),
                    )
                },
            ) { Text("Espelhar H ✓") }
        } else {
            OutlinedButton(
                onClick = {
                    runtime.editorViewModel.dispatch(
                        EditorAction.SetSelectedEffects(FlipToolsUiPolicy.toggleHorizontal(clip.effects)),
                    )
                },
            ) { Text("Espelhar H") }
        }

        if (vertical) {
            FilledTonalButton(
                onClick = {
                    runtime.editorViewModel.dispatch(
                        EditorAction.SetSelectedEffects(FlipToolsUiPolicy.toggleVertical(clip.effects)),
                    )
                },
            ) { Text("Espelhar V ✓") }
        } else {
            OutlinedButton(
                onClick = {
                    runtime.editorViewModel.dispatch(
                        EditorAction.SetSelectedEffects(FlipToolsUiPolicy.toggleVertical(clip.effects)),
                    )
                },
            ) { Text("Espelhar V") }
        }

        OutlinedButton(
            onClick = {
                runtime.editorViewModel.dispatch(
                    EditorAction.SetSelectedEffects(FlipToolsUiPolicy.clear(clip.effects)),
                )
            },
            enabled = horizontal || vertical,
        ) { Text("Reset Flip") }
    }
}
