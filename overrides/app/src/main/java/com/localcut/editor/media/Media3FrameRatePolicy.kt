package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect

@OptIn(UnstableApi::class)
object Media3FrameRatePolicy {
    fun effectsFor(sourceProjectFps: Int, targetFps: Int): List<Effect> {
        require(sourceProjectFps > 0) { "sourceProjectFps must be > 0" }
        require(targetFps > 0) { "targetFps must be > 0" }
        require(targetFps <= sourceProjectFps) {
            "targetFps=$targetFps cannot exceed sourceProjectFps=$sourceProjectFps without frame interpolation"
        }
        if (targetFps == sourceProjectFps) return emptyList()
        return listOf(FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat()))
    }
}
