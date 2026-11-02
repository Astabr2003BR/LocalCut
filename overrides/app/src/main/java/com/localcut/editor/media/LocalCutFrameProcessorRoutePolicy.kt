package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

object LocalCutFrameProcessorRoutePolicy {
    fun requiresCustomProcessor(project: Project): Boolean =
        project.tracks.asSequence()
            .filter { it.type == TrackType.VISUAL && !it.hidden }
            .flatMap { it.clips.asSequence() }
            .any { clip ->
                clip.compositing.blendMode != BlendMode.NORMAL ||
                    clip.compositing.chromaKey.enabled ||
                    clip.compositing.masks.isNotEmpty()
            }
}
