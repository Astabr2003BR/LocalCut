package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNodeKind

data class CameraPreset(
    val id: String,
    val label: String,
    val settings: CameraSettings,
)

object CameraPresetUiPolicy {
    private val values = listOf(
        CameraPreset(
            id = "perspective-strong",
            label = "Perspectiva forte",
            settings = CameraSettings(focalLength = 750.0, orthographic = false),
        ),
        CameraPreset(
            id = "normal",
            label = "Normal",
            settings = CameraSettings(focalLength = 1000.0, orthographic = false),
        ),
        CameraPreset(
            id = "perspective-soft",
            label = "Perspectiva suave",
            settings = CameraSettings(focalLength = 1800.0, orthographic = false),
        ),
        CameraPreset(
            id = "orthographic",
            label = "Ortográfica",
            settings = CameraSettings(focalLength = 1000.0, orthographic = true),
        ),
    )

    fun presets(): List<CameraPreset> = values

    fun apply(graph: CompositionGraph, cameraNodeId: String, presetId: String): CompositionGraph {
        require(cameraNodeId.isNotBlank()) { "cameraNodeId must not be blank" }
        val preset = values.firstOrNull { it.id == presetId }
            ?: throw IllegalArgumentException("unknown camera preset: $presetId")
        val node = graph.nodes.firstOrNull { it.id == cameraNodeId }
            ?: throw IllegalArgumentException("composition node does not exist: $cameraNodeId")
        require(node.kind == CompositionNodeKind.CAMERA) { "node is not a camera: $cameraNodeId" }
        require(!node.locked) { "controller is locked: $cameraNodeId" }
        require(preset.settings.validationIssues().isEmpty()) { preset.settings.validationIssues().first() }
        return graph.copy(
            nodes = graph.nodes.map { current ->
                if (current.id == cameraNodeId) current.copy(camera = preset.settings) else current
            },
        )
    }
}
