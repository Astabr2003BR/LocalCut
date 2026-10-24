package com.localcut.editor.domain.model

sealed interface EffectParameter {
    data class Number(val value: Double) : EffectParameter
    data class Color(val argb: Int) : EffectParameter
    data class Bool(val value: Boolean) : EffectParameter
    data class Text(val value: String) : EffectParameter
    data class Point(val x: Double, val y: Double) : EffectParameter

    fun validationIssues(): List<String> = when (this) {
        is Number -> if (value.isFinite()) emptyList() else listOf("effect number must be finite")
        is Point -> if (x.isFinite() && y.isFinite()) emptyList() else listOf("effect point values must be finite")
        is Color, is Bool, is Text -> emptyList()
    }
}

data class EffectInstance(
    val id: String,
    val typeId: String,
    val enabled: Boolean = true,
    val parameters: Map<String, EffectParameter> = emptyMap(),
    val animations: List<PropertyAnimation> = emptyList(),
) {
    fun orderedParameters(): List<Pair<String, EffectParameter>> =
        parameters.entries.sortedBy { it.key }.map { it.key to it.value }

    fun validationIssues(): List<String> = buildList {
        if (id.isBlank()) add("effect id must not be blank")
        if (typeId.isBlank()) add("effect typeId must not be blank")
        if (parameters.keys.any { it.isBlank() }) add("effect parameter names must not be blank")
        orderedParameters().forEach { (_, value) -> addAll(value.validationIssues()) }
        animations.forEach { addAll(it.validationIssues()) }
    }
}
