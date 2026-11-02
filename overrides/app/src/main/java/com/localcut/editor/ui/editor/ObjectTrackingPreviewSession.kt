package com.localcut.editor.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ObjectTrackingPreviewSessionState(
    val enabled: Boolean = false,
    val selection: PreviewSelectionRect? = null,
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
)

object ObjectTrackingPreviewSession {
    private val mutableState = MutableStateFlow(ObjectTrackingPreviewSessionState())
    val state: StateFlow<ObjectTrackingPreviewSessionState> = mutableState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        val current = mutableState.value
        mutableState.value = current.copy(
            enabled = enabled,
            selection = if (enabled) current.selection else null,
        )
    }

    fun setSelection(selection: PreviewSelectionRect?) {
        val current = mutableState.value
        mutableState.value = if (current.enabled) {
            current.copy(selection = selection)
        } else {
            current.copy(selection = null)
        }
    }

    fun updateViewport(widthPx: Int, heightPx: Int) {
        val safeWidth = widthPx.coerceAtLeast(0)
        val safeHeight = heightPx.coerceAtLeast(0)
        val current = mutableState.value
        val changed = current.viewportWidthPx != safeWidth || current.viewportHeightPx != safeHeight
        val valid = safeWidth > 0 && safeHeight > 0
        mutableState.value = current.copy(
            viewportWidthPx = safeWidth,
            viewportHeightPx = safeHeight,
            selection = if (changed || !valid) null else current.selection,
        )
    }

    internal fun resetForTest() {
        mutableState.value = ObjectTrackingPreviewSessionState()
    }
}
