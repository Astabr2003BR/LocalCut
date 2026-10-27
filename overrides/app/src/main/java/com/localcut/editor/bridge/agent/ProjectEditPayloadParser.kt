package com.localcut.editor.bridge.agent

import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import com.localcut.editor.domain.history.ProjectEditBatchCommand
import com.localcut.editor.domain.history.ProjectEditOperation
import com.localcut.editor.domain.history.ProjectItemKind
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath

internal object ProjectEditPayloadParser {
    private const val MAX_PAYLOAD_CHARS = 16_384
    private const val MAX_PATH_POINTS = 256

    fun parse(payloadJson: String): List<ProjectEditOperation> {
        require(payloadJson.isNotBlank() && payloadJson.length <= MAX_PAYLOAD_CHARS) { "project edit payload size invalid" }
        val root = MiniJson.parse(payloadJson).asObject()
        root.requireOnly("operations")
        val rawOperations = root.required("operations").asArray().values
        require(rawOperations.isNotEmpty()) { "project edit requires operations" }
        require(rawOperations.size <= ProjectEditBatchCommand.MAX_OPERATIONS) { "too many project edit operations" }
        return rawOperations.mapIndexed { index, raw ->
            runCatching { parseOperation(raw.asObject()) }
                .getOrElse { throw IllegalArgumentException("invalid project edit operation ${index + 1}: ${it.message}", it) }
        }
    }

    private fun parseOperation(item: JObject): ProjectEditOperation = when (val op = item.requiredString("op")) {
        "deleteByKind" -> {
            item.requireOnly("op", "kinds")
            val kinds = item.required("kinds").asArray().values.map { raw ->
                runCatching { ProjectItemKind.valueOf(raw.asString().trim().uppercase()) }
                    .getOrElse { throw IllegalArgumentException("unsupported project item kind") }
            }.toSet()
            ProjectEditOperation.DeleteByKind(kinds)
        }
        "deleteClip" -> {
            item.requireOnly("op", "clipId")
            ProjectEditOperation.DeleteClip(item.requiredNonBlankString("clipId"))
        }
        "deleteText" -> {
            item.requireOnly("op", "overlayId")
            ProjectEditOperation.DeleteText(item.requiredNonBlankString("overlayId"))
        }
        "deleteDesign" -> {
            item.requireOnly("op", "elementId")
            ProjectEditOperation.DeleteDesign(item.requiredNonBlankString("elementId"))
        }
        "addDesign" -> {
            item.requireOnly(
                "op", "id", "startUs", "durationUs", "kind", "points", "closed", "fillArgb", "strokeArgb",
                "strokeWidthPx", "cornerRadiusPx", "x", "y", "scale", "rotationDeg", "opacity", "zIndex",
            )
            val kind = runCatching { ShapeKind.valueOf(item.requiredNonBlankString("kind").uppercase()) }
                .getOrElse { throw IllegalArgumentException("unsupported design shape kind") }
            val points = item.optional("points")?.asArray()?.values?.map { raw ->
                val point = raw.asObject()
                point.requireOnly("x", "y")
                PathPoint(point.requiredDouble("x"), point.requiredDouble("y"))
            }
            if (points != null) require(points.size in 2..MAX_PATH_POINTS) { "design path point count invalid" }
            require(kind == ShapeKind.PATH || points == null) { "points are only allowed for PATH" }
            if (kind == ShapeKind.PATH) require(points != null) { "PATH requires points" }
            val closed = item.optionalBoolean("closed") ?: false
            val shape = ShapeSpec(
                kind = kind,
                path = points?.let { VectorPath(it, closed) },
                fillArgb = item.optionalArgb("fillArgb") ?: if (kind == ShapeKind.PATH && !closed) null else 0xFFFFFFFF.toInt(),
                strokeArgb = item.optionalArgb("strokeArgb") ?: if (kind == ShapeKind.PATH && !closed) 0xFFFFFFFF.toInt() else null,
                strokeWidthPx = item.optionalDouble("strokeWidthPx") ?: if (kind == ShapeKind.LINE) 6.0 else 0.0,
                cornerRadiusPx = item.optionalDouble("cornerRadiusPx") ?: 0.0,
            )
            ProjectEditOperation.AddDesign(
                DesignElement(
                    id = item.requiredNonBlankString("id"),
                    startUs = item.requiredLong("startUs"),
                    durationUs = item.requiredLong("durationUs"),
                    shape = shape,
                    transform = TransformValue(
                        x = item.optionalDouble("x") ?: 0.0,
                        y = item.optionalDouble("y") ?: 0.0,
                        scale = item.optionalDouble("scale") ?: 1.0,
                        rotationDeg = item.optionalDouble("rotationDeg") ?: 0.0,
                    ),
                    opacity = item.optionalDouble("opacity") ?: 1.0,
                    zIndex = item.optionalInt("zIndex") ?: 0,
                ),
            )
        }
        "addText" -> {
            item.requireOnly(
                "op", "id", "text", "startUs", "durationUs", "x", "y", "scale", "rotationDeg",
                "opacity", "fontSizePx", "fillArgb", "zIndex",
            )
            val transform = TransformValue(
                x = item.optionalDouble("x") ?: 0.0,
                y = item.optionalDouble("y") ?: 0.0,
                scale = item.optionalDouble("scale") ?: 1.0,
                rotationDeg = item.optionalDouble("rotationDeg") ?: 0.0,
            )
            val overlay = TextOverlay(
                id = item.requiredNonBlankString("id"),
                startUs = item.requiredLong("startUs"),
                durationUs = item.requiredLong("durationUs"),
                text = item.requiredNonBlankString("text"),
                style = TextStyle(
                    fontSizePx = item.optionalDouble("fontSizePx") ?: 64.0,
                    fillArgb = item.optionalArgb("fillArgb") ?: 0xFFFFFFFF.toInt(),
                ),
                transform = transform,
                opacity = item.optionalDouble("opacity") ?: 1.0,
                zIndex = item.optionalInt("zIndex") ?: 0,
            )
            ProjectEditOperation.AddText(overlay)
        }
        "addClip" -> {
            item.requireOnly(
                "op", "clipId", "assetId", "trackId", "startUs", "durationUs", "sourceInUs",
                "x", "y", "scale", "rotationDeg", "opacity", "volume",
            )
            ProjectEditOperation.AddClip(
                clipId = item.requiredNonBlankString("clipId"),
                assetId = item.requiredNonBlankString("assetId"),
                trackId = item.requiredNonBlankString("trackId"),
                startUs = item.requiredLong("startUs"),
                durationUs = item.requiredLong("durationUs"),
                sourceInUs = item.optionalLong("sourceInUs") ?: 0L,
                x = item.optionalDouble("x") ?: 0.0,
                y = item.optionalDouble("y") ?: 0.0,
                scale = item.optionalDouble("scale") ?: 1.0,
                rotationDeg = item.optionalDouble("rotationDeg") ?: 0.0,
                opacity = item.optionalDouble("opacity") ?: 1.0,
                volume = item.optionalDouble("volume") ?: 1.0,
            )
        }
        "patchText" -> {
            item.requireOnly(
                "op", "overlayId", "text", "startUs", "durationUs", "x", "y", "scale", "rotationDeg",
                "opacity", "fontSizePx", "fillArgb", "zIndex",
            )
            ProjectEditOperation.PatchText(
                overlayId = item.requiredNonBlankString("overlayId"),
                text = item.optionalString("text"),
                startUs = item.optionalLong("startUs"),
                durationUs = item.optionalLong("durationUs"),
                x = item.optionalDouble("x"),
                y = item.optionalDouble("y"),
                scale = item.optionalDouble("scale"),
                rotationDeg = item.optionalDouble("rotationDeg"),
                opacity = item.optionalDouble("opacity"),
                fontSizePx = item.optionalDouble("fontSizePx"),
                fillArgb = item.optionalArgb("fillArgb"),
                zIndex = item.optionalInt("zIndex"),
            )
        }
        "patchDesign" -> {
            item.requireOnly(
                "op", "elementId", "startUs", "durationUs", "x", "y", "scale", "rotationDeg", "opacity",
                "zIndex", "fillArgb", "strokeArgb", "strokeWidthPx", "cornerRadiusPx",
            )
            ProjectEditOperation.PatchDesign(
                elementId = item.requiredNonBlankString("elementId"),
                startUs = item.optionalLong("startUs"),
                durationUs = item.optionalLong("durationUs"),
                x = item.optionalDouble("x"),
                y = item.optionalDouble("y"),
                scale = item.optionalDouble("scale"),
                rotationDeg = item.optionalDouble("rotationDeg"),
                opacity = item.optionalDouble("opacity"),
                zIndex = item.optionalInt("zIndex"),
                fillArgb = item.optionalArgb("fillArgb"),
                strokeArgb = item.optionalArgb("strokeArgb"),
                strokeWidthPx = item.optionalDouble("strokeWidthPx"),
                cornerRadiusPx = item.optionalDouble("cornerRadiusPx"),
            )
        }
        "patchClip" -> {
            item.requireOnly("op", "clipId", "startUs", "x", "y", "scale", "rotationDeg", "opacity", "volume")
            ProjectEditOperation.PatchClip(
                clipId = item.requiredNonBlankString("clipId"),
                startUs = item.optionalLong("startUs"),
                x = item.optionalDouble("x"),
                y = item.optionalDouble("y"),
                scale = item.optionalDouble("scale"),
                rotationDeg = item.optionalDouble("rotationDeg"),
                opacity = item.optionalDouble("opacity"),
                volume = item.optionalDouble("volume"),
            )
        }
        else -> throw IllegalArgumentException("unsupported project edit op '$op'")
    }

    private fun JObject.requireOnly(vararg keys: String) {
        val allowed = keys.toSet()
        val unknown = fields.keys.filterNot { it in allowed }
        require(unknown.isEmpty()) { "unknown fields: ${unknown.joinToString()}" }
    }

    private fun JObject.requiredString(key: String): String = required(key).asString()
    private fun JObject.requiredNonBlankString(key: String): String = requiredString(key).also {
        require(it.isNotBlank()) { "$key must not be blank" }
    }
    private fun JObject.requiredLong(key: String): Long = required(key).asLong()
    private fun JObject.requiredDouble(key: String): Double = required(key).asDouble()
    private fun JObject.optionalString(key: String): String? = optional(key)?.asString()
    private fun JObject.optionalLong(key: String): Long? = optional(key)?.asLong()
    private fun JObject.optionalDouble(key: String): Double? = optional(key)?.asDouble()
    private fun JObject.optionalBoolean(key: String): Boolean? = optional(key)?.asBoolean()
    private fun JObject.optionalInt(key: String): Int? = optionalLong(key)?.also {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$key out of range" }
    }?.toInt()
    private fun JObject.optionalArgb(key: String): Int? = optionalLong(key)?.also {
        require(it in Int.MIN_VALUE.toLong()..0xFFFFFFFFL) { "$key out of range" }
    }?.toInt()
}
