package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

data class AddClipTransitionCommand(
    val transition: ClipTransition,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.transitions.none { it.id == transition.id }) {
            "clip transition ${transition.id} already exists"
        }
        val edited = validateTransitionEdit(project.copy(transitions = project.transitions + transition))
        return CommandResult(
            project = edited,
            inverse = DeleteClipTransitionCommand(transition.id),
        )
    }
}

data class UpdateClipTransitionCommand(
    val transition: ClipTransition,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.transitions.indexOfFirst { it.id == transition.id }
        require(index >= 0) { "clip transition ${transition.id} not found" }
        val previous = project.transitions[index]
        val transitions = project.transitions.toMutableList().also { it[index] = transition }
        val edited = validateTransitionEdit(project.copy(transitions = transitions))
        return CommandResult(
            project = edited,
            inverse = UpdateClipTransitionCommand(previous),
        )
    }
}

data class DeleteClipTransitionCommand(
    val transitionId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.transitions.indexOfFirst { it.id == transitionId }
        require(index >= 0) { "clip transition $transitionId not found" }
        val removed = project.transitions[index]
        val transitions = project.transitions.toMutableList().also { it.removeAt(index) }
        val edited = validateTransitionEdit(project.copy(transitions = transitions))
        return CommandResult(
            project = edited,
            inverse = RestoreClipTransitionCommand(index, removed),
        )
    }
}

private data class RestoreClipTransitionCommand(
    val index: Int,
    val transition: ClipTransition,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.transitions.none { it.id == transition.id }) {
            "clip transition ${transition.id} already exists"
        }
        require(index in 0..project.transitions.size) { "clip transition restore index out of range" }
        val transitions = project.transitions.toMutableList().also { it.add(index, transition) }
        val edited = validateTransitionEdit(project.copy(transitions = transitions))
        return CommandResult(
            project = edited,
            inverse = DeleteClipTransitionCommand(transition.id),
        )
    }
}

private fun validateTransitionEdit(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid clip transition edit: ${issues.first()}" }
    return project
}
