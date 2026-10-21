package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay

data class TextOverlayEditorModel(
    val draft: TextOverlay,
    private val originalId: String?,
) {
    val isNew: Boolean get() = originalId == null

    fun withText(text: String): TextOverlayEditorModel =
        copy(draft = draft.copy(text = text))

    fun withFontSizePx(fontSizePx: Double): TextOverlayEditorModel {
        require(fontSizePx.isFinite() && fontSizePx > 0.0) { "font size must be positive" }
        return copy(draft = draft.copy(style = draft.style.copy(fontSizePx = fontSizePx)))
    }

    fun withAlignment(alignment: TextHorizontalAlignment): TextOverlayEditorModel =
        copy(draft = draft.copy(style = draft.style.copy(alignment = alignment)))

    fun withFillArgb(fillArgb: Int): TextOverlayEditorModel =
        copy(draft = draft.copy(style = draft.style.copy(fillArgb = fillArgb)))

    fun withDuration(project: Project, requestedDurationUs: Long): TextOverlayEditorModel {
        val updated = TextOverlayUiPolicy.withDuration(project, draft, requestedDurationUs) ?: return this
        return copy(draft = updated)
    }

    fun overlayForSave(): TextOverlay? = draft.takeIf { it.text.isNotBlank() }

    companion object {
        fun forNew(project: Project, playheadUs: Long): TextOverlayEditorModel? =
            TextOverlayUiPolicy.createNew(project, playheadUs, "Texto")?.let { overlay ->
                TextOverlayEditorModel(
                    draft = overlay.copy(text = ""),
                    originalId = null,
                )
            }

        fun forEdit(overlay: TextOverlay): TextOverlayEditorModel =
            TextOverlayEditorModel(draft = overlay, originalId = overlay.id)
    }
}
