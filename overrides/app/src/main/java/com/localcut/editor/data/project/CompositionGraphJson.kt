package com.localcut.editor.data.project

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D

internal object CompositionGraphJson {
    fun encode(graph: CompositionGraph): String = buildString {
        append('{')
        append("\"nodes\":[")
        graph.nodes.forEachIndexed { index, node ->
            if (index > 0) append(',')
            appendNode(node)
        }
        append("],\"cameraCuts\":[")
        graph.cameraCuts.forEachIndexed { index, cut ->
            if (index > 0) append(',')
            append('{')
            field("startUs", cut.startUs.toString()); append(',')
            field("cameraNodeId", jsonString(cut.cameraNodeId))
            append('}')
        }
        append("]}")
    }

    fun decode(root: JObject): CompositionGraph = CompositionGraph(
        nodes = root.required("nodes").asArray().values.map { decodeNode(it.asObject()) },
        cameraCuts = root.required("cameraCuts").asArray().values.map { raw ->
            val cut = raw.asObject()
            CameraCut(
                startUs = cut.required("startUs").asLong(),
                cameraNodeId = cut.required("cameraNodeId").asString(),
            )
        },
    )

    private fun StringBuilder.appendNode(node: CompositionNode) {
        append('{')
        field("id", jsonString(node.id)); append(',')
        field("kind", jsonString(node.kind.name)); append(',')
        field("parentId", node.parentId?.let(::jsonString) ?: "null"); append(',')
        field("clipId", node.clipId?.let(::jsonString) ?: "null"); append(',')
        append("\"transform\":"); appendTransform(node.transform); append(',')
        append("\"camera\":")
        val camera = node.camera
        if (camera == null) {
            append("null")
        } else {
            append('{')
            field("focalLength", camera.focalLength.toString()); append(',')
            field("orthographic", camera.orthographic.toString())
            append('}')
        }
        append(','); field("visible", node.visible.toString())
        append(','); field("locked", node.locked.toString())
        append('}')
    }

    private fun StringBuilder.appendTransform(transform: LayerTransform3D) {
        append('{')
        field("x", transform.x.toString()); append(',')
        field("y", transform.y.toString()); append(',')
        field("z", transform.z.toString()); append(',')
        field("scale", transform.scale.toString()); append(',')
        field("rotationXDeg", transform.rotationXDeg.toString()); append(',')
        field("rotationYDeg", transform.rotationYDeg.toString()); append(',')
        field("rotationZDeg", transform.rotationZDeg.toString()); append(',')
        field("opacity", transform.opacity.toString())
        append('}')
    }

    private fun decodeNode(root: JObject): CompositionNode = CompositionNode(
        id = root.required("id").asString(),
        kind = CompositionNodeKind.valueOf(root.required("kind").asString()),
        parentId = root.optional("parentId").nullableString(),
        clipId = root.optional("clipId").nullableString(),
        transform = decodeTransform(root.required("transform").asObject()),
        camera = root.optional("camera").let { raw ->
            if (raw == null || raw === JNull) null else {
                val camera = raw.asObject()
                CameraSettings(
                    focalLength = camera.required("focalLength").asDouble(),
                    orthographic = camera.required("orthographic").asBoolean(),
                )
            }
        },
        visible = root.optional("visible")?.asBoolean() ?: true,
        locked = root.optional("locked")?.asBoolean() ?: false,
    )

    private fun decodeTransform(root: JObject): LayerTransform3D = LayerTransform3D(
        x = root.required("x").asDouble(),
        y = root.required("y").asDouble(),
        z = root.required("z").asDouble(),
        scale = root.required("scale").asDouble(),
        rotationXDeg = root.required("rotationXDeg").asDouble(),
        rotationYDeg = root.required("rotationYDeg").asDouble(),
        rotationZDeg = root.required("rotationZDeg").asDouble(),
        opacity = root.required("opacity").asDouble(),
    )

    private fun JValue?.nullableString(): String? = when (this) {
        null, JNull -> null
        else -> asString()
    }

    private fun StringBuilder.field(name: String, encodedValue: String) {
        append(jsonString(name)); append(':'); append(encodedValue)
    }
}
