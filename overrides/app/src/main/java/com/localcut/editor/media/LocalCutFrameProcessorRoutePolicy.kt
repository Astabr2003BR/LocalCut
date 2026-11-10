package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

object LocalCutFrameProcessorRoutePolicy {
    /**
     * Media3 only needs a multi-input video compositor when two or more visible
     * visual sequences are present. Audio-only tracks do not count as video inputs.
     * Prefer its default single-input graph for portrait + voice/TALK projects.
     */
    fun requiresMultipleInputVideoGraph(project: Project): Boolean =
        project.tracks.asSequence()
            .filter { it.type == TrackType.VISUAL && !it.hidden && it.clips.isNotEmpty() }
            .take(2)
            .count() > 1

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
