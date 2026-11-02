package com.localcut.editor.domain.model

enum class CompositionNodeKind {
    LAYER,
    GROUP,
    NULL,
    CAMERA,
}

data class LayerTransform3D(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val scale: Double = 1.0,
    val rotationXDeg: Double = 0.0,
    val rotationYDeg: Double = 0.0,
    val rotationZDeg: Double = 0.0,
    val opacity: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        val finite = listOf(x, y, z, scale, rotationXDeg, rotationYDeg, rotationZDeg, opacity).all { it.isFinite() }
        if (!finite) add("layer transform values must be finite")
        if (!scale.isFinite() || scale <= 0.0) add("layer scale must be finite and positive")
        if (!opacity.isFinite() || opacity !in 0.0..1.0) add("layer opacity must be finite and in 0..1")
    }
}

data class CameraSettings(
    val focalLength: Double = 1000.0,
    val orthographic: Boolean = false,
) {
    fun validationIssues(): List<String> = buildList {
        if (!focalLength.isFinite() || focalLength <= 0.0) add("camera focalLength must be finite and positive")
    }
}

data class CompositionNode(
    val id: String,
    val kind: CompositionNodeKind,
    val parentId: String? = null,
    val clipId: String? = null,
    val transform: LayerTransform3D = LayerTransform3D(),
    val camera: CameraSettings? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
)

data class CameraCut(
    val startUs: Long,
    val cameraNodeId: String,
)

data class CompositionGraph(
    val nodes: List<CompositionNode> = emptyList(),
    val cameraCuts: List<CameraCut> = emptyList(),
)
