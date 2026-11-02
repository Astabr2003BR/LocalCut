package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

data class UpdateDesignElementsCommand(
    val elements: List<DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(elements.isNotEmpty()) { "design element update set must not be empty" }
        require(elements.all { it.id.isNotBlank() }) { "design element ids must not be blank" }
        require(elements.map { it.id }.distinct().size == elements.size) { "design element ids must be unique" }

        val replacements = elements.associateBy { it.id }
        val existingIds = project.designElements.mapTo(linkedSetOf()) { it.id }
        val missing = replacements.keys.filterNot { it in existingIds }
        require(missing.isEmpty()) { "design elements not found: ${missing.joinToString()}" }

        val previous = project.designElements.filter { it.id in replacements }
        val next = project.designElements.map { replacements[it.id] ?: it }
        val edited = validatedDesignBatch(project.copy(designElements = next))
        return CommandResult(
            project = edited,
            inverse = UpdateDesignElementsCommand(previous),
        )
    }
}

private fun validatedDesignBatch(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid design batch update: ${issues.first()}" }
    return project
}
