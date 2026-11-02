package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingSourceGeometryResolverTest {
    @Test
    fun resolverUsesVideoMetadataAndNormalizesRotation() {
        val asset = videoAsset()
        val reader = FakeReader(
            ObjectTrackingRawVideoMetadata(
                encodedWidthPx = 1920,
                encodedHeightPx = 1080,
                rotationDeg = 90,
            ),
        )
        val resolver = ObjectTrackingSourceGeometryResolver(reader)

        val result = resolver.resolve(asset).getOrThrow()

        assertEquals(asset.uri, reader.lastUri)
        assertEquals(ObjectTrackingSourceGeometry(1080, 1920), result)
    }

    @Test
    fun nonVideoFailsBeforeMetadataRead() {
        val reader = FakeReader(ObjectTrackingRawVideoMetadata(100, 100, 0))
        val resolver = ObjectTrackingSourceGeometryResolver(reader)
        val result = resolver.resolve(videoAsset().copy(type = AssetType.AUDIO, mimeType = "audio/mp4"))

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_GEOMETRY_REQUIRES_VIDEO", result.exceptionOrNull()?.message)
        assertEquals(null, reader.lastUri)
    }

    @Test
    fun metadataFailureIsPropagatedWithoutFallbackGeometry() {
        val resolver = ObjectTrackingSourceGeometryResolver(
            object : ObjectTrackingVideoMetadataReader {
                override fun read(asset: Asset): Result<ObjectTrackingRawVideoMetadata> =
                    Result.failure(IllegalStateException("OBJECT_TRACKING_METADATA_UNAVAILABLE"))
            },
        )

        val result = resolver.resolve(videoAsset())

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_METADATA_UNAVAILABLE", result.exceptionOrNull()?.message)
    }

    private fun videoAsset() = Asset(
        id = "asset-1",
        type = AssetType.VIDEO,
        uri = "content://localcut/video.mp4",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 5L,
        displayName = "video.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-1",
    )

    private class FakeReader(
        private val metadata: ObjectTrackingRawVideoMetadata,
    ) : ObjectTrackingVideoMetadataReader {
        var lastUri: String? = null

        override fun read(asset: Asset): Result<ObjectTrackingRawVideoMetadata> {
            lastUri = asset.uri
            return Result.success(metadata)
        }
    }
}
