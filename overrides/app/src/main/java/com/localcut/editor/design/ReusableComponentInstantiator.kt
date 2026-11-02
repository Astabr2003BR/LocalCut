package com.localcut.editor.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ReusableComponent

object ReusableComponentInstantiator {
    fun instantiate(
        component: ReusableComponent,
        sourceElements: List<DesignElement>,
        instanceId: String,
        startUs: Long,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
    ): List<DesignElement> {
        require(instanceId.isNotBlank()) { "instanceId must not be blank" }
        require(startUs >= 0L) { "startUs must be non-negative" }
        require(offsetX.isFinite() && offsetY.isFinite()) { "component offsets must be finite" }

        val byId = sourceElements.associateBy { it.id }
        val validation = component.validationIssues(byId.keys)
        require(validation.isEmpty()) { "invalid reusable component: ${validation.first()}" }

        val selected = component.elementIds.map { id ->
            requireNotNull(byId[id]) { "component element $id not found" }
        }
        val baseStartUs = selected.minOf { it.startUs }

        return selected.map { element ->
            val relativeStartUs = element.startUs - baseStartUs
            require(relativeStartUs >= 0L) { "component element timing underflow" }
            require(startUs <= Long.MAX_VALUE - relativeStartUs) { "component instance timing overflow" }
            element.copy(
                id = "$instanceId:${element.id}",
                startUs = startUs + relativeStartUs,
                transform = element.transform.copy(
                    x = element.transform.x + offsetX,
                    y = element.transform.y + offsetY,
                ),
            )
        }.also { result ->
            require(result.map { it.id }.distinct().size == result.size) { "component instance ids must be unique" }
            result.forEach { element ->
                require(element.validationIssues().isEmpty()) {
                    "invalid component instance element ${element.id}: ${element.validationIssues().first()}"
                }
            }
        }
    }
}
