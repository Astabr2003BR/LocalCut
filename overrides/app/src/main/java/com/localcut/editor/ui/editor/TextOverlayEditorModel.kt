package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CaptionMetadata
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextAnimationDescriptor
import com.localcut.editor.domain.model.TextAnimationKind
import com.localcut.editor.domain.model.TextBackgroundStyle
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextShadowStyle
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue

data class TextOverlayEditorModel(
    val draft: TextOverlay,
    private val originalId: String?,
) {
    val isNew: Boolean get() = originalId == null
    val isCaption: Boolean get() = draft.caption != null

    fun withText(text: String): TextOverlayEditorModel =
        copy(
            draft = draft.copy(
                text = text,
                caption = draft.caption?.copy(sourceText = text),
            ),
        )

    fun withFontSizePx(fontSizePx: Double): TextOverlayEditorModel {
        require(fontSizePx.isFinite() && fontSizePx > 0.0) { "font size must be positive" }
        return copy(draft = draft.copy(style = draft.style.copy(fontSizePx = fontSizePx)))
    }

    fun withAlignment(alignment: TextHorizontalAlignment): TextOverlayEditorModel =
        copy(draft = draft.copy(style = draft.style.copy(alignment = alignment)))

    fun withFillArgb(fillArgb: Int): TextOverlayEditorModel =
        copy(draft = draft.copy(style = draft.style.copy(fillArgb = fillArgb)))

    fun withTextOpacity(opacity: Double): TextOverlayEditorModel {
        require(opacity.isFinite()) { "text opacity must be finite" }
        return copy(draft = draft.copy(opacity = opacity.coerceIn(0.0, 1.0)))
    }

    fun withLetterSpacingEm(letterSpacingEm: Double): TextOverlayEditorModel {
        require(letterSpacingEm.isFinite() && letterSpacingEm in -0.5..2.0) {
            "letter spacing must be finite and in -0.5..2.0 em"
        }
        return copy(draft = draft.copy(style = draft.style.copy(letterSpacingEm = letterSpacingEm)))
    }

    fun withBackgroundEnabled(enabled: Boolean): TextOverlayEditorModel {
        val background = if (enabled) {
            draft.style.background ?: TextBackgroundStyle(
                argb = 0xFF000000.toInt(),
                paddingPx = 10.0,
                cornerRadiusPx = 10.0,
                opacity = 0.70,
            )
        } else {
            null
        }
        return copy(draft = draft.copy(style = draft.style.copy(background = background)))
    }

    fun withBackgroundArgb(argb: Int): TextOverlayEditorModel {
        val background = draft.style.background ?: return this
        return copy(
            draft = draft.copy(
                style = draft.style.copy(background = background.copy(argb = argb)),
            ),
        )
    }

    fun withBackgroundOpacity(opacity: Double): TextOverlayEditorModel {
        require(opacity.isFinite()) { "background opacity must be finite" }
        val background = draft.style.background ?: return this
        return copy(
            draft = draft.copy(
                style = draft.style.copy(background = background.copy(opacity = opacity.coerceIn(0.0, 1.0))),
            ),
        )
    }

    fun withShadowEnabled(enabled: Boolean): TextOverlayEditorModel {
        val style = if (enabled) {
            draft.style.copy(
                shadowArgb = null,
                shadowRadiusPx = 0.0,
                shadowOffsetX = 0.0,
                shadowOffsetY = 0.0,
                shadows = draft.style.shadows.ifEmpty {
                    listOf(
                        TextShadowStyle(
                            argb = 0xCC000000.toInt(),
                            radiusPx = 6.0,
                            offsetX = 2.0,
                            offsetY = 3.0,
                            opacity = 0.9,
                        ),
                    )
                },
            )
        } else {
            draft.style.copy(
                shadowArgb = null,
                shadowRadiusPx = 0.0,
                shadowOffsetX = 0.0,
                shadowOffsetY = 0.0,
                shadows = emptyList(),
            )
        }
        return copy(draft = draft.copy(style = style))
    }

    fun withShadowArgb(argb: Int): TextOverlayEditorModel =
        updatePrimaryShadow { shadow -> shadow.copy(argb = argb) }

    fun withShadowRadiusPx(radiusPx: Double): TextOverlayEditorModel {
        require(radiusPx.isFinite()) { "shadow radius must be finite" }
        return updatePrimaryShadow { shadow -> shadow.copy(radiusPx = radiusPx.coerceIn(0.0, 200.0)) }
    }

    fun withShadowOffset(offsetX: Double, offsetY: Double): TextOverlayEditorModel {
        require(offsetX.isFinite() && offsetY.isFinite()) { "shadow offsets must be finite" }
        return updatePrimaryShadow { shadow ->
            shadow.copy(
                offsetX = offsetX.coerceIn(-200.0, 200.0),
                offsetY = offsetY.coerceIn(-200.0, 200.0),
            )
        }
    }

    fun withShadowOpacity(opacity: Double): TextOverlayEditorModel {
        require(opacity.isFinite()) { "shadow opacity must be finite" }
        return updatePrimaryShadow { shadow -> shadow.copy(opacity = opacity.coerceIn(0.0, 1.0)) }
    }

    fun withEntranceAnimation(kind: TextAnimationKind): TextOverlayEditorModel =
        copy(draft = draft.copy(entrance = animationDescriptor(draft.entrance, kind, DEFAULT_ENTRANCE_DURATION_US)))

    fun withExitAnimation(kind: TextAnimationKind): TextOverlayEditorModel =
        copy(draft = draft.copy(exit = animationDescriptor(draft.exit, kind, DEFAULT_ENTRANCE_DURATION_US)))

    fun withLoopAnimation(kind: TextAnimationKind): TextOverlayEditorModel =
        copy(draft = draft.copy(loop = animationDescriptor(draft.loop, kind, DEFAULT_LOOP_DURATION_US)))

    fun withDuration(project: Project, requestedDurationUs: Long): TextOverlayEditorModel {
        val updated = TextOverlayUiPolicy.withDuration(project, draft, requestedDurationUs) ?: return this
        return copy(draft = updated)
    }

    fun overlayForSave(): TextOverlay? = draft.takeIf { it.text.isNotBlank() }

    private fun updatePrimaryShadow(transform: (TextShadowStyle) -> TextShadowStyle): TextOverlayEditorModel {
        if (draft.style.shadows.isEmpty()) return this
        val shadows = draft.style.shadows.toMutableList()
        shadows[0] = transform(shadows[0])
        return copy(draft = draft.copy(style = draft.style.copy(shadows = shadows)))
    }

    private fun animationDescriptor(
        previous: TextAnimationDescriptor,
        kind: TextAnimationKind,
        defaultDurationUs: Long,
    ): TextAnimationDescriptor = previous.copy(
        kind = kind,
        durationUs = if (kind == TextAnimationKind.NONE) 0L else previous.durationUs.takeIf { it > 0L } ?: defaultDurationUs,
    )

    companion object {
        private const val DEFAULT_ENTRANCE_DURATION_US = 350_000L
        private const val DEFAULT_LOOP_DURATION_US = 700_000L

        fun forNew(project: Project, playheadUs: Long): TextOverlayEditorModel? =
            TextOverlayUiPolicy.createNew(project, playheadUs, "Texto")?.let { overlay ->
                TextOverlayEditorModel(
                    draft = overlay.copy(text = ""),
                    originalId = null,
                )
            }

        fun forNewCaption(project: Project, playheadUs: Long): TextOverlayEditorModel? =
            TextOverlayUiPolicy.createNew(
                project = project,
                playheadUs = playheadUs,
                text = "Legenda",
                style = TextStyle(
                    fontSizePx = 52.0,
                    alignment = TextHorizontalAlignment.CENTER,
                    fillArgb = 0xFFFFFFFF.toInt(),
                    background = TextBackgroundStyle(
                        argb = 0xFF000000.toInt(),
                        paddingPx = 12.0,
                        cornerRadiusPx = 12.0,
                        opacity = 0.72,
                    ),
                ),
            )?.let { overlay ->
                TextOverlayEditorModel(
                    draft = overlay.copy(
                        text = "",
                        transform = TransformValue(y = project.height * 0.35),
                        caption = CaptionMetadata(sourceText = ""),
                    ),
                    originalId = null,
                )
            }

        fun forEdit(overlay: TextOverlay): TextOverlayEditorModel =
            TextOverlayEditorModel(draft = overlay, originalId = overlay.id)
    }
}
