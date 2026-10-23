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
        append("],\"compositionGraph\":")
        append(CompositionGraphJson.encode(project.compositionGraph)); comma()
        append("\"designElements\":[")
        project.designElements.forEachIndexed { i, element -> if (i > 0) comma(); appendDesignElement(element) }
        append("],\"designCanvas\":"); appendDesignCanvas(project.designCanvas); comma()
        append("\"reusableComponents\":[")
        project.reusableComponents.forEachIndexed { i, component -> if (i > 0) comma(); appendReusableComponent(component) }
        append("]}")
    }

    fun decode(json: String): Project {
        val root = MiniJson.parse(json).asObject()
        val sourceSchemaVersion = root.required("schemaVersion").asInt()
        if (sourceSchemaVersion > BuildContract.PROJECT_SCHEMA_VERSION) {
            throw UnsupportedProjectSchemaException(sourceSchemaVersion)
        }
        require(sourceSchemaVersion in 1..5) {
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
            textOverlays = if (sourceSchemaVersion >= 3) root.required("textOverlays").asArray().values.map { decodeTextOverlay(it.asObject(), sourceSchemaVersion) } else emptyList(),
            transitions = if (sourceSchemaVersion >= 3) root.required("transitions").asArray().values.map { decodeTransition(it.asObject()) } else emptyList(),
            compositionGraph = if (sourceSchemaVersion >= 4) {
                root.optional("compositionGraph")?.let { CompositionGraphJson.decode(it.asObject()) } ?: CompositionGraph()
            } else {
                CompositionGraph()
            },
            designElements = if (sourceSchemaVersion >= 5) {
                root.required("designElements").asArray().values.map { decodeDesignElement(it.asObject()) }
            } else emptyList(),
            designCanvas = if (sourceSchemaVersion >= 5) {
                decodeDesignCanvas(root.required("designCanvas").asObject())
            } else DesignCanvasSettings(),
            reusableComponents = if (sourceSchemaVersion >= 5) {
                root.required("reusableComponents").asArray().values.map { decodeReusableComponent(it.asObject()) }
            } else emptyList(),
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
        append("],\"propertyAnimations\":[")
        c.propertyAnimations.forEachIndexed { i, animation -> if (i > 0) comma(); appendPropertyAnimation(animation) }
        append("],\"compositing\":"); appendCompositing(c.compositing); comma()
        append("\"effects\":[")
        c.effects.forEachIndexed { i, effect -> if (i > 0) comma(); appendEffect(effect) }
        append("],\"timeMap\":"); appendTimeMap(c.timeMap)
        append('}')
    }

    private fun StringBuilder.appendTextOverlay(overlay: TextOverlay) {
        append('{')
        field("id", jsonString(overlay.id)); comma(); field("startUs", overlay.startUs.toString()); comma(); field("durationUs", overlay.durationUs.toString()); comma()
        field("text", jsonString(overlay.text)); comma(); append("\"style\":"); appendTextStyle(overlay.style); comma()
        append("\"transform\":"); appendTransform(overlay.transform); comma(); field("opacity", overlay.opacity.toString()); comma(); field("zIndex", overlay.zIndex.toString()); comma()
        append("\"entrance\":"); appendTextAnimation(overlay.entrance); comma()
        append("\"exit\":"); appendTextAnimation(overlay.exit); comma()
        append("\"loop\":"); appendTextAnimation(overlay.loop); comma()
        field("perCharacterDelayUs", overlay.perCharacterDelayUs.toString()); comma()
        append("\"caption\":"); appendCaption(overlay.caption)
        append('}')
    }

    private fun StringBuilder.appendTextStyle(style: TextStyle) {
        append('{')
        field("fontSizePx", style.fontSizePx.toString()); comma(); field("alignment", jsonString(style.alignment.name)); comma(); field("fillArgb", style.fillArgb.toString()); comma()
        field("strokeArgb", style.strokeArgb?.toString() ?: "null"); comma(); field("strokeWidthPx", style.strokeWidthPx.toString()); comma()
        field("shadowArgb", style.shadowArgb?.toString() ?: "null"); comma(); field("shadowRadiusPx", style.shadowRadiusPx.toString()); comma()
        field("shadowOffsetX", style.shadowOffsetX.toString()); comma(); field("shadowOffsetY", style.shadowOffsetY.toString()); comma()
        append("\"font\":"); appendFont(style.font); comma()
        field("letterSpacingEm", style.letterSpacingEm.toString()); comma(); field("lineSpacingMultiplier", style.lineSpacingMultiplier.toString()); comma()
        append("\"background\":"); appendTextBackground(style.background); comma()
        append("\"strokes\":[")
        style.strokes.forEachIndexed { index, stroke ->
            if (index > 0) comma()
            append('{'); field("argb", stroke.argb.toString()); comma(); field("widthPx", stroke.widthPx.toString()); append('}')
        }
        append("],\"shadows\":[")
        style.shadows.forEachIndexed { index, shadow ->
            if (index > 0) comma()
            append('{')
            field("argb", shadow.argb.toString()); comma(); field("radiusPx", shadow.radiusPx.toString()); comma()
            field("offsetX", shadow.offsetX.toString()); comma(); field("offsetY", shadow.offsetY.toString()); comma(); field("opacity", shadow.opacity.toString())
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.appendFont(font: FontReference?) {
        if (font == null) { append("null"); return }
        append('{')
        field("familyName", font.familyName?.let(::jsonString) ?: "null"); comma()
        field("uri", font.uri?.let(::jsonString) ?: "null"); comma()
        field("fingerprint", font.fingerprint?.let(::jsonString) ?: "null")
        append('}')
    }

    private fun StringBuilder.appendTextBackground(background: TextBackgroundStyle?) {
        if (background == null) { append("null"); return }
        append('{')
        field("argb", background.argb.toString()); comma(); field("paddingPx", background.paddingPx.toString()); comma()
        field("cornerRadiusPx", background.cornerRadiusPx.toString()); comma(); field("opacity", background.opacity.toString())
        append('}')
    }

    private fun StringBuilder.appendTextAnimation(animation: TextAnimationDescriptor) {
        append('{')
        field("kind", jsonString(animation.kind.name)); comma(); field("durationUs", animation.durationUs.toString()); comma()
        append("\"easing\":"); appendEasing(animation.easing); comma(); field("amount", animation.amount.toString())
        append('}')
    }

    private fun StringBuilder.appendCaption(caption: CaptionMetadata?) {
        if (caption == null) { append("null"); return }
        append('{')
        field("languageTag", caption.languageTag?.let(::jsonString) ?: "null"); comma()
        field("speaker", caption.speaker?.let(::jsonString) ?: "null"); comma()
        field("confidence", caption.confidence?.toString() ?: "null"); comma()
        field("sourceText", caption.sourceText?.let(::jsonString) ?: "null")
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

    private fun StringBuilder.appendPropertyAnimation(animation: PropertyAnimation) {
        append('{')
        field("propertyId", jsonString(animation.propertyId)); comma()
        append("\"keyframes\":[")
        animation.keyframes.forEachIndexed { index, keyframe ->
            if (index > 0) comma()
            append('{')
            field("id", jsonString(keyframe.id)); comma()
            field("offsetUs", keyframe.offsetUs.toString()); comma()
            append("\"value\":"); appendPropertyValue(keyframe.value); comma()
            append("\"easingToNext\":"); appendEasing(keyframe.easingToNext)
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.appendPropertyValue(value: PropertyValue) {
        append('{')
        when (value) {
            is PropertyValue.Scalar -> {
                field("type", jsonString("SCALAR")); comma(); field("value", value.value.toString())
            }
            is PropertyValue.Point -> {
                field("type", jsonString("POINT")); comma(); field("x", value.x.toString()); comma(); field("y", value.y.toString())
            }
            is PropertyValue.Color -> {
                field("type", jsonString("COLOR")); comma(); field("argb", value.argb.toString())
            }
            is PropertyValue.Flag -> {
                field("type", jsonString("FLAG")); comma(); field("value", value.value.toString())
            }
        }
        append('}')
    }

    private fun StringBuilder.appendEasing(easing: Easing) {
        append('{')
        field("kind", jsonString(easing.kind.name)); comma()
        append("\"bezier\":")
        val bezier = easing.bezier
        if (bezier == null) {
            append("null")
        } else {
            append('{')
            field("x1", bezier.x1.toString()); comma(); field("y1", bezier.y1.toString()); comma()
            field("x2", bezier.x2.toString()); comma(); field("y2", bezier.y2.toString())
            append('}')
        }
        comma(); field("steps", easing.steps.toString()); comma(); field("seed", easing.seed.toString()); comma(); field("overshoot", easing.overshoot.toString())
        append('}')
    }

    private fun StringBuilder.appendCompositing(settings: CompositingSettings) {
        append('{')
        field("blendMode", jsonString(settings.blendMode.name)); comma()
        append("\"masks\":[")
        settings.masks.forEachIndexed { index, mask -> if (index > 0) comma(); appendMask(mask) }
        append("],\"chromaKey\":"); appendChromaKey(settings.chromaKey); comma()
        append("\"crop\":"); appendCrop(settings.crop)
        append('}')
    }

    private fun StringBuilder.appendMask(mask: MaskSpec) {
        append('{')
        field("id", jsonString(mask.id)); comma(); field("kind", jsonString(mask.kind.name)); comma()
        append("\"points\":[")
        mask.points.forEachIndexed { index, point ->
            if (index > 0) comma()
            append('{'); field("x", point.x.toString()); comma(); field("y", point.y.toString()); append('}')
        }
        append("],")
        field("invert", mask.invert.toString()); comma(); field("featherPx", mask.featherPx.toString()); comma()
        field("expansionPx", mask.expansionPx.toString()); comma(); field("opacity", mask.opacity.toString()); comma()
        append("\"animations\":[")
        mask.animations.forEachIndexed { index, animation -> if (index > 0) comma(); appendPropertyAnimation(animation) }
        append("]}")
    }

    private fun StringBuilder.appendChromaKey(chroma: ChromaKeySettings) {
        append('{')
        field("enabled", chroma.enabled.toString()); comma(); field("keyArgb", chroma.keyArgb.toString()); comma()
        field("threshold", chroma.threshold.toString()); comma(); field("softness", chroma.softness.toString()); comma()
        field("spillSuppression", chroma.spillSuppression.toString()); comma(); field("edgeFeatherPx", chroma.edgeFeatherPx.toString())
        append('}')
    }

    private fun StringBuilder.appendCrop(crop: CropSettings) {
        append('{')
        field("mode", jsonString(crop.mode.name)); comma(); field("left", crop.left.toString()); comma(); field("top", crop.top.toString()); comma()
        field("right", crop.right.toString()); comma(); field("bottom", crop.bottom.toString())
        append('}')
    }

    private fun StringBuilder.appendEffect(effect: EffectInstance) {
        append('{')
        field("id", jsonString(effect.id)); comma(); field("typeId", jsonString(effect.typeId)); comma(); field("enabled", effect.enabled.toString()); comma()
        append("\"parameters\":[")
        effect.orderedParameters().forEachIndexed { index, (name, value) ->
            if (index > 0) comma()
            append('{'); field("name", jsonString(name)); comma(); append("\"value\":"); appendEffectParameter(value); append('}')
        }
        append("],\"animations\":[")
        effect.animations.forEachIndexed { index, animation -> if (index > 0) comma(); appendPropertyAnimation(animation) }
        append("]}")
    }

    private fun StringBuilder.appendEffectParameter(parameter: EffectParameter) {
        append('{')
        when (parameter) {
            is EffectParameter.Number -> {
                field("type", jsonString("NUMBER")); comma(); field("value", parameter.value.toString())
            }
            is EffectParameter.Color -> {
                field("type", jsonString("COLOR")); comma(); field("argb", parameter.argb.toString())
            }
            is EffectParameter.Bool -> {
                field("type", jsonString("BOOL")); comma(); field("value", parameter.value.toString())
            }
            is EffectParameter.Text -> {
                field("type", jsonString("TEXT")); comma(); field("value", jsonString(parameter.value))
            }
            is EffectParameter.Point -> {
                field("type", jsonString("POINT")); comma(); field("x", parameter.x.toString()); comma(); field("y", parameter.y.toString())
            }
        }
        append('}')
    }

    private fun StringBuilder.appendTimeMap(timeMap: TimeMap) {
        append('{')
        field("baseSpeed", timeMap.baseSpeed.toString()); comma(); field("reverse", timeMap.reverse.toString()); comma()
        append("\"speedPoints\":[")
        timeMap.speedPoints.forEachIndexed { index, point ->
            if (index > 0) comma()
            append('{'); field("offsetUs", point.offsetUs.toString()); comma(); field("speed", point.speed.toString()); append('}')
        }
        append("],\"freezeSegments\":[")
        timeMap.freezeSegments.forEachIndexed { index, freeze ->
            if (index > 0) comma()
            append('{')
            field("startOffsetUs", freeze.startOffsetUs.toString()); comma(); field("durationUs", freeze.durationUs.toString()); comma(); field("sourceOffsetUs", freeze.sourceOffsetUs.toString())
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.appendDesignElement(element: DesignElement) {
        append('{')
        field("id", jsonString(element.id)); comma(); field("startUs", element.startUs.toString()); comma(); field("durationUs", element.durationUs.toString()); comma()
        append("\"shape\":"); appendShape(element.shape); comma()
        field("clipId", element.clipId?.let(::jsonString) ?: "null"); comma(); field("textOverlayId", element.textOverlayId?.let(::jsonString) ?: "null"); comma()
        append("\"transform\":"); appendTransform(element.transform); comma(); field("opacity", element.opacity.toString()); comma(); field("zIndex", element.zIndex.toString())
        append('}')
    }

    private fun StringBuilder.appendShape(shape: ShapeSpec?) {
        if (shape == null) { append("null"); return }
        append('{')
        field("kind", jsonString(shape.kind.name)); comma(); append("\"path\":"); appendVectorPath(shape.path); comma()
        field("fillArgb", shape.fillArgb?.toString() ?: "null"); comma(); field("strokeArgb", shape.strokeArgb?.toString() ?: "null"); comma()
        field("strokeWidthPx", shape.strokeWidthPx.toString()); comma(); field("cornerRadiusPx", shape.cornerRadiusPx.toString())
        append('}')
    }

    private fun StringBuilder.appendVectorPath(path: VectorPath?) {
        if (path == null) { append("null"); return }
        append('{'); append("\"points\":[")
        path.points.forEachIndexed { index, point ->
            if (index > 0) comma()
            append('{'); field("x", point.x.toString()); comma(); field("y", point.y.toString()); append('}')
        }
        append("],"); field("closed", path.closed.toString()); append('}')
    }

    private fun StringBuilder.appendDesignCanvas(settings: DesignCanvasSettings) {
        append('{'); append("\"guides\":[")
        settings.guides.forEachIndexed { index, guide ->
            if (index > 0) comma()
            append('{'); field("id", jsonString(guide.id)); comma(); field("axis", jsonString(guide.axis.name)); comma(); field("positionNormalized", guide.positionNormalized.toString()); append('}')
        }
        append("],"); field("snapToleranceNormalized", settings.snapToleranceNormalized.toString()); append('}')
    }

    private fun StringBuilder.appendReusableComponent(component: ReusableComponent) {
        append('{')
        field("id", jsonString(component.id)); comma(); field("name", jsonString(component.name)); comma()
        append("\"elementIds\":[")
        component.elementIds.forEachIndexed { index, id -> if (index > 0) comma(); append(jsonString(id)) }
        append("],\"exposedProperties\":[")
        component.exposedProperties.sorted().forEachIndexed { index, property -> if (index > 0) comma(); append(jsonString(property)) }
        append("]}")
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
        clips = o.required("clips").asArray().values.map { decodeClip(it.asObject(), sourceSchemaVersion) },
        solo = o.optional("solo")?.asBoolean() ?: false,
    )

    private fun decodeClip(o: JObject, sourceSchemaVersion: Int): Clip = Clip(
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
        propertyAnimations = if (sourceSchemaVersion >= 4) o.required("propertyAnimations").asArray().values.map { decodePropertyAnimation(it.asObject()) } else emptyList(),
        compositing = if (sourceSchemaVersion >= 4) decodeCompositing(o.required("compositing").asObject()) else CompositingSettings(),
        effects = if (sourceSchemaVersion >= 4) o.required("effects").asArray().values.map { decodeEffect(it.asObject()) } else emptyList(),
        timeMap = if (sourceSchemaVersion >= 4) decodeTimeMap(o.required("timeMap").asObject()) else TimeMap(),
    )

    private fun decodePropertyAnimation(o: JObject): PropertyAnimation = PropertyAnimation(
        propertyId = o.required("propertyId").asString(),
        keyframes = o.required("keyframes").asArray().values.map { raw ->
            val keyframe = raw.asObject()
            PropertyKeyframe(
                id = keyframe.required("id").asString(),
                offsetUs = keyframe.required("offsetUs").asLong(),
                value = decodePropertyValue(keyframe.required("value").asObject()),
                easingToNext = decodeEasing(keyframe.required("easingToNext").asObject()),
            )
        },
    )

    private fun decodePropertyValue(o: JObject): PropertyValue = when (o.required("type").asString()) {
        "SCALAR" -> PropertyValue.Scalar(o.required("value").asDouble())
        "POINT" -> PropertyValue.Point(o.required("x").asDouble(), o.required("y").asDouble())
        "COLOR" -> PropertyValue.Color(o.required("argb").asInt())
        "FLAG" -> PropertyValue.Flag(o.required("value").asBoolean())
        else -> error("unsupported property value type")
    }

    private fun decodeEasing(o: JObject): Easing {
        val rawBezier = o.optional("bezier")
        val bezier = if (rawBezier == null || rawBezier === JNull) {
            null
        } else {
            val b = rawBezier.asObject()
            CubicBezierCurve(
                x1 = b.required("x1").asDouble(),
                y1 = b.required("y1").asDouble(),
                x2 = b.required("x2").asDouble(),
                y2 = b.required("y2").asDouble(),
            )
        }
        return Easing(
            kind = EasingKind.valueOf(o.required("kind").asString()),
            bezier = bezier,
            steps = o.required("steps").asInt(),
            seed = o.required("seed").asLong(),
            overshoot = o.required("overshoot").asBoolean(),
        )
    }

    private fun decodeCompositing(o: JObject): CompositingSettings = CompositingSettings(
        blendMode = BlendMode.valueOf(o.required("blendMode").asString()),
        masks = o.required("masks").asArray().values.map { decodeMask(it.asObject()) },
        chromaKey = decodeChromaKey(o.required("chromaKey").asObject()),
        crop = decodeCrop(o.required("crop").asObject()),
    )

    private fun decodeMask(o: JObject): MaskSpec = MaskSpec(
        id = o.required("id").asString(),
        kind = MaskKind.valueOf(o.required("kind").asString()),
        points = o.required("points").asArray().values.map { raw ->
            val point = raw.asObject()
            MaskPoint(point.required("x").asDouble(), point.required("y").asDouble())
        },
        invert = o.required("invert").asBoolean(),
        featherPx = o.required("featherPx").asDouble(),
        expansionPx = o.required("expansionPx").asDouble(),
        opacity = o.required("opacity").asDouble(),
        animations = o.required("animations").asArray().values.map { decodePropertyAnimation(it.asObject()) },
    )

    private fun decodeChromaKey(o: JObject): ChromaKeySettings = ChromaKeySettings(
        enabled = o.required("enabled").asBoolean(),
        keyArgb = o.required("keyArgb").asInt(),
        threshold = o.required("threshold").asDouble(),
        softness = o.required("softness").asDouble(),
        spillSuppression = o.required("spillSuppression").asDouble(),
        edgeFeatherPx = o.required("edgeFeatherPx").asDouble(),
    )

    private fun decodeCrop(o: JObject): CropSettings = CropSettings(
        mode = CropMode.valueOf(o.required("mode").asString()),
        left = o.required("left").asDouble(),
        top = o.required("top").asDouble(),
        right = o.required("right").asDouble(),
        bottom = o.required("bottom").asDouble(),
    )

    private fun decodeEffect(o: JObject): EffectInstance {
        val parameterEntries = o.required("parameters").asArray().values.map { raw ->
            val entry = raw.asObject()
            entry.required("name").asString() to decodeEffectParameter(entry.required("value").asObject())
        }
        require(parameterEntries.map { it.first }.distinct().size == parameterEntries.size) { "duplicate effect parameter name" }
        return EffectInstance(
            id = o.required("id").asString(),
            typeId = o.required("typeId").asString(),
            enabled = o.required("enabled").asBoolean(),
            parameters = parameterEntries.toMap(),
            animations = o.required("animations").asArray().values.map { decodePropertyAnimation(it.asObject()) },
        )
    }

    private fun decodeEffectParameter(o: JObject): EffectParameter = when (o.required("type").asString()) {
        "NUMBER" -> EffectParameter.Number(o.required("value").asDouble())
        "COLOR" -> EffectParameter.Color(o.required("argb").asInt())
        "BOOL" -> EffectParameter.Bool(o.required("value").asBoolean())
        "TEXT" -> EffectParameter.Text(o.required("value").asString())
        "POINT" -> EffectParameter.Point(o.required("x").asDouble(), o.required("y").asDouble())
        else -> error("unsupported effect parameter type")
    }

    private fun decodeTimeMap(o: JObject): TimeMap = TimeMap(
        baseSpeed = o.required("baseSpeed").asDouble(),
        reverse = o.required("reverse").asBoolean(),
        speedPoints = o.required("speedPoints").asArray().values.map { raw ->
            val point = raw.asObject()
            SpeedPoint(point.required("offsetUs").asLong(), point.required("speed").asDouble())
        },
        freezeSegments = o.required("freezeSegments").asArray().values.map { raw ->
            val freeze = raw.asObject()
            FreezeSegment(
                startOffsetUs = freeze.required("startOffsetUs").asLong(),
                durationUs = freeze.required("durationUs").asLong(),
                sourceOffsetUs = freeze.required("sourceOffsetUs").asLong(),
            )
        },
    )

    private fun decodeTextOverlay(o: JObject, sourceSchemaVersion: Int): TextOverlay = TextOverlay(
        id = o.required("id").asString(),
        startUs = o.required("startUs").asLong(),
        durationUs = o.required("durationUs").asLong(),
        text = o.required("text").asString(),
        style = decodeTextStyle(o.required("style").asObject(), sourceSchemaVersion),
        transform = decodeTransform(o.required("transform").asObject()),
        opacity = o.required("opacity").asDouble(),
        zIndex = o.required("zIndex").asInt(),
        entrance = if (sourceSchemaVersion >= 5) decodeTextAnimation(o.required("entrance").asObject()) else TextAnimationDescriptor(),
        exit = if (sourceSchemaVersion >= 5) decodeTextAnimation(o.required("exit").asObject()) else TextAnimationDescriptor(),
        loop = if (sourceSchemaVersion >= 5) decodeTextAnimation(o.required("loop").asObject()) else TextAnimationDescriptor(),
        perCharacterDelayUs = if (sourceSchemaVersion >= 5) o.required("perCharacterDelayUs").asLong() else 0L,
        caption = if (sourceSchemaVersion >= 5) decodeCaption(o.optional("caption")) else null,
    )

    private fun decodeTextStyle(o: JObject, sourceSchemaVersion: Int): TextStyle = TextStyle(
        fontSizePx = o.required("fontSizePx").asDouble(),
        alignment = TextHorizontalAlignment.valueOf(o.required("alignment").asString()),
        fillArgb = o.required("fillArgb").asInt(),
        strokeArgb = o.optional("strokeArgb").nullableInt(),
        strokeWidthPx = o.required("strokeWidthPx").asDouble(),
        shadowArgb = o.optional("shadowArgb").nullableInt(),
        shadowRadiusPx = o.required("shadowRadiusPx").asDouble(),
        shadowOffsetX = o.required("shadowOffsetX").asDouble(),
        shadowOffsetY = o.required("shadowOffsetY").asDouble(),
        font = if (sourceSchemaVersion >= 5) decodeFont(o.optional("font")) else null,
        letterSpacingEm = if (sourceSchemaVersion >= 5) o.required("letterSpacingEm").asDouble() else 0.0,
        lineSpacingMultiplier = if (sourceSchemaVersion >= 5) o.required("lineSpacingMultiplier").asDouble() else 1.0,
        background = if (sourceSchemaVersion >= 5) decodeTextBackground(o.optional("background")) else null,
        strokes = if (sourceSchemaVersion >= 5) o.required("strokes").asArray().values.map { raw ->
            val stroke = raw.asObject(); TextStrokeStyle(stroke.required("argb").asInt(), stroke.required("widthPx").asDouble())
        } else emptyList(),
        shadows = if (sourceSchemaVersion >= 5) o.required("shadows").asArray().values.map { raw ->
            val shadow = raw.asObject()
            TextShadowStyle(
                argb = shadow.required("argb").asInt(),
                radiusPx = shadow.required("radiusPx").asDouble(),
                offsetX = shadow.required("offsetX").asDouble(),
                offsetY = shadow.required("offsetY").asDouble(),
                opacity = shadow.required("opacity").asDouble(),
            )
        } else emptyList(),
    )

    private fun decodeFont(value: JValue?): FontReference? = when (value) {
        null, JNull -> null
        else -> value.asObject().let { o ->
            FontReference(
                familyName = o.optional("familyName").nullableString(),
                uri = o.optional("uri").nullableString(),
                fingerprint = o.optional("fingerprint").nullableString(),
            )
        }
    }

    private fun decodeTextBackground(value: JValue?): TextBackgroundStyle? = when (value) {
        null, JNull -> null
        else -> value.asObject().let { o ->
            TextBackgroundStyle(
                argb = o.required("argb").asInt(),
                paddingPx = o.required("paddingPx").asDouble(),
                cornerRadiusPx = o.required("cornerRadiusPx").asDouble(),
                opacity = o.required("opacity").asDouble(),
            )
        }
    }

    private fun decodeTextAnimation(o: JObject): TextAnimationDescriptor = TextAnimationDescriptor(
        kind = TextAnimationKind.valueOf(o.required("kind").asString()),
        durationUs = o.required("durationUs").asLong(),
        easing = decodeEasing(o.required("easing").asObject()),
        amount = o.required("amount").asDouble(),
    )

    private fun decodeCaption(value: JValue?): CaptionMetadata? = when (value) {
        null, JNull -> null
        else -> value.asObject().let { o ->
            CaptionMetadata(
                languageTag = o.optional("languageTag").nullableString(),
                speaker = o.optional("speaker").nullableString(),
                confidence = o.optional("confidence").nullableDouble(),
                sourceText = o.optional("sourceText").nullableString(),
            )
        }
    }

    private fun decodeDesignElement(o: JObject): DesignElement = DesignElement(
        id = o.required("id").asString(),
        startUs = o.required("startUs").asLong(),
        durationUs = o.required("durationUs").asLong(),
        shape = decodeShape(o.optional("shape")),
        clipId = o.optional("clipId").nullableString(),
        textOverlayId = o.optional("textOverlayId").nullableString(),
        transform = decodeTransform(o.required("transform").asObject()),
        opacity = o.required("opacity").asDouble(),
        zIndex = o.required("zIndex").asInt(),
    )

    private fun decodeShape(value: JValue?): ShapeSpec? = when (value) {
        null, JNull -> null
        else -> value.asObject().let { o ->
            ShapeSpec(
                kind = ShapeKind.valueOf(o.required("kind").asString()),
                path = decodeVectorPath(o.optional("path")),
                fillArgb = o.optional("fillArgb").nullableInt(),
                strokeArgb = o.optional("strokeArgb").nullableInt(),
                strokeWidthPx = o.required("strokeWidthPx").asDouble(),
                cornerRadiusPx = o.required("cornerRadiusPx").asDouble(),
            )
        }
    }

    private fun decodeVectorPath(value: JValue?): VectorPath? = when (value) {
        null, JNull -> null
        else -> value.asObject().let { o ->
            VectorPath(
                points = o.required("points").asArray().values.map { raw ->
                    val point = raw.asObject(); PathPoint(point.required("x").asDouble(), point.required("y").asDouble())
                },
                closed = o.required("closed").asBoolean(),
            )
        }
    }

    private fun decodeDesignCanvas(o: JObject): DesignCanvasSettings = DesignCanvasSettings(
        guides = o.required("guides").asArray().values.map { raw ->
            val guide = raw.asObject()
            CanvasGuide(
                id = guide.required("id").asString(),
                axis = GuideAxis.valueOf(guide.required("axis").asString()),
                positionNormalized = guide.required("positionNormalized").asDouble(),
            )
        },
        snapToleranceNormalized = o.required("snapToleranceNormalized").asDouble(),
    )

    private fun decodeReusableComponent(o: JObject): ReusableComponent = ReusableComponent(
        id = o.required("id").asString(),
        name = o.required("name").asString(),
        elementIds = o.required("elementIds").asArray().values.map { it.asString() },
        exposedProperties = o.required("exposedProperties").asArray().values.map { it.asString() }.toSet(),
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
    private fun JValue?.nullableDouble(): Double? = when (this) { null, JNull -> null; else -> asDouble() }
    private fun JValue?.nullableString(): String? = when (this) { null, JNull -> null; else -> asString() }
    private fun StringBuilder.field(name: String, encodedValue: String) { append(jsonString(name)); append(':'); append(encodedValue) }
    private fun StringBuilder.comma() { append(',') }
}
