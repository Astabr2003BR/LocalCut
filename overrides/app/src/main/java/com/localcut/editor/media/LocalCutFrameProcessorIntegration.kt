@file:OptIn(
    androidx.media3.common.util.ExperimentalApi::class,
    androidx.media3.common.util.UnstableApi::class,
)

package com.localcut.editor.media

import android.content.Context
import android.os.Build
import androidx.media3.effect.HardwareBufferJni
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.Transformer
import com.localcut.editor.domain.model.Project

internal object LocalCutFrameProcessorIntegration {
    fun shouldUseCustomProcessor(project: Project): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            LocalCutFrameProcessorRoutePolicy.requiresCustomProcessor(project)

    fun configurePreviewBuilder(
        builder: CompositionPlayer.Builder,
        context: Context,
        project: Project,
    ): CompositionPlayer.Builder =
        if (shouldUseCustomProcessor(project)) {
            builder
                .setFrameProcessorFactory(LocalCutBlendFrameProcessor.Factory(context))
                .setNativeHardwareBufferHelpers(HardwareBufferJni.INSTANCE)
        } else if (LocalCutFrameProcessorRoutePolicy.requiresMultipleInputVideoGraph(project)) {
            builder.setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        } else {
            // CompositionPlayer defaults to SingleInputVideoGraph; avoid the
            // multi-input compositor for one portrait plus audio-only tracks.
            builder
        }

    fun configureTransformerBuilder(
        builder: Transformer.Builder,
        context: Context,
        project: Project,
    ): Transformer.Builder =
        if (shouldUseCustomProcessor(project)) {
            builder
                .setFrameProcessorFactory(LocalCutBlendFrameProcessor.Factory(context))
                .setNativeHardwareBufferHelpers(HardwareBufferJni.INSTANCE)
        } else {
            builder
        }
}
