package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

data class SetCompositionGraphCommand(
    val graph: CompositionGraph,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val previous = project.compositionGraph
        val edited = validatedComposition(project.copy(compositionGraph = graph))
        return CommandResult(edited, SetCompositionGraphCommand(previous))
    }
}

private fun validatedComposition(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid composition edit: ${issues.first()}" }
    return project
}
