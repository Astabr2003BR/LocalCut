package com.localcut.editor.media

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionRenderResolverTest {
    private val clip = Clip(
        id = "clip-1",
        assetId = "asset-1",
        startUs = 0L,
        durationUs = 2_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 2_000_000L,
        transform = TransformValue(x = 10.0, y = 5.0, scale = 2.0, rotationDeg = 5.0, opacity = 0.8),
        opacity = 0.75,
        keyframes = listOf(
            TransformKeyframe(1_000_000L, TransformValue(x = 20.0, y = 0.0, scale = 1.5, opacity = 0.5)),
        ),
    )

    @Test
    fun emptyGraphPreservesLegacyTransformExactly() {
        val resolved = CompositionRenderResolver.resolve(CompositionGraph(), clip).getOrThrow()

        assertEquals(clip.transform, resolved.transform)
        assertEquals(clip.keyframes, resolved.keyframes)
        assertEquals(clip.opacity, resolved.opacity, 0.0)
    }

    @Test
    fun hiddenLayerResolvesToZeroOpacityForPreviewAndExport() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "layer",
                    kind = CompositionNodeKind.LAYER,
                    clipId = clip.id,
                    visible = false,
                ),
            ),
        )

        val resolved = CompositionRenderResolver.resolve(graph, clip).getOrThrow()

        assertEquals(0.0, resolved.opacity, 0.0)
    }

    @Test
    fun parentingComposesContainerTransformIntoClipAndKeyframes() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "parent",
                    kind = CompositionNodeKind.NULL,
                    transform = LayerTransform3D(x = 100.0, y = 50.0, scale = 2.0, opacity = 0.5),
                ),
                CompositionNode(
                    id = "layer",
                    kind = CompositionNodeKind.LAYER,
                    parentId = "parent",
                    clipId = clip.id,
                    transform = LayerTransform3D(x = 25.0, y = 10.0, scale = 0.5, rotationZDeg = 10.0, opacity = 0.8),
                ),
            ),
        )

        val resolved = CompositionRenderResolver.resolve(graph, clip).getOrThrow()

        assertEquals(158.9798366418, resolved.transform.x, 1e-6)
        assertEquals(76.6605205417, resolved.transform.y, 1e-6)
        assertEquals(2.0, resolved.transform.scale, 1e-6)
        assertEquals(15.0, resolved.transform.rotationDeg, 1e-6)
        assertEquals(0.30, resolved.opacity, 1e-9)
        assertEquals(169.6961550602, resolved.keyframes.single().value.x, 1e-6)
        assertEquals(73.4729635533, resolved.keyframes.single().value.y, 1e-6)
    }

    @Test
    fun staticCameraCreatesDepthParallax() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "camera",
                    kind = CompositionNodeKind.CAMERA,
                    camera = CameraSettings(focalLength = 1000.0),
                ),
                CompositionNode(
                    id = "layer",
                    kind = CompositionNodeKind.LAYER,
                    clipId = clip.id,
                    transform = LayerTransform3D(x = 100.0, z = 100.0),
                ),
            ),
        )

        val resolved = CompositionRenderResolver.resolve(graph, clip).getOrThrow()

        assertTrue(resolved.transform.x > 100.0)
        assertTrue(resolved.transform.scale > clip.transform.scale)
    }

    @Test
    fun unsupportedPlaneTiltFailsClosedInsteadOfSilentlyFlattening() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "layer",
                    kind = CompositionNodeKind.LAYER,
                    clipId = clip.id,
                    transform = LayerTransform3D(rotationXDeg = 20.0),
                ),
            ),
        )

        val result = CompositionRenderResolver.resolve(graph, clip)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("rotationX/rotationY"))
    }

    @Test
    fun temporalCameraCutsFailClosedUntilDiscreteCutBackendExists() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("camera-a", CompositionNodeKind.CAMERA),
                CompositionNode("camera-b", CompositionNodeKind.CAMERA),
                CompositionNode("layer", CompositionNodeKind.LAYER, clipId = clip.id),
            ),
            cameraCuts = listOf(
                CameraCut(0L, "camera-a"),
                CameraCut(1_000_000L, "camera-b"),
            ),
        )

        val result = CompositionRenderResolver.resolve(graph, clip)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("camera cuts"))
    }
}
