package com.localcut.editor.analysis

import com.localcut.editor.domain.animation.GlobalMotionSample
import com.localcut.editor.domain.animation.GlobalMotionStabilizationPlanner
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.termux.TermuxGlobalMotionAnalysis
import com.localcut.editor.termux.TermuxGlobalMotionSample

object TermuxGlobalMotionAdapter {
    fun toStabilizationAnimations(
        result: TermuxGlobalMotionAnalysis,
        strength: Double = 1.0,
        minConfidence: Double = 0.5,
        sourceInUs: Long = 0L,
        sourceOutUs: Long = Long.MAX_VALUE,
    ): List<PropertyAnimation> {
        require(sourceInUs >= 0L) { "stabilization sourceInUs must be non-negative" }
        require(sourceOutUs > sourceInUs) { "stabilization source window must be positive" }

        val samples = clipLocalSamples(result.samples, sourceInUs, sourceOutUs)
        if (samples.isEmpty()) return emptyList()

        return GlobalMotionStabilizationPlanner.plan(
            samples = samples,
            strength = strength,
            minConfidence = minConfidence,
        )
    }

    private fun clipLocalSamples(
        samples: List<TermuxGlobalMotionSample>,
        sourceInUs: Long,
        sourceOutUs: Long,
    ): List<GlobalMotionSample> {
        if (samples.isEmpty()) return emptyList()
        val result = ArrayList<GlobalMotionSample>(samples.size)
        samples.forEachIndexed { index, sample ->
            if (sample.timeUs <= sourceInUs || sample.timeUs > sourceOutUs) return@forEachIndexed

            val previousTimeUs = samples.getOrNull(index - 1)?.timeUs
            val crossesTrimBoundary = sourceInUs > 0L &&
                previousTimeUs != null &&
                previousTimeUs < sourceInUs
            if (crossesTrimBoundary) return@forEachIndexed

            val localOffsetUs = sample.timeUs - sourceInUs
            if (localOffsetUs <= 0L) return@forEachIndexed
            result += GlobalMotionSample(
                offsetUs = localOffsetUs,
                dx = sample.dx,
                dy = sample.dy,
                rotationDeg = sample.rotationDeg,
                scale = sample.scale,
                confidence = sample.confidence,
            )
        }
        return result
    }
}
