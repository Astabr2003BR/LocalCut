package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TransformDesignElementsCommand(
    val elementIds: List<String>,
    val pivotX: Double,
    val pivotY: Double,
    val scaleFactor: Double = 1.0,
    val rotationDeltaDeg: Double = 0.0,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(elementIds.isNotEmpty()) { "design transform selection must not be empty" }
        require(elementIds.all { it.isNotBlank() }) { "design transform ids must not be blank" }
        require(elementIds.distinct().size == elementIds.size) { "design transform ids must be unique" }
        require(pivotX.isFinite() && pivotY.isFinite()) { "design transform pivot must be finite" }
        require(scaleFactor.isFinite() && scaleFactor > 0.0) { "design scale factor must be finite and positive" }
        require(rotationDeltaDeg.isFinite()) { "design rotation delta must be finite" }
        require(scaleFactor != 1.0 || rotationDeltaDeg != 0.0) { "design transform must not be a no-op" }
        require(project.width > 0 && project.height > 0) { "project canvas must be positive" }

        val requested = elementIds.toSet()
        val existingIds = project.designElements.mapTo(linkedSetOf()) { it.id }
        val missing = requested.filterNot { it in existingIds }
        require(missing.isEmpty()) { "design elements not found: ${missing.joinToString()}" }

        val radians = rotationDeltaDeg * PI / 180.0
        val cosine = cos(radians)
        val sine = sin(radians)
        val canvasCenterX = project.width / 2.0
        val canvasCenterY = project.height / 2.0
        val previous = project.designElements
        val next = previous.map { element ->
            if (element.id !in requested) return@map element

            val centerX = canvasCenterX + element.transform.x
            val centerY = canvasCenterY + element.transform.y
            val relativeX = (centerX - pivotX) * scaleFactor
            val relativeY = (centerY - pivotY) * scaleFactor
            val rotatedX = relativeX * cosine - relativeY * sine
            val rotatedY = relativeX * sine + relativeY * cosine
            val nextX = pivotX + rotatedX - canvasCenterX
            val nextY = pivotY + rotatedY - canvasCenterY
            val nextScale = element.transform.scale * scaleFactor
            val nextRotation = element.transform.rotationDeg + rotationDeltaDeg

            require(
                nextX.isFinite() && nextY.isFinite() && nextScale.isFinite() && nextScale > 0.0 && nextRotation.isFinite()
            ) { "design transform overflow" }

            element.copy(
                transform = element.transform.copy(
                    x = nextX,
                    y = nextY,
                    scale = nextScale,
                    rotationDeg = nextRotation,
                ),
            )
        }

        val edited = validatedSelectionTransform(project.copy(designElements = next))
        return CommandResult(
            project = edited,
            inverse = ReplaceTransformedDesignElementListCommand(
                target = previous,
                inverseTarget = next,
            ),
        )
    }
}

private data class ReplaceTransformedDesignElementListCommand(
    val target: List<DesignElement>,
    val inverseTarget: List<DesignElement>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val edited = validatedSelectionTransform(project.copy(designElements = target))
        return CommandResult(
            project = edited,
            inverse = ReplaceTransformedDesignElementListCommand(
                target = inverseTarget,
                inverseTarget = target,
            ),
        )
    }
}

private fun validatedSelectionTransform(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid design selection transform: ${issues.first()}" }
    return project
}
