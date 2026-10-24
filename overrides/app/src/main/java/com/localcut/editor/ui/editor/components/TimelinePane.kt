package com.localcut.editor.ui.editor.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.audio.AndroidWaveformDecoder
import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformData
import com.localcut.editor.audio.WaveformRepository
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.mobile.MediaIngestResult
import com.localcut.editor.ui.editor.KeyframeEditorPolicy
import com.localcut.editor.ui.editor.TimelineAddMediaPolicy
import com.localcut.editor.ui.editor.TimelineAddTarget
import com.localcut.editor.ui.editor.TimelineEditBounds
import com.localcut.editor.ui.editor.TimelineScrubPolicy
import com.localcut.editor.ui.editor.TimelineSnapUiPolicy
import com.localcut.editor.ui.editor.TimelineTrackGeometry
import com.localcut.editor.ui.editor.TimelineZoomUiPolicy
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.math.max

private const val US_PER_SECOND = 1_000_000.0
private const val WAVEFORM_BUCKETS = 96
private const val WAVEFORM_CACHE_SAMPLES = 16_384
private const val ADD_BUTTON_SPACE_DP = 56f
private val TimelineBackground = Color(0xFF101012)
private val LaneBackground = Color(0xFF17171A)
private val VisualClipColor = Color(0xFF333338)
private val AudioClipColor = Color(0xFF1D3638)
private val AccentColor = Color(0xFF20D5D2)

@Composable
fun TimelinePane(
    project: Project,
    selectedClipId: String?,
    playheadUs: Long,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimLeft: (String, Long) -> Unit,
    onTrimRight: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
    dpPerSecond: Float = TimelineZoomUiPolicy.DEFAULT_DP_PER_SECOND,
    onSetTrackMuted: (String, Boolean) -> Unit = { _, _ -> },
    onSetTrackSolo: (String, Boolean) -> Unit = { _, _ -> },
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    var timelineZoom by remember(project.id) {
        mutableFloatStateOf(TimelineZoomUiPolicy.clamp(dpPerSecond))
    }
    val durationUs = max(TimelineMath.durationUs(project), 1_000_000L)
    val timelineWidth = (max(360f, durationUs / US_PER_SECOND.toFloat() * timelineZoom) + ADD_BUTTON_SPACE_DP).dp
    val assetNames = project.assets.associate { it.id to it.displayName }
    val tracksById = project.tracks.associateBy { it.id }
    val trackLayout = TimelineTrackGeometry.layout(project.tracks)
    val waveforms = remember { mutableStateMapOf<String, WaveformData>() }
    val requestedWaveforms = remember { mutableMapOf<String, WaveformCacheKey>() }
    val context = LocalContext.current.applicationContext
    val waveformExecutor = remember { Executors.newFixedThreadPool(2) }
    val waveformRepository = remember(context, waveformExecutor) {
        WaveformRepository(
            cache = WaveformCache(WAVEFORM_CACHE_SAMPLES),
            decoder = AndroidWaveformDecoder(context.contentResolver),
            executor = waveformExecutor,
        )
    }
    val scope = rememberCoroutineScope()
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val currentPlayheadUs by rememberUpdatedState(playheadUs)
    var snapEnabled by remember(project.id) { mutableStateOf(true) }
    val snapClipEdges = if (snapEnabled) {
        project.tracks.flatMap { track ->
            track.clips.map { clip ->
                TimelineSnapUiPolicy.ClipEdges(
                    clipId = clip.id,
                    startUs = clip.startUs,
                    endUs = Math.addExact(clip.startUs, clip.durationUs),
                )
            }
        }
    } else {
        emptyList()
    }
    val snapBeatTimesUs = if (snapEnabled) project.beatMarkers.map { it.timeUs } else emptyList()

    fun reportImportResult(result: MediaIngestResult) {
        result.errors.forEach { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
        if (result.importedCount > 0) {
            val message = if (result.importedCount == 1) {
                "1 mídia adicionada"
            } else {
                "${result.importedCount} mídias adicionadas"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val visualImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                reportImportResult(runtime.mediaIngestCoordinator.importUris(uris))
            }
        }
    }
    val overlayImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                reportImportResult(
                    runtime.mediaIngestCoordinator.importOverlayUris(
                        uris = uris,
                        playheadUs = currentPlayheadUs,
                    ),
                )
            }
        }
    }
    val audioImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                reportImportResult(runtime.mediaIngestCoordinator.importUris(listOf(uri)))
            }
        }
    }
    val onAddToTrack: (Track) -> Unit = { track ->
        when (TimelineAddMediaPolicy.targetFor(track.type, track.collisionPolicy)) {
            TimelineAddTarget.MEDIA -> visualImportLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
            TimelineAddTarget.OVERLAY -> overlayImportLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
            TimelineAddTarget.AUDIO -> audioImportLauncher.launch(arrayOf("audio/*"))
        }
    }

    DisposableEffect(waveformExecutor) {
        onDispose { waveformExecutor.shutdownNow() }
    }

    LaunchedEffect(project) {
        val assets = project.assets.associateBy { it.id }
        val desired = project.tracks
            .filter { it.type == TrackType.AUDIO }
            .flatMap { track -> track.clips }
            .mapNotNull { clip ->
                val asset = assets[clip.assetId] ?: return@mapNotNull null
                if (asset.type != AssetType.AUDIO) return@mapNotNull null
                clip to asset
            }
        val desiredIds = desired.mapTo(mutableSetOf()) { it.first.id }
        waveforms.keys.filterNot(desiredIds::contains).forEach(waveforms::remove)
        requestedWaveforms.keys.filterNot(desiredIds::contains).forEach(requestedWaveforms::remove)

        desired.forEach { (clip, asset) ->
            val key = WaveformCacheKey(
                assetFingerprint = asset.fingerprint,
                sourceInUs = clip.sourceInUs,
                sourceOutUs = clip.sourceOutUs,
                bucketCount = WAVEFORM_BUCKETS,
            )
            if (requestedWaveforms[clip.id] == key) return@forEach
            requestedWaveforms[clip.id] = key
            waveforms.remove(clip.id)
            waveformRepository.request(key, asset.uri) { result ->
                scope.launch {
                    if (requestedWaveforms[clip.id] != key) return@launch
                    result.onSuccess { waveform ->
                        waveforms[clip.id] = waveform
                    }.onFailure {
                        requestedWaveforms.remove(clip.id)
                        waveforms.remove(clip.id)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TimelineBackground)
            .verticalScroll(verticalScroll),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { timelineZoom = TimelineZoomUiPolicy.zoomOut(timelineZoom) },
                enabled = timelineZoom > TimelineZoomUiPolicy.MIN_DP_PER_SECOND,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
            ) { Text("−") }
            TextButton(
                onClick = { timelineZoom = TimelineZoomUiPolicy.reset() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    "${TimelineZoomUiPolicy.percent(timelineZoom)}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(
                onClick = { timelineZoom = TimelineZoomUiPolicy.zoomIn(timelineZoom) },
                enabled = timelineZoom < TimelineZoomUiPolicy.MAX_DP_PER_SECOND,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
            ) { Text("+") }
            TextButton(
                onClick = { snapEnabled = !snapEnabled },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    if (snapEnabled) "Ímã ✓" else "Ímã",
                    color = if (snapEnabled) AccentColor else Color(0xFFA2A2A8),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (project.beatMarkers.isNotEmpty()) {
                Text(
                    "${project.beatMarkers.size} beats",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8D8D93),
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
        }

        trackLayout.rows.forEach { rowGeometry ->
            val track = tracksById.getValue(rowGeometry.trackId)
            val addAction = if (track.locked) null else ({ onAddToTrack(track) })
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackHeader(
                    track = track,
                    onAdd = addAction,
                    onSetMuted = onSetTrackMuted,
                    onSetSolo = onSetTrackSolo,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(start = 3.dp),
                ) {
                    TrackLane(
                        track = track,
                        snapEnabled = snapEnabled,
                        snapClipEdges = snapClipEdges,
                        snapBeatTimesUs = snapBeatTimesUs,
                        selectedClipId = selectedClipId,
                        playheadUs = playheadUs,
                        durationUs = durationUs,
                        timelineWidth = timelineWidth,
                        dpPerSecond = timelineZoom,
                        assetNames = assetNames,
                        waveforms = waveforms,
                        onSelectClip = onSelectClip,
                        onMoveClip = onMoveClip,
                        onSeek = onSeek,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                        onAdd = addAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackHeader(
    track: Track,
    onAdd: (() -> Unit)?,
    onSetMuted: (String, Boolean) -> Unit,
    onSetSolo: (String, Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(Color(0xFF0D0D0F)),
        contentAlignment = Alignment.Center,
    ) {
        if (track.type != TrackType.AUDIO) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = trackGlyph(track),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFBEBEC4),
                )
                if (onAdd != null) {
                    Text(
                        text = "+",
                        color = AccentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable(onClick = onAdd)
                            .padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "A${track.order}",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBEBEC4),
                    )
                    if (onAdd != null) {
                        Text(
                            text = "+",
                            color = AccentColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable(onClick = onAdd)
                                .padding(start = 5.dp, end = 2.dp),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AudioTrackToggle(
                        label = "M",
                        active = track.muted,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSetMuted(track.id, !track.muted) },
                    )
                    AudioTrackToggle(
                        label = "S",
                        active = track.solo,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSetSolo(track.id, !track.solo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackToggle(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background = if (active) AccentColor.copy(alpha = 0.16f) else Color.Transparent
    Box(
        modifier = modifier.background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) AccentColor else Color(0xFF77777D),
        )
    }
}

@Composable
private fun TrackLane(
    track: Track,
    snapEnabled: Boolean,
    snapClipEdges: List<TimelineSnapUiPolicy.ClipEdges>,
    snapBeatTimesUs: List<Long>,
    selectedClipId: String?,
    playheadUs: Long,
    durationUs: Long,
    timelineWidth: androidx.compose.ui.unit.Dp,
    dpPerSecond: Float,
    assetNames: Map<String, String>,
    waveforms: Map<String, WaveformData>,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimLeft: (String, Long) -> Unit,
    onTrimRight: (String, Long) -> Unit,
    onAdd: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val snapToleranceUs = TimelineSnapUiPolicy.toleranceUs(dpPerSecond)
    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(58.dp)
            .background(LaneBackground, RoundedCornerShape(4.dp))
            .pointerInput(track.id, dpPerSecond, durationUs) {
                detectTapGestures { offset ->
                    val xDp = with(density) { offset.x.toDp().value }
                    val targetUs = TimelineScrubPolicy.positionForTap(
                        xDp = xDp,
                        dpPerSecond = dpPerSecond,
                        durationUs = durationUs,
                    )
                    onSelectClip(null)
                    onSeek(targetUs)
                }
            },
    ) {
        if (track.clips.isEmpty()) {
            Text(
                text = if (track.type == TrackType.AUDIO) "Áudio" else "Mídia",
                color = Color(0xFF66666C),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
            )
        }

        track.clips.forEach { clip ->
            val x = (clip.startUs / US_PER_SECOND * dpPerSecond).toFloat().dp
            val width = max(32f, (clip.durationUs / US_PER_SECOND * dpPerSecond).toFloat()).dp
            var bodyDragPx by remember(clip.id, clip.startUs) { mutableFloatStateOf(0f) }
            val selected = clip.id == selectedClipId
            val waveform = if (track.type == TrackType.AUDIO) waveforms[clip.id] else null
            val waveformColor = Color(0xFF9EEBE8).copy(alpha = 0.58f)
            val keyframeMarkerColor = Color(0xFFFFC857)
            val keyframeFractions = KeyframeEditorPolicy.markerFractions(clip)
            val snapCandidates = if (snapEnabled) {
                TimelineSnapUiPolicy.candidates(
                    clipEdgesUs = snapClipEdges,
                    excludedClipId = clip.id,
                    playheadUs = playheadUs,
                    beatTimesUs = snapBeatTimesUs,
                )
            } else {
                emptyList()
            }
            val clipColor = if (track.type == TrackType.AUDIO) AudioClipColor else VisualClipColor

            Box(
                modifier = Modifier
                    .offset(x = x, y = 5.dp)
                    .width(width)
                    .height(48.dp)
                    .graphicsLayer { translationX = bodyDragPx }
                    .background(clipColor, RoundedCornerShape(5.dp))
                    .drawBehind {
                        val peaks = waveform?.peaks.orEmpty()
                        if (peaks.isNotEmpty()) {
                            val centerY = size.height / 2f
                            val xStep = size.width / peaks.size.toFloat()
                            peaks.forEachIndexed { index, peak ->
                                val amplitude = peak.coerceIn(0f, 1f) * centerY * 0.78f
                                val xPx = (index + 0.5f) * xStep
                                drawLine(
                                    color = waveformColor,
                                    start = Offset(xPx, centerY - amplitude),
                                    end = Offset(xPx, centerY + amplitude),
                                    strokeWidth = max(1f, xStep * 0.28f),
                                )
                            }
                        }
                        keyframeFractions.forEach { fraction ->
                            drawCircle(
                                color = keyframeMarkerColor,
                                radius = 3.5f,
                                center = Offset(size.width * fraction, 6f),
                            )
                        }
                    }
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) AccentColor else Color.Transparent,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .clickable { onSelectClip(clip.id) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> bodyDragPx += delta },
                        onDragStarted = { onSelectClip(clip.id) },
                        onDragStopped = {
                            val deltaDp = with(density) { bodyDragPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            bodyDragPx = 0f
                            val requested = (clip.startUs + deltaUs).coerceAtLeast(0L)
                            val snapped = if (snapEnabled) {
                                TimelineSnapUiPolicy.snapMove(
                                    requestedStartUs = requested,
                                    clipDurationUs = clip.durationUs,
                                    candidatesUs = snapCandidates,
                                    toleranceUs = snapToleranceUs,
                                )
                            } else {
                                requested
                            }
                            onMoveClip(clip.id, TimelineEditBounds.clampMove(track, clip.id, snapped))
                        },
                    ),
            ) {
                Text(
                    text = assetNames[clip.assetId]?.take(24) ?: "Clipe",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF1F1F3),
                )
                if (selected) {
                    TrimHandle(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onFinished = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            val requested = (clip.startUs + deltaUs).coerceAtLeast(0L)
                            val snapped = if (snapEnabled) {
                                TimelineSnapUiPolicy.snapEdge(requested, snapCandidates, snapToleranceUs)
                            } else {
                                requested
                            }
                            onTrimLeft(clip.id, TimelineEditBounds.clampLeft(track, clip.id, snapped))
                        },
                    )
                    TrimHandle(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onFinished = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            val requested = (clip.startUs + clip.durationUs + deltaUs).coerceAtLeast(0L)
                            val snapped = if (snapEnabled) {
                                TimelineSnapUiPolicy.snapEdge(requested, snapCandidates, snapToleranceUs)
                            } else {
                                requested
                            }
                            onTrimRight(clip.id, TimelineEditBounds.clampRight(track, clip.id, snapped))
                        },
                    )
                }
            }
        }

        if (onAdd != null) {
            val trackEndUs = track.clips.maxOfOrNull { clip ->
                Math.addExact(clip.startUs, clip.durationUs)
            } ?: 0L
            val addX = ((trackEndUs / US_PER_SECOND * dpPerSecond).toFloat() + 8f).dp
            Box(
                modifier = Modifier
                    .offset(x = addX, y = 10.dp)
                    .width(38.dp)
                    .height(38.dp)
                    .background(AccentColor.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
                    .border(1.dp, AccentColor.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = AccentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        DraggablePlayhead(
            playheadUs = playheadUs,
            durationUs = durationUs,
            dpPerSecond = dpPerSecond,
            onSelectClip = onSelectClip,
            onSeek = onSeek,
        )
    }
}

@Composable
private fun DraggablePlayhead(
    playheadUs: Long,
    durationUs: Long,
    dpPerSecond: Float,
    onSelectClip: (String?) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val safeDurationUs = durationUs.coerceAtLeast(0L)
    val clampedPlayheadUs = playheadUs.coerceIn(0L, safeDurationUs)
    val playheadX = (clampedPlayheadUs / US_PER_SECOND * dpPerSecond).toFloat().dp
    var dragStartUs by remember { mutableStateOf(clampedPlayheadUs) }
    var dragDeltaPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(x = playheadX - 14.dp)
            .width(28.dp)
            .height(58.dp)
            .zIndex(20f)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { deltaPx ->
                    dragDeltaPx += deltaPx
                    val deltaDp = with(density) { dragDeltaPx.toDp().value }
                    onSeek(
                        TimelineScrubPolicy.positionForDrag(
                            startUs = dragStartUs,
                            deltaDp = deltaDp,
                            dpPerSecond = dpPerSecond,
                            durationUs = safeDurationUs,
                        ),
                    )
                },
                onDragStarted = {
                    dragStartUs = clampedPlayheadUs
                    dragDeltaPx = 0f
                    onSelectClip(null)
                },
                onDragStopped = { dragDeltaPx = 0f },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(AccentColor),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(12.dp)
                .height(6.dp)
                .background(AccentColor, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
        )
    }
}

@Composable
private fun TrimHandle(
    modifier: Modifier,
    onFinished: (Float) -> Unit,
) {
    var deltaPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(8.dp)
            .height(48.dp)
            .background(AccentColor, RoundedCornerShape(4.dp))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { deltaPx += it },
                onDragStopped = {
                    val result = deltaPx
                    deltaPx = 0f
                    onFinished(result)
                },
            ),
    )
}

private fun trackGlyph(track: Track): String = when {
    track.type == TrackType.VISUAL && track.collisionPolicy == TrackCollisionPolicy.OVERLAY -> "O"
    track.type == TrackType.VISUAL -> "V"
    else -> "A"
}
