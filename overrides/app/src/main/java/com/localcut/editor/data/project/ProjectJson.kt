package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.timeline.TimelineValidator

internal class UnsupportedProjectSchemaException(val schemaVersion: Int) : IllegalArgumentException("unsupported schemaVersion=$schemaVersion")

object ProjectJson {
    fun encode(project: Project): String = buildString {
        append('{')
        field("id", jsonString(project.id)); comma()
        field("schemaVersion", BuildContract.PROJECT_SCHEMA_VERSION.toString()); comma()
        field("title", jsonString(project.title)); comma()
        field("width", project.width.toString()); comma()
        field("height", project.height.toString()); comma()
        field("fps", project.fps.toString()); comma()
        append("\"assets\":[")
        project.assets.forEachIndexed { i, asset -> if (i > 0) comma(); appendAsset(asset) }
        append("],\"tracks\":[")
        project.tracks.forEachIndexed { i, track -> if (i > 0) comma(); appendTrack(track) }
        append("],\"textOverlays\":[")
        project.textOverlays.forEachIndexed { i, overlay -> if (i > 0) comma(); appendTextOverlay(overlay) }
        append("],\"transitions\":[")
        project.transitions.forEachIndexed { i, transition -> if (i > 0) comma(); appendTransition(transition) }
        append("]}")
    }

    fun decode(json: String): Project {
        val root = MiniJson.parse(json).asObject()
        val sourceSchemaVersion = root.required("schemaVersion").asInt()
        if (sourceSchemaVersion > BuildContract.PROJECT_SCHEMA_VERSION) {
            throw UnsupportedProjectSchemaException(sourceSchemaVersion)
        }
        require(sourceSchemaVersion in 1..3) {
            "unsupported old schemaVersion=$sourceSchemaVersion"
        }
        val project = Project(
            id = root.required("id").asString(),
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = root.required("title").asString(),
            width = root.required("width").asInt(),
            height = root.required("height").asInt(),
            fps = root.required("fps").asInt(),
            assets = root.required("assets").asArray().values.map { decodeAsset(it.asObject()) },
            tracks = root.required("tracks").asArray().values.map { decodeTrack(it.asObject(), sourceSchemaVersion) },
            textOverlays = if (sourceSchemaVersion >= 3) root.required("textOverlays").asArray().values.map { decodeTextOverlay(it.asObject()) } else emptyList(),
            transitions = if (sourceSchemaVersion >= 3) root.required("transitions").asArray().values.map { decodeTransition(it.asObject()) } else emptyList(),
        )
        val issues = TimelineValidator.validate(project)
        require(issues.isEmpty()) { "invalid project JSON: ${issues.first()}" }
        return project
    }

    private fun StringBuilder.appendAsset(a: Asset) {
        append('{')
        field("id", jsonString(a.id)); comma(); field("type", jsonString(a.type.name)); comma(); field("uri", jsonString(a.uri)); comma()
        field("sourceDurationUs", a.sourceDurationUs?.toString() ?: "null"); comma()
        field("sizeBytes", a.sizeBytes?.toString() ?: "null"); comma()
        field("displayName", jsonString(a.displayName)); comma()
        field("mimeType", a.mimeType?.let(::jsonString) ?: "null"); comma()
        field("fingerprint", jsonString(a.fingerprint))
        append('}')
    }

    private fun StringBuilder.appendTrack(t: Track) {
        append('{')
        field("id", jsonString(t.id)); comma(); field("type", jsonString(t.type.name)); comma(); field("order", t.order.toString()); comma()
        field("collisionPolicy", jsonString(t.collisionPolicy.name)); comma()
        field("muted", t.muted.toString()); comma(); field("hidden", t.hidden.toString()); comma(); field("locked", t.locked.toString()); comma()
        field("solo", t.solo.toString()); comma()
        append("\"clips\":[")
        t.clips.forEachIndexed { i, clip -> if (i > 0) comma(); appendClip(clip) }
        append("]}")
    }

    private fun StringBuilder.appendClip(c: Clip) {
        append('{')
        field("id", jsonString(c.id)); comma(); field("assetId", jsonString(c.assetId)); comma(); field("startUs", c.startUs.toString()); comma()
        field("durationUs", c.durationUs.toString()); comma(); field("sourceInUs", c.sourceInUs.toString()); comma(); field("sourceOutUs", c.sourceOutUs.toString()); comma()
        append("\"transform\":"); appendTransform(c.transform); comma()
        field("opacity", c.opacity.toString()); comma(); field("volume", c.volume.toString()); comma()
        field("fadeInUs", c.fadeInUs.toString()); comma(); field("fadeOutUs", c.fadeOutUs.toString()); comma()
        append("\"keyframes\":[")
        c.keyframes.forEachIndexed { i, k -> if (i > 0) comma(); appendKeyframe(k) }
        append("]}")
    }

    private fun StringBuilder.appendTextOverlay(overlay: TextOverlay) {
        append('{')
        field("id", jsonString(overlay.id)); comma(); field("startUs", overlay.startUs.toString()); comma(); field("durationUs", overlay.durationUs.toString()); comma()
        field("text", jsonString(overlay.text)); comma(); append("\"style\":"); appendTextStyle(overlay.style); comma()
        append("\"transform\":"); appendTransform(overlay.transform); comma(); field("opacity", overlay.opacity.toString()); comma(); field("zIndex", overlay.zIndex.toString())
        append('}')
    }

    private fun StringBuilder.appendTextStyle(style: TextStyle) {
        append('{')
        field("fontSizePx", style.fontSizePx.toString()); comma(); field("alignment", jsonString(style.alignment.name)); comma(); field("fillArgb", style.fillArgb.toString()); comma()
        field("strokeArgb", style.strokeArgb?.toString() ?: "null"); comma(); field("strokeWidthPx", style.strokeWidthPx.toString()); comma()
        field("shadowArgb", style.shadowArgb?.toString() ?: "null"); comma(); field("shadowRadiusPx", style.shadowRadiusPx.toString()); comma()
        field("shadowOffsetX", style.shadowOffsetX.toString()); comma(); field("shadowOffsetY", style.shadowOffsetY.toString())
        append('}')
    }

    private fun StringBuilder.appendTransition(transition: ClipTransition) {
        append('{')
        field("id", jsonString(transition.id)); comma(); field("fromClipId", jsonString(transition.fromClipId)); comma(); field("toClipId", jsonString(transition.toClipId)); comma()
        field("durationUs", transition.durationUs.toString()); comma(); field("kind", jsonString(transition.kind.name))
        append('}')
    }

    private fun StringBuilder.appendKeyframe(k: TransformKeyframe) {
        append("{\"offsetUs\":${k.offsetUs},\"value\":"); appendTransform(k.value); append('}')
    }

    private fun StringBuilder.appendTransform(v: TransformValue) {
        append('{')
        field("x", v.x.toString()); comma(); field("y", v.y.toString()); comma(); field("scale", v.scale.toString()); comma()
        field("rotationDeg", v.rotationDeg.toString()); comma(); field("opacity", v.opacity.toString())
        append('}')
    }

    private fun decodeAsset(o: JObject): Asset = Asset(
        id = o.required("id").asString(),
        type = AssetType.valueOf(o.required("type").asString()),
        uri = o.required("uri").asString(),
        sourceDurationUs = o.optional("sourceDurationUs").nullableLong(),
        sizeBytes = o.optional("sizeBytes").nullableLong(),
        displayName = o.required("displayName").asString(),
        mimeType = o.optional("mimeType").nullableString(),
        fingerprint = o.required("fingerprint").asString()
    )

    private fun decodeTrack(o: JObject, sourceSchemaVersion: Int): Track = Track(
        id = o.required("id").asString(),
        type = TrackType.valueOf(o.required("type").asString()),
        order = o.required("order").asInt(),
        collisionPolicy = if (sourceSchemaVersion >= 2) {
            TrackCollisionPolicy.valueOf(o.required("collisionPolicy").asString())
        } else {
            TrackCollisionPolicy.EXCLUSIVE
        },
        muted = o.required("muted").asBoolean(),
        hidden = o.required("hidden").asBoolean(),
        locked = o.required("locked").asBoolean(),
        clips = o.required("clips").asArray().values.map { decodeClip(it.asObject()) },
        solo = o.optional("solo")?.asBoolean() ?: false,
    )

    private fun decodeClip(o: JObject): Clip = Clip(
        id = o.required("id").asString(),
        assetId = o.required("assetId").asString(),
        startUs = o.required("startUs").asLong(),
        durationUs = o.required("durationUs").asLong(),
        sourceInUs = o.required("sourceInUs").asLong(),
        sourceOutUs = o.required("sourceOutUs").asLong(),
        transform = decodeTransform(o.required("transform").asObject()),
        opacity = o.required("opacity").asDouble(),
        volume = o.required("volume").asDouble(),
        keyframes = o.required("keyframes").asArray().values.map { k ->
            val ko = k.asObject()
            TransformKeyframe(ko.required("offsetUs").asLong(), decodeTransform(ko.required("value").asObject()))
        },
        fadeInUs = o.optional("fadeInUs").nullableLong() ?: 0L,
        fadeOutUs = o.optional("fadeOutUs").nullableLong() ?: 0L,
    )

    private fun decodeTextOverlay(o: JObject): TextOverlay = TextOverlay(
        id = o.required("id").asString(),
        startUs = o.required("startUs").asLong(),
        durationUs = o.required("durationUs").asLong(),
        text = o.required("text").asString(),
        style = decodeTextStyle(o.required("style").asObject()),
        transform = decodeTransform(o.required("transform").asObject()),
        opacity = o.required("opacity").asDouble(),
        zIndex = o.required("zIndex").asInt(),
    )

    private fun decodeTextStyle(o: JObject): TextStyle = TextStyle(
        fontSizePx = o.required("fontSizePx").asDouble(),
        alignment = TextHorizontalAlignment.valueOf(o.required("alignment").asString()),
        fillArgb = o.required("fillArgb").asInt(),
        strokeArgb = o.optional("strokeArgb").nullableInt(),
        strokeWidthPx = o.required("strokeWidthPx").asDouble(),
        shadowArgb = o.optional("shadowArgb").nullableInt(),
        shadowRadiusPx = o.required("shadowRadiusPx").asDouble(),
        shadowOffsetX = o.required("shadowOffsetX").asDouble(),
        shadowOffsetY = o.required("shadowOffsetY").asDouble(),
    )

    private fun decodeTransition(o: JObject): ClipTransition = ClipTransition(
        id = o.required("id").asString(),
        fromClipId = o.required("fromClipId").asString(),
        toClipId = o.required("toClipId").asString(),
        durationUs = o.required("durationUs").asLong(),
        kind = TransitionKind.valueOf(o.required("kind").asString()),
    )

    private fun decodeTransform(o: JObject): TransformValue = TransformValue(
        x = o.required("x").asDouble(),
        y = o.required("y").asDouble(),
        scale = o.required("scale").asDouble(),
        rotationDeg = o.required("rotationDeg").asDouble(),
        opacity = o.required("opacity").asDouble()
    )

    private fun JValue?.nullableLong(): Long? = when (this) { null, JNull -> null; else -> asLong() }
    private fun JValue?.nullableInt(): Int? = when (this) { null, JNull -> null; else -> asInt() }
    private fun JValue?.nullableString(): String? = when (this) { null, JNull -> null; else -> asString() }
    private fun StringBuilder.field(name: String, encodedValue: String) { append(jsonString(name)); append(':'); append(encodedValue) }
    private fun StringBuilder.comma() { append(',') }
}
