package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

data class MoveDesignElementsCommand(
    val elementIds: List<String>,
    val dx: Double,
    val dy: Double,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(elementIds.isNotEmpty()) { "design element move set must not be empty" }
        require(elementIds.all { it.isNotBlank() }) { "design element ids must not be blank" }
        require(elementIds.distinct().size == elementIds.size) { "design element ids must be unique" }
        require(dx.isFinite() && dy.isFinite()) { "design move delta must be finite" }
        require(dx != 0.0 || dy != 0.0) { "design move delta must not be zero" }

        val requested = elementIds.toSet()
        val existingIds = project.designElements.mapTo(linkedSetOf()) { it.id }
        val missing = requested.filterNot { it in existingIds }
        require(missing.isEmpty()) { "design elements not found: ${missing.joinToString()}" }

        val previous = project.designElements
        val next = previous.map { element ->
            if (element.id !in requested) return@map element
            val nextX = element.transform.x + dx
            val nextY = element.transform.y + dy
            require(nextX.isFinite() && nextY.isFinite()) { "design move transform overflow" }
            element.copy(
                transform = element.transform.copy(
                    x = nextX,
                    y = nextY,
                ),
            )
        }
        val edited = validatedMove(project.copy(designElements = next))
        return CommandResult(
            project = edited,
            inverse = ReplaceMovedDesignElementListCommand(
                target = previous,
                inverseTarget = next,
            ),
        )
    }
}

private data class ReplaceMovedDesignElementListCommand(
    val target: List<DesignElement>,
    val inverseTarget: List<DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val edited = validatedMove(project.copy(designElements = target))
        return CommandResult(
            project = edited,
            inverse = ReplaceMovedDesignElementListCommand(
                target = inverseTarget,
                inverseTarget = target,
            ),
        )
    }
}

private fun validatedMove(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid design batch move: ${issues.first()}" }
    return project
}
