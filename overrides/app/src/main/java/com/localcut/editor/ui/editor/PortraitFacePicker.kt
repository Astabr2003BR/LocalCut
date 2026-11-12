package com.localcut.editor.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.animation.BrowExpressionSettings
import com.localcut.editor.domain.animation.PortraitFaceGeometry
import com.localcut.editor.domain.animation.PortraitFaceSuggestion
import com.localcut.editor.domain.animation.PortraitMotionSettings
import com.localcut.editor.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private data class FacePoint(val x: Double, val y: Double)

@Composable
internal fun PortraitFacePicker(
    asset: Asset,
    motion: PortraitMotionSettings,
    brows: BrowExpressionSettings,
    enabled: Boolean,
    onSuggestion: (PortraitFaceSuggestion) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var image by remember(asset.id, asset.fingerprint) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(asset.id, asset.fingerprint) { mutableStateOf(false) }
    var first by remember(asset.id, asset.fingerprint) { mutableStateOf<FacePoint?>(null) }
    var second by remember(asset.id, asset.fingerprint) { mutableStateOf<FacePoint?>(null) }
    var notice by remember(asset.id, asset.fingerprint) { mutableStateOf<String?>(null) }
    var request by remember(asset.id, asset.fingerprint) { mutableStateOf(0) }
    var busy by remember(asset.id, asset.fingerprint) { mutableStateOf(false) }
    val latestMotion by rememberUpdatedState(motion)
    val latestBrows by rememberUpdatedState(brows)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSuggestion by rememberUpdatedState(onSuggestion)
    LaunchedEffect(asset.id, asset.fingerprint) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadPreviewBitmap(context, asset) }
        }
        image = loaded.getOrNull()
        failed = loaded.isFailure
    }
    val bitmap = image
    if (bitmap == null) {
        Text(if (failed) "Prévia indisponível. Use os controles manuais." else "Carregando imagem…")
        return
    }
    fun propose(a: FacePoint, b: FacePoint) {
        if (!latestEnabled) return
        runCatching {
            PortraitFaceGeometry.suggest(bitmap.width, bitmap.height, a.x, a.y, b.x, b.y,
                latestMotion, latestBrows)
        }.onSuccess {
            first = a
            second = b
            latestSuggestion(it)
            notice = "Sugestão preparada. Confira olhos, sobrancelhas e boca antes de aplicar."
        }.onFailure {
            notice = "Marcação não aplicada: " + (it.message ?: "geometria inválida")
        }
    }
    LaunchedEffect(asset.id, asset.fingerprint, request) {
        if (request == 0 || !latestEnabled) return@LaunchedEffect
        busy = true
        try {
            val detected = withContext(Dispatchers.Default) {
                runCatching { detectEyes(bitmap) }.getOrNull()
            }
            if (!latestEnabled) return@LaunchedEffect
            if (detected == null) {
                notice = "Não foi possível localizar um único rosto frontal. Marque os dois olhos na imagem."
            } else {
                propose(detected.first, detected.second)
            }
        } finally {
            busy = false
        }
    }
    Text("Toque no centro de cada olho. A posição das sobrancelhas e da boca será estimada.")
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        Image(bitmap.asImageBitmap(), contentDescription = "Imagem para marcar os olhos",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().pointerInput(bitmap, enabled, busy, first, second) {
            detectTapGestures { tap ->
                if (!enabled || busy || size.width <= 0 || size.height <= 0) return@detectTapGestures
                val fit = MouthRegionPlacementPolicy.fit(bitmap.width, bitmap.height,
                    size.width.toDouble(), size.height.toDouble())
                val x = (tap.x - fit.leftPx) / fit.widthPx
                val y = (tap.y - fit.topPx) / fit.heightPx
                if (x !in 0.0..1.0 || y !in 0.0..1.0) return@detectTapGestures
                val point = FacePoint(x, y)
                val prior = first
                if (prior == null || second != null) {
                    first = point
                    second = null
                    notice = "Marque o outro olho."
                } else {
                    propose(prior, point)
                }
            }
        }) {
            if (size.width > 0 && size.height > 0) {
                val fit = MouthRegionPlacementPolicy.fit(bitmap.width, bitmap.height,
                    size.width.toDouble(), size.height.toDouble())
                listOfNotNull(first, second).forEach { point ->
                    val center = Offset((fit.leftPx + point.x * fit.widthPx).toFloat(),
                        (fit.topPx + point.y * fit.heightPx).toFloat())
                    drawCircle(Color.Cyan, radius = 7.dp.toPx(), center = center,
                        style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
    FilledTonalButton(onClick = { request++ }, enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Analisando…" else "Sugerir olhos automaticamente")
    }
    OutlinedButton(onClick = { first = null; second = null; notice = null },
        enabled = enabled && !busy) { Text("Refazer marcação") }
    notice?.let { Text(it) }
}

/** Platform detector needs an even-width RGB_565 bitmap. Bounded, offline,
 * and deliberately rejects uncertain, tilted or multi-face detections. */
private fun detectEyes(source: Bitmap): Pair<FacePoint, FacePoint>? {
    val scale = (512.0 / maxOf(source.width, source.height)).coerceAtMost(1.0)
    val width = ((source.width * scale).roundToInt() / 2) * 2
    val height = (source.height * scale).roundToInt()
    if (width < 32 || height < 32) return null
    val resized = Bitmap.createScaledBitmap(source, width, height, true)
    try {
        val rgb = resized.copy(Bitmap.Config.RGB_565, false) ?: return null
        try {
            val faces = arrayOfNulls<FaceDetector.Face>(2)
            val count = FaceDetector(width, height, faces.size).findFaces(rgb, faces)
            if (count != 1) return null
            val face = faces[0] ?: return null
            if (face.confidence() < 0.55f ||
                abs(face.pose(FaceDetector.Face.EULER_Z)) > 12f ||
                abs(face.pose(FaceDetector.Face.EULER_Y)) > 20f) return null
            val middle = PointF()
            face.getMidPoint(middle)
            val half = face.eyesDistance().toDouble() / 2.0
            val y = middle.y.toDouble() / height
            return FacePoint((middle.x - half) / width, y) to
                FacePoint((middle.x + half) / width, y)
        } finally {
            if (rgb !== resized) rgb.recycle()
        }
    } finally {
        if (resized !== source) resized.recycle()
    }
}
