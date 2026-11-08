package com.localcut.editor.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val PREVIEW_MAX_EDGE = 1024

/**
 * Uses the original, locally authorized image rather than a project screenshot.
 * Decoding is bounded and asynchronous. The old percentage sliders remain as fallback.
 */
@Composable
fun MouthRegionVisualPicker(
    asset: Asset,
    region: MouthRegion,
    enabled: Boolean,
    onRegionChanged: (MouthRegion) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var imageResult by remember(asset.id, asset.fingerprint) {
        mutableStateOf<Result<Bitmap>?>(null)
    }
    LaunchedEffect(asset.id, asset.fingerprint) {
        imageResult = withContext(Dispatchers.IO) {
            runCatching { loadPreviewBitmap(context, asset) }
        }
    }
    val bitmap = imageResult?.getOrNull()
    val loadError = imageResult?.exceptionOrNull()
    if (bitmap == null) {
        Text(
            if (loadError == null) "Carregando a imagem local…" else
                "Prévia indisponível. Use os controles em porcentagem abaixo.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    val currentRegion by rememberUpdatedState(region)
    val latestOnChange by rememberUpdatedState(onRegionChanged)
    fun placeAt(position: Offset, width: Float, height: Float) {
        if (!enabled || width <= 0f || height <= 0f) return
        val fit = MouthRegionPlacementPolicy.fit(
            bitmap.width, bitmap.height, width.toDouble(), height.toDouble(),
        )
        MouthRegionPlacementPolicy.place(
            currentRegion, position.x.toDouble(), position.y.toDouble(), fit,
        )?.let(latestOnChange)
    }

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Imagem original para marcar a região da boca",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(bitmap.width, bitmap.height, enabled) {
                    detectTapGestures { position ->
                        placeAt(position, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(bitmap.width, bitmap.height, enabled) {
                    detectDragGestures(
                        onDragStart = { position ->
                            placeAt(position, size.width.toFloat(), size.height.toFloat())
                        },
                        onDrag = { change, _ ->
                            placeAt(change.position, size.width.toFloat(), size.height.toFloat())
                            change.consume()
                        },
                    )
                },
        ) {
            val fit = MouthRegionPlacementPolicy.fit(
                bitmap.width, bitmap.height, size.width.toDouble(), size.height.toDouble(),
            )
            val oval = MouthRegionPlacementPolicy.ellipseBounds(region, fit)
            drawOval(
                color = Color.Cyan,
                topLeft = Offset(oval.leftPx.toFloat(), oval.topPx.toFloat()),
                size = Size(oval.widthPx.toFloat(), oval.heightPx.toFloat()),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color.Cyan,
                radius = 3.dp.toPx(),
                center = Offset(
                    (fit.leftPx + region.centerX * fit.widthPx).toFloat(),
                    (fit.topPx + region.centerY * fit.heightPx).toFloat(),
                ),
            )
        }
    }
}

private fun loadPreviewBitmap(context: Context, asset: Asset): Bitmap {
    // ImageDecoder supports orientation metadata and bounded decode on Android 9+.
    // Older Android versions preserve the numerical controls rather than risking a
    // misplaced oval on EXIF-rotated media.
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        "A prévia visual requer Android 9 ou superior"
    }
    val uri = AssetUriResolver(context.contentResolver).resolve(asset).getOrThrow()
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        require(width > 0 && height > 0) { "Imagem com dimensões inválidas" }
        val scale = (PREVIEW_MAX_EDGE.toDouble() / max(width, height)).coerceAtMost(1.0)
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        decoder.setTargetSize(targetWidth, targetHeight)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}
