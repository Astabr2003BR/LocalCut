package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.model.TimeMap

@OptIn(UnstableApi::class)
internal class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    init {
        require(speed.isFinite() && speed > 0f) { "speed must be finite and positive" }
    }

    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

internal object Media3TimeMapSupport {
    fun canRender(timeMap: TimeMap): Boolean =
        timeMap.validationIssues().isEmpty() &&
            !timeMap.reverse &&
            timeMap.speedPoints.isEmpty() &&
            timeMap.freezeSegments.isEmpty()

    fun providerOrNull(timeMap: TimeMap): ConstantSpeedProvider? =
        if (canRender(timeMap) && timeMap.baseSpeed != 1.0) {
            ConstantSpeedProvider(timeMap.baseSpeed.toFloat())
        } else {
            null
        }
}
