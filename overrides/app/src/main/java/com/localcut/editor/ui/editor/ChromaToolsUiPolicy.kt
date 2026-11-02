package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings

enum class ChromaKeyColorPreset(val argb: Int) {
    GREEN(0xFF00FF00.toInt()),
    BLUE(0xFF0000FF.toInt()),
    MAGENTA(0xFFFF00FF.toInt()),
    BLACK(0xFF000000.toInt()),
    WHITE(0xFFFFFFFF.toInt()),
}

object ChromaToolsUiPolicy {
    fun label(preset: ChromaKeyColorPreset): String = when (preset) {
        ChromaKeyColorPreset.GREEN -> "Verde"
        ChromaKeyColorPreset.BLUE -> "Azul"
        ChromaKeyColorPreset.MAGENTA -> "Magenta"
        ChromaKeyColorPreset.BLACK -> "Preto"
        ChromaKeyColorPreset.WHITE -> "Branco"
    }

    fun setKeyColor(
        source: CompositingSettings,
        preset: ChromaKeyColorPreset,
    ): CompositingSettings {
        val updated = source.copy(
            chromaKey = source.chromaKey.copy(
                enabled = true,
                keyArgb = preset.argb,
                edgeFeatherPx = 0.0,
            ),
        )
        val issues = updated.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return updated
    }

    fun selectedPreset(source: CompositingSettings): ChromaKeyColorPreset? =
        ChromaKeyColorPreset.entries.firstOrNull { it.argb == source.chromaKey.keyArgb }
}
