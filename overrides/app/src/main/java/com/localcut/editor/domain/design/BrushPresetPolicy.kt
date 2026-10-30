package com.localcut.editor.domain.design

data class BrushPreset(
    val id: String,
    val label: String,
    val strokeWidthPx: Double,
    val opacity: Double,
)

object BrushPresetPolicy {
    private val presets = listOf(
        BrushPreset(id = "fineliner", label = "Fineliner", strokeWidthPx = 2.0, opacity = 1.0),
        BrushPreset(id = "pencil", label = "Lápis", strokeWidthPx = 4.0, opacity = 0.78),
        BrushPreset(id = "ink", label = "Tinta", strokeWidthPx = 8.0, opacity = 1.0),
        BrushPreset(id = "marker", label = "Marcador", strokeWidthPx = 18.0, opacity = 0.62),
        BrushPreset(id = "highlighter", label = "Marca-texto", strokeWidthPx = 28.0, opacity = 0.32),
        BrushPreset(id = "bold", label = "Grosso", strokeWidthPx = 32.0, opacity = 1.0),
    )

    fun presets(): List<BrushPreset> = presets

    fun byId(id: String): BrushPreset = presets.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("unknown brush preset: $id")
}
