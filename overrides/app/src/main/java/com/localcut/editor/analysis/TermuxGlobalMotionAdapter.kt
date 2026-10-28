package com.localcut.editor.analysis

import com.localcut.editor.domain.animation.GlobalMotionSample
import com.localcut.editor.domain.animation.GlobalMotionStabilizationPlanner
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.termux.TermuxGlobalMotionAnalysis

object TermuxGlobalMotionAdapter {
    fun toStabilizationAnimations(
        result: TermuxGlobalMotionAnalysis,
        strength: Double = 1.0,
        minConfidence: Double = 0.5,
    ): List<PropertyAnimation> = GlobalMotionStabilizationPlanner.plan(
        samples = result.samples.map { sample ->
            GlobalMotionSample(
                offsetUs = sample.timeUs,
                dx = sample.dx,
                dy = sample.dy,
                rotationDeg = sample.rotationDeg,
                scale = sample.scale,
                confidence = sample.confidence,
            )
        },
        strength = strength,
        minConfidence = minConfidence,
    )
}
