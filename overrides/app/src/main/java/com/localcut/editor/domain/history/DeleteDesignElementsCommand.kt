package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

data class DeleteDesignElementsCommand(
    val elementIds: List<String>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(elementIds.isNotEmpty()) { "design element delete set must not be empty" }
        require(elementIds.all { it.isNotBlank() }) { "design element ids must not be blank" }
        require(elementIds.distinct().size == elementIds.size) { "design element ids must be unique" }

        val requested = elementIds.toSet()
        val existingIds = project.designElements.mapTo(linkedSetOf()) { it.id }
        val missing = requested.filterNot { it in existingIds }
        require(missing.isEmpty()) { "design elements not found: ${missing.joinToString()}" }

        val protectedIds = project.reusableComponents
            .asSequence()
            .flatMap { it.elementIds.asSequence() }
            .filter { it in requested }
            .distinct()
            .toList()
        require(protectedIds.isEmpty()) {
            "design elements are used by reusable components: ${protectedIds.joinToString()}"
        }

        val previous = project.designElements
        val next = previous.filterNot { it.id in requested }
        val edited = validated(project.copy(designElements = next))
        return CommandResult(
            project = edited,
            inverse = ReplaceDesignElementListCommand(
                target = previous,
                inverseTarget = next,
            ),
        )
    }
}

private data class ReplaceDesignElementListCommand(
    val target: List<com.localcut.editor.domain.model.DesignElement>,
    val inverseTarget: List<com.localcut.editor.domain.model.DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val edited = validated(project.copy(designElements = target))
        return CommandResult(
            project = edited,
            inverse = ReplaceDesignElementListCommand(
                target = inverseTarget,
                inverseTarget = target,
            ),
        )
    }
}

private fun validated(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid design batch delete: ${issues.first()}" }
    return project
}
