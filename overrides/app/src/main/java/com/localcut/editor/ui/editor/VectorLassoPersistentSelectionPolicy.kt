package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.DesignElement

object VectorLassoPersistentSelectionPolicy {
    fun capture(ids: List<String>): List<String> {
        require(ids.all { it.isNotBlank() }) { "lasso selection ids must not be blank" }
        require(ids.distinct().size == ids.size) { "lasso selection ids must be unique" }
        return ids.toList()
    }

    fun existing(
        selectedIds: List<String>,
        elements: List<DesignElement>,
    ): List<String> {
        val captured = capture(selectedIds)
        val existingIds = elements.mapTo(hashSetOf()) { it.id }
        return captured.filter { it in existingIds }
    }
}
