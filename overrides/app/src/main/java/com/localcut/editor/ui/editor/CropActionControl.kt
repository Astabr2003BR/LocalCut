package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
fun CropActionControl(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val clip = session.selectedClipId?.let(session.project::findClip)
    val asset = clip?.let { selected ->
        session.project.assets.firstOrNull { it.id == selected.assetId }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (clip == null || asset?.type !in setOf(AssetType.VIDEO, AssetType.IMAGE)) {
            Text(
                "Selecione um vídeo ou imagem para ajustar o recorte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val capturedClipId = clip.id
        val capturedCompositing = clip.compositing
        CropManualControls(
            settings = capturedCompositing,
            onSetCompositing = { updated ->
                val current = runtime.editorViewModel.sessionState.value
                val currentClip = current.project.findClip(capturedClipId)
                if (
                    current.selectedClipId == capturedClipId &&
                    currentClip?.compositing == capturedCompositing
                ) {
                    runtime.editorViewModel.dispatch(EditorAction.SetSelectedCompositing(updated))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
