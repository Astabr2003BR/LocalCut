package com.localcut.editor.domain.history

import com.localcut.editor.domain.design.CaptionEditor
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.timeline.TimelineValidator

data class AddDesignElementCommand(
    val element: DesignElement,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.designElements.none { it.id == element.id }) { "design element ${element.id} already exists" }
        val edited = validatedDesign(project.copy(designElements = project.designElements + element))
        return CommandResult(edited, DeleteDesignElementCommand(element.id))
    }
}

data class UpdateDesignElementCommand(
    val element: DesignElement,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.designElements.indexOfFirst { it.id == element.id }
        require(index >= 0) { "design element ${element.id} not found" }
        val previous = project.designElements[index]
        val elements = project.designElements.toMutableList().also { it[index] = element }
        val edited = validatedDesign(project.copy(designElements = elements))
        return CommandResult(edited, UpdateDesignElementCommand(previous))
    }
}

data class DeleteDesignElementCommand(
    val elementId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.designElements.indexOfFirst { it.id == elementId }
        require(index >= 0) { "design element $elementId not found" }
        require(project.reusableComponents.none { elementId in it.elementIds }) {
            "design element $elementId is used by a reusable component"
        }
        val removed = project.designElements[index]
        val elements = project.designElements.toMutableList().also { it.removeAt(index) }
        val edited = validatedDesign(project.copy(designElements = elements))
        return CommandResult(edited, RestoreDesignElementCommand(index, removed))
    }
}

private data class RestoreDesignElementCommand(
    val index: Int,
    val element: DesignElement,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.designElements.none { it.id == element.id }) { "design element ${element.id} already exists" }
        require(index in 0..project.designElements.size) { "design element restore index out of range" }
        val elements = project.designElements.toMutableList().also { it.add(index, element) }
        val edited = validatedDesign(project.copy(designElements = elements))
        return CommandResult(edited, DeleteDesignElementCommand(element.id))
    }
}

data class SetDesignCanvasCommand(
    val settings: DesignCanvasSettings,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val previous = project.designCanvas
        val edited = validatedDesign(project.copy(designCanvas = settings))
        return CommandResult(edited, SetDesignCanvasCommand(previous))
    }
}

data class SetProjectCanvasSizeCommand(
    val width: Int,
    val height: Int,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(width > 0 && height > 0) { "project canvas dimensions must be positive" }
        if (project.width == width && project.height == height) {
            return CommandResult(project, SetProjectCanvasSizeCommand(project.width, project.height))
        }
        val edited = validatedDesign(project.copy(width = width, height = height))
        return CommandResult(edited, SetProjectCanvasSizeCommand(project.width, project.height))
    }
}

data class UpsertReusableComponentCommand(
    val component: ReusableComponent,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.reusableComponents.indexOfFirst { it.id == component.id }
        val previous = project.reusableComponents.getOrNull(index)
        val components = project.reusableComponents.toMutableList().also { list ->
            if (index >= 0) list[index] = component else list += component
        }
        val edited = validatedDesign(project.copy(reusableComponents = components))
        val inverse: EditorCommand = if (previous == null) {
            DeleteReusableComponentCommand(component.id)
        } else {
            UpsertReusableComponentCommand(previous)
        }
        return CommandResult(edited, inverse)
    }
}

data class DeleteReusableComponentCommand(
    val componentId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.reusableComponents.indexOfFirst { it.id == componentId }
        require(index >= 0) { "reusable component $componentId not found" }
        val removed = project.reusableComponents[index]
        val components = project.reusableComponents.toMutableList().also { it.removeAt(index) }
        val edited = validatedDesign(project.copy(reusableComponents = components))
        return CommandResult(edited, RestoreReusableComponentCommand(index, removed))
    }
}

private data class RestoreReusableComponentCommand(
    val index: Int,
    val component: ReusableComponent,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.reusableComponents.none { it.id == component.id }) { "reusable component ${component.id} already exists" }
        require(index in 0..project.reusableComponents.size) { "reusable component restore index out of range" }
        val components = project.reusableComponents.toMutableList().also { it.add(index, component) }
        val edited = validatedDesign(project.copy(reusableComponents = components))
        return CommandResult(edited, DeleteReusableComponentCommand(component.id))
    }
}

data class SplitCaptionCommand(
    val overlayId: String,
    val leftDurationUs: Long,
    val rightId: String,
    val leftText: String,
    val rightText: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.textOverlays.indexOfFirst { it.id == overlayId }
        require(index >= 0) { "text overlay $overlayId not found" }
        require(project.textOverlays.none { it.id == rightId }) { "text overlay $rightId already exists" }
        val original = project.textOverlays[index]
        val (left, right) = CaptionEditor.split(original, leftDurationUs, rightId, leftText, rightText)
        val next = project.textOverlays.toMutableList().also {
            it[index] = left
            it.add(index + 1, right)
        }
        val edited = validatedDesign(project.copy(textOverlays = next))
        return CommandResult(
            edited,
            ReplaceTextOverlayListCommand(target = project.textOverlays, inverseTarget = next),
        )
    }
}

private data class ReplaceTextOverlayListCommand(
    val target: List<TextOverlay>,
    val inverseTarget: List<TextOverlay>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val edited = validatedDesign(project.copy(textOverlays = target))
        return CommandResult(
            edited,
            ReplaceTextOverlayListCommand(target = inverseTarget, inverseTarget = target),
        )
    }
}

private fun validatedDesign(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid design edit: ${issues.first()}" }
    return project
}
