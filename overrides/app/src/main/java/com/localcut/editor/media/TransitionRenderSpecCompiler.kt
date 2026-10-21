package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransitionKind
import com.localcut.editor.domain.timeline.ProjectIssueCode
import com.localcut.editor.domain.timeline.TimelineValidator

data class ClipTransitionRenderSpec(
    val clipId: String,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
)

object TransitionRenderSpecCompiler {
    fun compile(project: Project): Result<Map<String, ClipTransitionRenderSpec>> = runCatching {
        val invalidTransition = TimelineValidator.validate(project)
            .firstOrNull { it.code == ProjectIssueCode.INVALID_TRANSITION }
        require(invalidTransition == null) {
            "invalid transition ${invalidTransition?.subjectId ?: "unknown"}: ${invalidTransition?.detail.orEmpty()}"
        }

        val specs = linkedMapOf<String, ClipTransitionRenderSpec>()
        project.transitions.forEach { transition ->
            when (transition.kind) {
                TransitionKind.FADE_TO_BLACK -> {
                    val from = specs[transition.fromClipId]
                        ?: ClipTransitionRenderSpec(clipId = transition.fromClipId)
                    specs[transition.fromClipId] = from.copy(
                        fadeOutUs = maxOf(from.fadeOutUs, transition.durationUs),
                    )

                    val to = specs[transition.toClipId]
                        ?: ClipTransitionRenderSpec(clipId = transition.toClipId)
                    specs[transition.toClipId] = to.copy(
                        fadeInUs = maxOf(to.fadeInUs, transition.durationUs),
                    )
                }

                TransitionKind.DISSOLVE,
                TransitionKind.WIPE_LEFT,
                -> throw UnsupportedOperationException(
                    "Transition ${transition.kind.name} rendering is not supported yet",
                )
            }
        }
        specs.toMap()
    }
}
