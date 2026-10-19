package com.localcut.editor.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import kotlin.math.max

private const val US_PER_SECOND = 1_000_000.0
private const val DEFAULT_DP_PER_SECOND = 84f

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
) {
    val scroll = rememberScrollState()
    val durationUs = max(TimelineMath.durationUs(project), 1_000_000L)
    val timelineWidth = max(360f, durationUs / US_PER_SECOND.toFloat() * dpPerSecond).dp
    val assetNames = project.assets.associate { it.id to it.displayName }

    Column(modifier = modifier.fillMaxWidth()) {
        project.tracks.sortedBy { it.order }.forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(76.dp).height(58.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = trackLabel(track.type),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).horizontalScroll(scroll).padding(start = 6.dp)) {
                    TrackLane(
                        track = track,
                        selectedClipId = selectedClipId,
                        playheadUs = playheadUs,
                        timelineWidth = timelineWidth,
                        dpPerSecond = dpPerSecond,
                        assetNames = assetNames,
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
private fun TrackLane(
    track: Track,
    selectedClipId: String?,
    playheadUs: Long,
    timelineWidth: androidx.compose.ui.unit.Dp,
    dpPerSecond: Float,
    assetNames: Map<String, String>,
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

            Box(
                modifier = Modifier
                    .offset(x = x, y = 6.dp)
                    .width(width)
                    .height(46.dp)
                    .graphicsLayer { translationX = bodyDragPx }
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
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
                            onMoveClip(clip.id, (clip.startUs + deltaUs).coerceAtLeast(0L))
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
                            onTrimLeft(clip.id, (clip.startUs + deltaUs).coerceAtLeast(0L))
                        },
                    )
                    TrimHandle(
                        label = "›",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onFinished = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            val deltaUs = (deltaDp / dpPerSecond * US_PER_SECOND).toLong()
                            onTrimRight(clip.id, (clip.startUs + clip.durationUs + deltaUs).coerceAtLeast(0L))
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

private fun trackLabel(type: TrackType): String = when (type) {
    TrackType.VISUAL -> "VÍDEO"
    TrackType.AUDIO -> "ÁUDIO"
}
