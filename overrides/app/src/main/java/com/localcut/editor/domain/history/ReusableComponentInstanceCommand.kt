package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

/** Expands a reusable component into ordinary design elements in one undoable history step. */
data class InstantiateReusableComponentCommand(
    val componentId: String,
    val instancePrefix: String,
    val startUs: Long,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(instancePrefix.isNotBlank()) { "instance prefix must not be blank" }
        require(startUs >= 0L) { "instance start must be non-negative" }
        val component = project.reusableComponents.firstOrNull { it.id == componentId }
            ?: throw IllegalArgumentException("reusable component $componentId not found")
        val byId = project.designElements.associateBy { it.id }
        val sources = component.elementIds.map { id ->
            byId[id] ?: throw IllegalArgumentException("component $componentId references missing element $id")
        }
        require(sources.isNotEmpty()) { "reusable component must contain elements" }

        val earliestStart = sources.minOf { it.startUs }
        val minZ = sources.minOf { it.zIndex }
        val baseZ = (project.designElements.maxOfOrNull { it.zIndex } ?: -1) + 1
        val existingIds = project.designElements.map { it.id }.toHashSet()
        val created = sources.map { source ->
            val id = "$instancePrefix-${source.id}"
            require(existingIds.add(id)) { "design element $id already exists" }
            val relativeStart = source.startUs - earliestStart
            require(startUs <= Long.MAX_VALUE - relativeStart) { "component instance timing overflow" }
            source.copy(
                id = id,
                startUs = startUs + relativeStart,
                zIndex = baseZ + (source.zIndex - minZ),
            )
        }

        val edited = validated(project.copy(designElements = project.designElements + created))
        return CommandResult(
            project = edited,
            inverse = RemoveInstantiatedDesignElementsCommand(created),
        )
    }
}

private data class RemoveInstantiatedDesignElementsCommand(
    val elements: List<DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val ids = elements.map { it.id }.toSet()
        require(ids.size == elements.size) { "instantiated design ids must be unique" }
        require(ids.all { id -> project.designElements.any { it.id == id } }) {
            "one or more instantiated design elements are missing"
        }
        val edited = validated(project.copy(designElements = project.designElements.filterNot { it.id in ids }))
        return CommandResult(
            project = edited,
            inverse = RestoreInstantiatedDesignElementsCommand(elements),
        )
    }
}

private data class RestoreInstantiatedDesignElementsCommand(
    val elements: List<DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val existingIds = project.designElements.map { it.id }.toSet()
        require(elements.none { it.id in existingIds }) { "instantiated design element already exists" }
        val edited = validated(project.copy(designElements = project.designElements + elements))
        return CommandResult(
            project = edited,
            inverse = RemoveInstantiatedDesignElementsCommand(elements),
        )
    }
}

private fun validated(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid component instance: ${issues.first()}" }
    return project
}
