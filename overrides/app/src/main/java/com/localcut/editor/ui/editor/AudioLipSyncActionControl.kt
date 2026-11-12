package com.localcut.editor.ui.editor

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.audio.AndroidWaveformDecoder
import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformRepository
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.animation.AudioDrivenTalkPlanner
import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.animation.WaveformLipSyncCueCoordinator
import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.EditorAction
import java.util.concurrent.Executors

/** Offline, waveform-energy mouth animation. The user must position/approve the mouth region. */
@Composable
fun AudioLipSyncActionControl(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val project = session.project
    val imageClip = session.selectedClipId?.let(project::findClip) ?: return
    val imageAsset = project.assets.firstOrNull { it.id == imageClip.assetId } ?: return
    if (imageAsset.type != AssetType.IMAGE) return
    val visualPlacementSupported = MouthRegionRenderProjectionPolicy.supportsSourcePreview(
        project, imageClip,
    )
    var sourceSize by remember(imageClip.id, imageAsset.fingerprint) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val voiceClips = project.tracks.filter { it.type == TrackType.AUDIO }.flatMap { it.clips }
        .filter { clip -> project.assets.any { it.id == clip.assetId && it.type == AssetType.AUDIO } }

    var open by remember(imageClip.id) { mutableStateOf(false) }
    var voiceId by remember(imageClip.id) { mutableStateOf(voiceClips.firstOrNull()?.id ?: "") }
    var centerX by remember(imageClip.id) { mutableFloatStateOf(0.5f) }
    var centerY by remember(imageClip.id) { mutableFloatStateOf(0.68f) }
    var radiusX by remember(imageClip.id) { mutableFloatStateOf(0.14f) }
    var radiusY by remember(imageClip.id) { mutableFloatStateOf(0.08f) }
    var regionApproved by remember(imageClip.id) { mutableStateOf(false) }
    var working by remember(imageClip.id) { mutableStateOf(false) }
    var message by remember(imageClip.id) { mutableStateOf<String?>(null) }
    var sequence by remember(imageClip.id) { mutableStateOf(0L) }
    var activeRequest by remember(imageClip.id) {
        mutableStateOf<WaveformLipSyncCueCoordinator.RequestHandle?>(null)
    }
    val operationGate = remember(imageClip.id) { AudioLipSyncOperationGate() }
    val decoderExecutor = remember(imageClip.id) { Executors.newSingleThreadExecutor() }
    val plannerExecutor = remember(imageClip.id) { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val coordinator = remember(context, imageClip.id) {
        WaveformLipSyncCueCoordinator(
            WaveformRepository(
                WaveformCache(maxSamples = 8_192),
                AndroidWaveformDecoder(context.contentResolver),
                decoderExecutor,
            ),
            plannerExecutor,
        )
    }

    DisposableEffect(imageClip.id) {
        onDispose {
            // A result may already be queued on the main thread; invalidate it first.
            operationGate.dispose()
            activeRequest?.cancel()
            decoderExecutor.shutdownNow()
            plannerExecutor.shutdownNow()
        }
    }
    fun closeDialog() {
        operationGate.cancel()
        sequence += 1L
        activeRequest?.cancel()
        activeRequest = null
        working = false
        open = false
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        OutlinedButton(
            onClick = { message = null; open = true },
            enabled = voiceClips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sincronizar boca com áudio") }
        if (voiceClips.isEmpty()) Text(
            "Adicione uma voz na faixa de áudio antes de usar o lip-sync.",
            style = MaterialTheme.typography.bodySmall,
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (!open) return
    AlertDialog(
        onDismissRequest = { closeDialog() },
        title = { Text("Lip-sync local") },
        text = {
            Column(
                Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "A boca acompanha a energia da voz, não os fonemas. " +
                        "O processamento ocorre no celular e não utiliza créditos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Selecione o clipe de voz:")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    voiceClips.forEach { clip ->
                        val name = project.assets.firstOrNull { it.id == clip.assetId }
                            ?.displayName?.take(20) ?: "Áudio"
                        OutlinedButton(
                            enabled = !working,
                            onClick = { voiceId = clip.id; regionApproved = false },
                        ) { Text((if (voiceId == clip.id) "✓ " else "") + name) }
                    }
                }
                if (visualPlacementSupported) {
                    Text("Marque a boca tocando ou arrastando na imagem original:")
                    MouthRegionVisualPicker(
                        asset = imageAsset,
                        region = MouthRegionPlacementPolicy.fromUiValues(centerX, centerY, radiusX, radiusY),
                        enabled = !working,
                        onSourceDimensionsChanged = { width, height ->
                            if (sourceSize != (width to height)) {
                                sourceSize = width to height
                                regionApproved = false
                            }
                        },
                        onRegionChanged = { selected ->
                            centerX = selected.centerX.toFloat()
                            centerY = selected.centerY.toFloat()
                            radiusX = selected.radiusX.toFloat()
                            radiusY = selected.radiusY.toFloat()
                            regionApproved = false
                        },
                    )
                } else {
                    Text(
                        "Esta imagem tem transformação, recorte ou animação. " +
                            "A marcação visual não pode garantir alinhamento; " +
                            "use os valores da região no quadro final.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Ajuste fino (posição e tamanho em porcentagem):")
                Text("Horizontal: " + (centerX * 100).toInt() + "%")
                Slider(
                    value = centerX,
                    onValueChange = {
                        centerX = it.coerceIn(radiusX, 1f - radiusX)
                        regionApproved = false
                    },
                    valueRange = 0f..1f,
                    enabled = !working,
                )
                Text("Vertical: " + (centerY * 100).toInt() + "%")
                Slider(
                    value = centerY,
                    onValueChange = {
                        centerY = it.coerceIn(radiusY, 1f - radiusY)
                        regionApproved = false
                    },
                    valueRange = 0f..1f,
                    enabled = !working,
                )
                Text("Raio horizontal: " + (radiusX * 100).toInt() + "%")
                Slider(
                    value = radiusX,
                    onValueChange = { value ->
                        val adjusted = MouthRegionPlacementPolicy.withRadii(
                            MouthRegionPlacementPolicy.fromUiValues(centerX, centerY, radiusX, radiusY),
                            value.toDouble().coerceIn(0.01, 0.50),
                            radiusY.toDouble().coerceIn(0.005, 0.30),
                        )
                        radiusX = adjusted.radiusX.toFloat()
                        centerX = adjusted.centerX.toFloat()
                        regionApproved = false
                    },
                    valueRange = 0.01f..0.50f,
                    enabled = !working,
                )
                Text("Raio vertical: " + (radiusY * 100).toInt() + "%")
                Slider(
                    value = radiusY,
                    onValueChange = { value ->
                        val adjusted = MouthRegionPlacementPolicy.withRadii(
                            MouthRegionPlacementPolicy.fromUiValues(centerX, centerY, radiusX, radiusY),
                            radiusX.toDouble().coerceIn(0.01, 0.50),
                            value.toDouble().coerceIn(0.005, 0.30),
                        )
                        radiusY = adjusted.radiusY.toFloat()
                        centerY = adjusted.centerY.toFloat()
                        regionApproved = false
                    },
                    valueRange = 0.005f..0.30f,
                    enabled = !working,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Checkbox(regionApproved, { regionApproved = it }, enabled = !working)
                    Text(
                        "Conferi a região da boca. Posso reverter usando Desfazer.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (working) Text("Decodificando e analisando voz…")
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && regionApproved && voiceClips.any { it.id == voiceId },
                onClick = {
                    val before = runtime.session.state
                    runCatching {
                        require(before.selectedClipId == imageClip.id) { "Seleção de imagem alterada" }
                        val slice = AudioDrivenTalkPlanner.prepare(before.project, imageClip.id, voiceId)
                        val sourceAsset = requireNotNull(
                            before.project.assets.firstOrNull { it.id == slice.audioAssetId },
                        )
                        val uri = AssetUriResolver(context.contentResolver)
                            .resolve(sourceAsset).getOrThrow().toString()
                        require(uri.isNotBlank()) { "Arquivo de voz indisponível" }
                        slice to uri
                    }.fold(
                        onSuccess = { (slice, uri) ->
                            activeRequest?.cancel()
                            val requestToken = operationGate.begin()
                            working = true
                            message = "Analisando áudio…"
                            sequence += 1L
                            val operation = sequence
                            val localRegion = MouthRegionPlacementPolicy.fromUiValues(
                                centerX, centerY, radiusX, radiusY,
                            )
                            val chosenRegion = if (visualPlacementSupported && sourceSize != null) {
                                val (width, height) = checkNotNull(sourceSize)
                                MouthRegionRenderProjectionPolicy.fromSourceToEffect(
                                    localRegion, width, height, before.project.width, before.project.height,
                                )
                            } else {
                                // Fallback sliders remain in final-frame coordinates.
                                localRegion
                            }
                            val key = WaveformCacheKey(
                                slice.audioFingerprint, slice.audioSourceInUs,
                                slice.audioSourceOutUs, slice.bucketCount,
                            )
                            activeRequest = coordinator.request(key, uri, slice.durationUs) { decoded ->
                                mainHandler.post {
                                    // Cancellation/navigation/reselection invalidates already posted results.
                                    if (!operationGate.isCurrent(requestToken) ||
                                        !open || sequence != operation
                                    ) return@post
                                    val applied = runCatching {
                                        val current = runtime.session.state
                                        AudioDrivenTalkPlanner.requireUnchanged(
                                            current, before.revision, before.project.id, slice,
                                        )
                                        val cues = decoded.getOrThrow()
                                        val plan = AudioDrivenTalkPlanner.plan(
                                            current.project, slice, chosenRegion, cues,
                                        )
                                        runtime.editorViewModel.dispatch(
                                            EditorAction.Execute(ApplyAnimationPlanCommand(plan)),
                                        )
                                        check(runtime.session.state.revision == before.revision + 1L) {
                                            runtime.session.state.lastError ?: "Animação não aplicada"
                                        }
                                        "TALK aplicado com " + cues.size + " trechos de voz. Use Desfazer se necessário."
                                    }
                                    operationGate.cancel()
                                    working = false
                                    activeRequest = null
                                    applied.fold(
                                        onSuccess = { message = it; open = false },
                                        onFailure = { message = it.message ?: "Falha ao aplicar TALK" },
                                    )
                                }
                            }
                        },
                        onFailure = { message = it.message ?: "Falha ao preparar áudio" },
                    )
                },
            ) { Text("Aplicar TALK") }
        },
        dismissButton = {
            TextButton(onClick = { closeDialog() }) { Text(if (working) "Cancelar" else "Fechar") }
        },
    )
}
