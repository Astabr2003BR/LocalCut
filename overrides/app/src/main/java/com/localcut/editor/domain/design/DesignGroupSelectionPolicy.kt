package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignGroup

object DesignGroupSelectionPolicy {
    fun expand(
        selectedIds: List<String>,
        groups: List<DesignGroup>,
        existingElementIds: List<String>,
    ): List<String> {
        if (selectedIds.isEmpty() || existingElementIds.isEmpty()) return emptyList()
        val existing = existingElementIds.toSet()
        val seeds = selectedIds.asSequence().filter { it in existing }.toSet()
        if (seeds.isEmpty()) return emptyList()

        val expanded = seeds.toMutableSet()
        groups.asSequence()
            .filter { group -> group.elementIds.any { it in seeds } }
            .flatMap { it.elementIds.asSequence() }
            .filter { it in existing }
            .forEach(expanded::add)

        return existingElementIds.filter { it in expanded }
    }

    fun exactGroup(
        selectedIds: List<String>,
        groups: List<DesignGroup>,
    ): DesignGroup? {
        val selected = selectedIds.toSet()
        if (selected.isEmpty() || selected.size != selectedIds.distinct().size) return null
        return groups.singleOrNull { group ->
            group.elementIds.size == selected.size && group.elementIds.toSet() == selected
        }
    }

    fun canCreateGroup(
        selectedIds: List<String>,
        groups: List<DesignGroup>,
    ): Boolean {
        val selected = selectedIds.distinct()
        if (selected.size < 2) return false
        val selectedSet = selected.toSet()
        return groups.none { group -> group.elementIds.any { it in selectedSet } }
    }

    fun containsGroupedMember(
        selectedIds: List<String>,
        groups: List<DesignGroup>,
    ): Boolean {
        if (selectedIds.isEmpty()) return false
        val selected = selectedIds.toSet()
        return groups.any { group -> group.elementIds.any { it in selected } }
    }
}
