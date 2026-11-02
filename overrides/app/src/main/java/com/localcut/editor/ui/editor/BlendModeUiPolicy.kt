package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.BlendMode

data class BlendModeUiOption(
    val mode: BlendMode,
    val label: String,
)

object BlendModeUiPolicy {
    private val supported = listOf(
        BlendModeUiOption(BlendMode.NORMAL, "Normal"),
        BlendModeUiOption(BlendMode.MULTIPLY, "Multiplicar"),
        BlendModeUiOption(BlendMode.SCREEN, "Tela"),
        BlendModeUiOption(BlendMode.OVERLAY, "Sobrepor"),
        BlendModeUiOption(BlendMode.DARKEN, "Escurecer"),
        BlendModeUiOption(BlendMode.LIGHTEN, "Clarear"),
        BlendModeUiOption(BlendMode.ADD, "Adicionar"),
        BlendModeUiOption(BlendMode.SUBTRACT, "Subtrair"),
    )

    fun options(): List<BlendModeUiOption> = supported
}
