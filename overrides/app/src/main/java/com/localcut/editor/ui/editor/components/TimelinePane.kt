package com.localcut.editor.ui.editor.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcut.editor.audio.AndroidWaveformDecoder
import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformData
import com.localcut.editor.audio.WaveformRepository
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.ui.editor.BeatMarkerUiPolicy
import com.localcut.editor.ui.editor.KeyframeEditorPolicy
import com.localcut.editor.ui.editor.TimelineEditBounds
import com.localcut.editor.ui.editor.TimelineTrackGeometry
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.math.max

private const val US_PER_SECOND = 1_000_000.0
private const val DEFAULT_DP_PER_SECOND = 84f
private const val WAVEFORM_BUCKETS = 96
private const val WAVEFORM_CACHE_SAMPLES = 16_384

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
    dpPerSecond: Float = DEFAULT_DP_PER_SECOND,
    onSetTrackMuted: (String, Boolean) -> Unit = { _, _ -> },
    onSetTrackSolo: (String, Boolean) -> Unit = { _, _ -> },
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val durationUs = max(TimelineMath.durationUs(project), 1_000_000L)
    val timelineWidth = max(360f, durationUs / US_PER_SECOND.toFloat() * dpPerSecond).dp
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
        modifier = modifier.fillMaxWidth().verticalScroll(verticalScroll),
        verticalArrangement = Arrangement.spacedBy(TimelineTrackGeometry.DEFAULT_ROW_GAP_DP.dp),
    ) {
        trackLayout.rows.forEach { rowGeometry ->
            val track = tracksById.getValue(rowGeometry.trackId)
            Row(
                modifier = Modifier.fillMaxWidth().height(TimelineTrackGeometry.DEFAULT_ROW_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackHeader(
                    track = track,
                    onSetMuted = onSetTrackMuted,
                    onSetSolo = onSetTrackSolo,
                )
                Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScroll).padding(start = 6.dp)) {
                    TrackLane(
                        track = track,
                        beatMarkers = project.beatMarkers,
                        selectedClipId = selectedClipId,
                        playheadUs = playheadUs,
                        timelineWidth = timelineWidth,
                        dpPerSecond = dpPerSecond,
                        assetNames = assetNames,
                        waveforms = waveforms,
                        onSelectClip = onSelectClip,
                        onMoveClip = onMoveClip,
                        onSeek = onSeek,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackHeader(
    track: Track,
    onSetMuted: (String, Boolean) -> Unit,
    onSetSolo: (String, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.width(76.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        if (track.type != TrackType.AUDIO) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = trackLabel(track),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "ÁUDIO ${track.order}",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val background = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier.background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackLane(
    track: Track,
    beatMarkers: List<BeatMarker>,
    selectedClipId: String?,
    playheadUs: Long,
    timelineWidth: androidx.compose.ui.unit.Dp,
    dpPerSecond: Float,
    assetNames: Map<String, String>,
    waveforms: Map<String, WaveformData>,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimLeft: (String, Long) -> Unit,
    onTrimRight: (String, Long) -> Unit,
) {
    val density = LocalDensity.current
    val playheadColor = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(58.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), MaterialTheme.shapes.small)
            .pointerInput(track.id, dpPerSecond) {
                detectTapGestures { offset ->
                    val xDp = with(density) { offset.x.toDp().value }
                    val targetUs = (xDp / dpPerSecond * US_PER_SECOND).toLong().coerceAtLeast(0L)
                    onSelectClip(null)
                    onSeek(targetUs)
                }
            },
    ) {
        if (track.clips.isEmpty()) {
            Text(
                text = "Vazio",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            )
        }

        track.clips.forEach { clip ->
            val x = (clip.startUs / US_PER_SECOND * dpPerSecond).toFloat().dp
            val width = max(32f, (clip.durationUs / US_PER_SECOND * dpPerSecond).toFloat()).dp
            var bodyDragPx by remember(clip.id, clip.startUs) { mutableFloatStateOf(0f) }
            val selected = clip.id == selectedClipId
            val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            val waveform = if (track.type == TrackType.AUDIO) waveforms[clip.id] else null
            val waveformColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.52f)
            val keyframeMarkerColor = MaterialTheme.colorScheme.tertiary
            val keyframeFractions = KeyframeEditorPolicy.markerFractions(clip)

            Box(
                modifier = Modifier
                    .offset(x = x, y = 6.dp)
                    .width(width)
                    .height(46.dp)
                    .graphicsLayer { translationX = bodyDragPx }
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .drawBehind {
                        val peaks = waveform?.peaks.orEmpty()
                        if (peaks.isNotEmpty()) {
                            val centerY = size.height / 2f
                            val xStep = size.width / peaks.size.toFloat()
                            peaks.forEachIndexed { index, peak ->
                                val amplitude = peak.coerceIn(0f, 1f) * centerY * 0.82f
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
                                radius = 4f,
                                center = Offset(size.width * fraction, 7f),
                            )
                        }
                    }
                    .border(if (selected) 3.dp else 1.dp, borderColor, MaterialTheme.shapes.small)
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
                            val snapped = BeatMarkerUiPolicy.snapTimeUs(beatMarkers, requested)
                            onMoveClip(clip.id, TimelineEditBounds.clampMove(track, clip.id, snapped))
                        },
                    ),
            ) {
                Text(
                    text = assetNames[clip.assetId]?.take(24) ?: "Clipe",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (selected) {
                    TrimHandle(
                        label = "‹",
                        modifier = Modifier.align(Alignment.CenterStart),
                        onFinished = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            val requested = (clip.startUs + deltaUs).coerceAtLeast(0L)
                            val snapped = BeatMarkerUiPolicy.snapTimeUs(beatMarkers, requested)
                            onTrimLeft(clip.id, TimelineEditBounds.clampLeft(track, clip.id, snapped))
                        },
                    )
                    TrimHandle(
                        label = "›",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onFinished = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            val requested = (clip.startUs + clip.durationUs + deltaUs).coerceAtLeast(0L)
                            val snapped = BeatMarkerUiPolicy.snapTimeUs(beatMarkers, requested)
                            onTrimRight(clip.id, TimelineEditBounds.clampRight(track, clip.id, snapped))
                        },
                    )
                }
            }
        }

        val playheadX = (playheadUs.coerceAtLeast(0L) / US_PER_SECOND * dpPerSecond).toFloat().dp
        Box(
            modifier = Modifier
                .offset(x = playheadX)
                .width(3.dp)
                .height(58.dp)
                .drawBehind { drawRect(playheadColor) },
        )
    }
}

@Composable
private fun TrimHandle(
    label: String,
    modifier: Modifier,
    onFinished: (Float) -> Unit,
) {
    var deltaPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(16.dp)
            .height(46.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { deltaPx += it },
                onDragStopped = {
                    val result = deltaPx
                    deltaPx = 0f
                    onFinished(result)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun trackLabel(track: Track): String = when {
    track.type == TrackType.VISUAL && track.collisionPolicy == TrackCollisionPolicy.OVERLAY -> "OVERLAY"
    track.type == TrackType.VISUAL -> "VÍDEO"
    else -> "ÁUDIO"
}
