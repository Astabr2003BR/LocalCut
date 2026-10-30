package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CanvasGuide
import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ReusableComponent
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath

enum class DesignToolsSection {
    ELEMENTS,
    CANVAS,
    COMPONENTS,
}

enum class DesignCanvasPreset(
    val label: String,
    val width: Int,
    val height: Int,
) {
    VERTICAL_9_16("9:16", 1080, 1920),
    LANDSCAPE_16_9("16:9", 1920, 1080),
    SQUARE_1_1("1:1", 1080, 1080),
    PORTRAIT_4_5("4:5", 1080, 1350),
}

enum class DesignShapePreset(val label: String) {
    STAR("Estrela"),
    DIAMOND("Losango"),
    ARROW("Seta"),
    HEXAGON("Hexágono"),
}

object DesignToolsUiPolicy {
    const val DEFAULT_DURATION_US: Long = 3_000_000L
    const val MAX_STROKE_WIDTH_PX: Double = 200.0
    const val MAX_CORNER_RADIUS_PX: Double = 300.0
    private const val DUPLICATE_OFFSET_PX: Double = 24.0
    private const val CENTER_X_GUIDE_ID = "design-center-x"
    private const val CENTER_Y_GUIDE_ID = "design-center-y"

    fun mobileSections(): List<DesignToolsSection> = listOf(
        DesignToolsSection.ELEMENTS,
        DesignToolsSection.CANVAS,
        DesignToolsSection.COMPONENTS,
    )

    fun canvasPresets(): List<DesignCanvasPreset> = listOf(
        DesignCanvasPreset.VERTICAL_9_16,
        DesignCanvasPreset.LANDSCAPE_16_9,
        DesignCanvasPreset.SQUARE_1_1,
        DesignCanvasPreset.PORTRAIT_4_5,
    )

    fun shapePresets(): List<DesignShapePreset> = DesignShapePreset.entries

    fun isCanvasPresetActive(project: Project, preset: DesignCanvasPreset): Boolean =
        project.width == preset.width && project.height == preset.height

    fun newShape(project: Project, kind: ShapeKind, playheadUs: Long): DesignElement {
        require(playheadUs >= 0L) { "playheadUs must be non-negative" }
        val id = nextId("shape", project.designElements.map { it.id }.toSet())
        val nextZ = (project.designElements.maxOfOrNull { it.zIndex } ?: -1) + 1
        val shape = when (kind) {
            ShapeKind.RECTANGLE -> ShapeSpec(
                kind = kind,
                fillArgb = 0xFFFFFFFF.toInt(),
                cornerRadiusPx = 24.0,
            )
            ShapeKind.ELLIPSE -> ShapeSpec(kind = kind, fillArgb = 0xFFFFFFFF.toInt())
            ShapeKind.LINE -> ShapeSpec(
                kind = kind,
                fillArgb = 0xFFFFFFFF.toInt(),
                strokeWidthPx = 6.0,
            )
            ShapeKind.PATH -> ShapeSpec(
                kind = kind,
                path = VectorPath(
                    points = listOf(
                        PathPoint(0.5, 0.05),
                        PathPoint(0.95, 0.95),
                        PathPoint(0.05, 0.95),
                    ),
                    closed = true,
                ),
                fillArgb = 0xFFFFFFFF.toInt(),
            )
        }
        return DesignElement(
            id = id,
            startUs = playheadUs,
            durationUs = DEFAULT_DURATION_US,
            shape = shape,
            zIndex = nextZ,
        )
    }

    fun newShapePreset(
        project: Project,
        preset: DesignShapePreset,
        playheadUs: Long,
    ): DesignElement {
        require(playheadUs >= 0L) { "playheadUs must be non-negative" }
        val id = nextId("shape", project.designElements.map { it.id }.toSet())
        val nextZ = (project.designElements.maxOfOrNull { it.zIndex } ?: -1) + 1
        val points = when (preset) {
            DesignShapePreset.STAR -> listOf(
                PathPoint(0.50, 0.04), PathPoint(0.61, 0.35),
                PathPoint(0.95, 0.36), PathPoint(0.68, 0.56),
                PathPoint(0.78, 0.90), PathPoint(0.50, 0.70),
                PathPoint(0.22, 0.90), PathPoint(0.32, 0.56),
                PathPoint(0.05, 0.36), PathPoint(0.39, 0.35),
            )
            DesignShapePreset.DIAMOND -> listOf(
                PathPoint(0.50, 0.05),
                PathPoint(0.95, 0.50),
                PathPoint(0.50, 0.95),
                PathPoint(0.05, 0.50),
            )
            DesignShapePreset.ARROW -> listOf(
                PathPoint(0.05, 0.35),
                PathPoint(0.62, 0.35),
                PathPoint(0.62, 0.12),
                PathPoint(0.95, 0.50),
                PathPoint(0.62, 0.88),
                PathPoint(0.62, 0.65),
                PathPoint(0.05, 0.65),
            )
            DesignShapePreset.HEXAGON -> listOf(
                PathPoint(0.25, 0.07),
                PathPoint(0.75, 0.07),
                PathPoint(0.96, 0.50),
                PathPoint(0.75, 0.93),
                PathPoint(0.25, 0.93),
                PathPoint(0.04, 0.50),
            )
        }
        val element = DesignElement(
            id = id,
            startUs = playheadUs,
            durationUs = DEFAULT_DURATION_US,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(points = points, closed = true),
                fillArgb = 0xFFFFFFFF.toInt(),
            ),
            zIndex = nextZ,
        )
        val issues = element.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return element
    }

    fun duplicateElement(project: Project, element: DesignElement): DesignElement {
        require(project.designElements.any { it.id == element.id }) { "design element ${element.id} not found" }
        val existingIds = project.designElements.mapTo(hashSetOf()) { it.id }
        val nextZ = project.designElements.maxOfOrNull { it.zIndex }?.let { maxZ ->
            require(maxZ < Int.MAX_VALUE) { "cannot duplicate element above max z-index" }
            maxZ + 1
        } ?: 0
        return element.copy(
            id = nextId("shape", existingIds),
            transform = element.transform.copy(
                x = element.transform.x + DUPLICATE_OFFSET_PX,
                y = element.transform.y + DUPLICATE_OFFSET_PX,
            ),
            zIndex = nextZ,
        )
    }

    fun setShapeFill(element: DesignElement, fillArgb: Int?): DesignElement =
        updateShape(element) { shape -> shape.copy(fillArgb = fillArgb) }

    fun setShapeStrokeColor(element: DesignElement, strokeArgb: Int?): DesignElement =
        updateShape(element) { shape -> shape.copy(strokeArgb = strokeArgb) }

    fun setShapeStrokeWidth(element: DesignElement, strokeWidthPx: Double): DesignElement {
        require(strokeWidthPx.isFinite()) { "shape stroke width must be finite" }
        return updateShape(element) { shape ->
            shape.copy(strokeWidthPx = strokeWidthPx.coerceIn(0.0, MAX_STROKE_WIDTH_PX))
        }
    }

    fun setShapeCornerRadius(element: DesignElement, cornerRadiusPx: Double): DesignElement {
        require(cornerRadiusPx.isFinite()) { "shape corner radius must be finite" }
        return updateShape(element) { shape ->
            shape.copy(cornerRadiusPx = cornerRadiusPx.coerceIn(0.0, MAX_CORNER_RADIUS_PX))
        }
    }

    fun setElementOpacity(element: DesignElement, opacity: Double): DesignElement {
        require(opacity.isFinite()) { "design element opacity must be finite" }
        return element.copy(opacity = opacity.coerceIn(0.0, 1.0))
    }

    fun alignCenterX(element: DesignElement): DesignElement =
        element.copy(transform = element.transform.copy(x = 0.0))

    fun alignCenterY(element: DesignElement): DesignElement =
        element.copy(transform = element.transform.copy(y = 0.0))

    fun alignCenter(element: DesignElement): DesignElement =
        element.copy(transform = element.transform.copy(x = 0.0, y = 0.0))

    fun bringToFront(project: Project, element: DesignElement): DesignElement {
        require(project.designElements.any { it.id == element.id }) { "design element ${element.id} not found" }
        val maxZ = project.designElements.maxOfOrNull { it.zIndex } ?: element.zIndex
        if (project.designElements.size <= 1 || element.zIndex == maxZ) return element
        require(maxZ < Int.MAX_VALUE) { "cannot bring element further forward" }
        return element.copy(zIndex = maxZ + 1)
    }

    fun sendToBack(project: Project, element: DesignElement): DesignElement {
        require(project.designElements.any { it.id == element.id }) { "design element ${element.id} not found" }
        val minZ = project.designElements.minOfOrNull { it.zIndex } ?: element.zIndex
        if (project.designElements.size <= 1 || element.zIndex == minZ) return element
        require(minZ > Int.MIN_VALUE) { "cannot send element further backward" }
        return element.copy(zIndex = minZ - 1)
    }

    fun toggleCenterGuides(settings: DesignCanvasSettings): DesignCanvasSettings {
        val centerIds = setOf(CENTER_X_GUIDE_ID, CENTER_Y_GUIDE_ID)
        val hasBoth = settings.guides.count { it.id in centerIds } == 2
        val retained = settings.guides.filterNot { it.id in centerIds }
        return if (hasBoth) {
            settings.copy(guides = retained)
        } else {
            settings.copy(
                guides = retained + listOf(
                    CanvasGuide(CENTER_X_GUIDE_ID, GuideAxis.VERTICAL, 0.5),
                    CanvasGuide(CENTER_Y_GUIDE_ID, GuideAxis.HORIZONTAL, 0.5),
                ),
            )
        }
    }

    fun componentFromAllShapes(project: Project): ReusableComponent? {
        val ids = project.designElements.filter { it.shape != null }.map { it.id }
        if (ids.isEmpty()) return null
        return ReusableComponent(
            id = nextId("component", project.reusableComponents.map { it.id }.toSet()),
            name = "Componente ${project.reusableComponents.size + 1}",
            elementIds = ids,
        )
    }

    fun nextInstancePrefix(project: Project, componentId: String): String {
        require(componentId.isNotBlank()) { "componentId must not be blank" }
        var index = 1L
        while (true) {
            val prefix = "$componentId-instance-$index"
            val occupied = project.designElements.any { it.id.startsWith("$prefix-") }
            if (!occupied) return prefix
            check(index < Long.MAX_VALUE) { "unable to allocate component instance prefix" }
            index++
        }
    }

    fun instantiateComponentElements(
        project: Project,
        component: ReusableComponent,
        instancePrefix: String,
        startUs: Long,
    ): List<DesignElement> {
        require(instancePrefix.isNotBlank()) { "instance prefix must not be blank" }
        require(startUs >= 0L) { "instance start must be non-negative" }
        val byId = project.designElements.associateBy { it.id }
        val sources = component.elementIds.map { id ->
            byId[id] ?: throw IllegalArgumentException("component ${component.id} references missing element $id")
        }
        require(sources.isNotEmpty()) { "component must contain elements" }
        val earliestStart = sources.minOf { it.startUs }
        val minZ = sources.minOf { it.zIndex }
        val baseZ = (project.designElements.maxOfOrNull { it.zIndex } ?: -1) + 1
        val existingIds = project.designElements.map { it.id }.toHashSet()
        return sources.map { source ->
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
    }

    private fun updateShape(
        element: DesignElement,
        transform: (ShapeSpec) -> ShapeSpec,
    ): DesignElement {
        val shape = requireNotNull(element.shape) { "design element ${element.id} is not a shape" }
        return element.copy(shape = transform(shape))
    }

    private fun nextId(prefix: String, existing: Set<String>): String {
        var index = 1L
        while (true) {
            val candidate = "$prefix-$index"
            if (candidate !in existing) return candidate
            check(index < Long.MAX_VALUE) { "unable to allocate $prefix id" }
            index++
        }
    }
}
