package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType

data class ObjectTrackingRawVideoMetadata(
    val encodedWidthPx: Int,
    val encodedHeightPx: Int,
    val rotationDeg: Int,
)

interface ObjectTrackingVideoMetadataReader {
    fun read(asset: Asset): Result<ObjectTrackingRawVideoMetadata>
}

class ObjectTrackingSourceGeometryResolver(
    private val metadataReader: ObjectTrackingVideoMetadataReader,
) {
    fun resolve(asset: Asset): Result<ObjectTrackingSourceGeometry> = runCatching {
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "OBJECT_TRACKING_GEOMETRY_REQUIRES_VIDEO"
        }
        val metadata = metadataReader.read(asset).getOrThrow()
        ObjectTrackingSourceGeometryPolicy.oriented(
            encodedWidthPx = metadata.encodedWidthPx,
            encodedHeightPx = metadata.encodedHeightPx,
            rotationDeg = metadata.rotationDeg,
        )
    }
}
