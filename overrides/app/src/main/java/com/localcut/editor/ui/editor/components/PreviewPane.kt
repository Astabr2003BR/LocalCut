package com.localcut.editor.ui.editor.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.media.PreviewPlayerViewPolicy
import com.localcut.editor.media.PreviewResumePolicy
import com.localcut.editor.media.PreviewState
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@OptIn(ExperimentalApi::class)
@Composable
fun PreviewPane(
    controller: Media3PreviewController,
    state: PreviewState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStepFrames: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 190.dp,
) {
    val hostView = LocalView.current
    val latestState = rememberUpdatedState(state)
    val playerViewRef = remember(controller) { AtomicReference<PlayerView?>(null) }

    DisposableEffect(hostView, controller) {
        val lifecycleOwner = hostView.findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val snapshot = latestState.value
                    val playerView = playerViewRef.get()
                    if (
                        playerView != null &&
                        PreviewResumePolicy.shouldRefresh(snapshot.durationUs, snapshot.playing)
                    ) {
                        playerView.post {
                            if (playerViewRef.get() === playerView) {
                                controller.refreshBoundView(playerView)
                            }
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black,
            shape = RoundedCornerShape(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (state.durationUs > 0L) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).also {
                                it.useController = false
                                it.setShutterBackgroundColor(android.graphics.Color.BLACK)
                                it.setEnableComposeSurfaceSyncWorkaround(
                                    PreviewPlayerViewPolicy.ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND,
                                )
                                it.setKeepContentOnPlayerReset(
                                    PreviewPlayerViewPolicy.KEEP_CONTENT_ON_PLAYER_RESET,
                                )
                                playerViewRef.set(it)
                                controller.bindTo(it)
                            }
                        },
                        update = { playerView ->
                            playerViewRef.set(playerView)
                            controller.bindTo(playerView)
                        },
                        onRelease = { playerView ->
                            playerViewRef.compareAndSet(playerView, null)
                            controller.unbindFrom(playerView)
                        },
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(
                            text = "+",
                            color = Color(0xFFEDEDF0),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            text = "Adicione mídia para começar",
                            color = Color(0xFFB8B8BE),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.errorMessage?.let { message ->
                    Surface(
                        color = Color(0xDD4A171B),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    ) {
                        Text(
                            text = message,
                            color = Color(0xFFFFDADD),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        val duration = state.durationUs.coerceAtLeast(0L)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatUs(state.positionUs),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9B9BA1),
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onStepFrames(-1) },
                enabled = duration > 0L,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("‹|", color = Color(0xFFD9D9DD)) }
            TextButton(
                onClick = onPlayPause,
                enabled = duration > 0L,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    if (state.playing) "Ⅱ" else "▶",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = { onStepFrames(1) },
                enabled = duration > 0L,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("|›", color = Color(0xFFD9D9DD)) }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatUs(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9B9BA1),
            )
        }
    }
}

private fun formatUs(us: Long): String {
    val totalSeconds = us.coerceAtLeast(0L) / 1_000_000.0
    val minutes = (totalSeconds / 60).toInt()
    val seconds = totalSeconds - minutes * 60
    return String.format(Locale.ROOT, "%02d:%05.2f", minutes, seconds)
}
