package com.localcut.editor.ui.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localcut.editor.ui.editor.TimelineRulerUiPolicy

private val RulerBackground = Color(0xFF0D0D0F)
private val RulerTickColor = Color(0xFF6E6E74)
private val RulerLabelColor = Color(0xFFA7A7AD)
private val RulerPlayheadColor = Color(0xFF20D5D2)

@Composable
internal fun TimelineRuler(
    durationUs: Long,
    dpPerSecond: Float,
    playheadUs: Long,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val ticksUs = remember(durationUs, dpPerSecond) {
        TimelineRulerUiPolicy.majorTicksUs(durationUs, dpPerSecond)
    }
    val clampedPlayheadUs = playheadUs.coerceIn(0L, durationUs)

    Box(
        modifier = modifier
            .width(width)
            .height(24.dp)
            .background(RulerBackground),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            ticksUs.forEach { timeUs ->
                val x = ((timeUs / 1_000_000.0) * dpPerSecond).toFloat().dp.toPx()
                drawLine(
                    color = RulerTickColor,
                    start = androidx.compose.ui.geometry.Offset(x, size.height * 0.58f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val playheadX = ((clampedPlayheadUs / 1_000_000.0) * dpPerSecond).toFloat().dp.toPx()
            drawLine(
                color = RulerPlayheadColor,
                start = androidx.compose.ui.geometry.Offset(playheadX, 0f),
                end = androidx.compose.ui.geometry.Offset(playheadX, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }

        ticksUs.forEach { timeUs ->
            val xDp = ((timeUs / 1_000_000.0) * dpPerSecond).toFloat().dp
            Text(
                text = TimelineRulerUiPolicy.formatTimeUs(timeUs),
                style = MaterialTheme.typography.labelSmall,
                color = RulerLabelColor,
                maxLines = 1,
                modifier = Modifier.offset(x = xDp + 3.dp, y = 0.dp),
            )
        }
    }
}
