package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue

private const val PROP_X = "transform.x"
private const val PROP_Y = "transform.y"
private const val PROP_SCALE = "transform.scale"
private const val PROP_ROTATION = "transform.rotationDeg"
private const val PROP_OPACITY = "transform.opacity"
private const val MOUTH_WARP_EFFECT_ID = "localcut-character-mouth-warp"
private const val MOUTH_WARP_TYPE_ID = "localcut.character.mouth_warp"

enum class CharacterAction {
    IDLE,
    TALK,
    WALK,
    CROUCH,
    STAND,
    PICK_UP,
    PUT_DOWN,
    POINT,
    ENTER,
    EXIT,
}

data class CharacterRigBinding(
    val bodyClipId: String,
    val headClipId: String? = null,
    val eyesClipId: String? = null,
    val mouthClipId: String? = null,
    val leftArmClipId: String? = null,
    val rightArmClipId: String? = null,
    val leftLegClipId: String? = null,
    val rightLegClipId: String? = null,
    val heldObjectClipId: String? = null,
)

data class LipSyncCue(
    val offsetUs: Long,
    val durationUs: Long,
    val openness: Double,
)

data class MouthRegion(
    val centerX: Double,
    val centerY: Double,
    val radiusX: Double,
    val radiusY: Double,
)

data class CharacterActionRequest(
    val action: CharacterAction,
    val rig: CharacterRigBinding,
    val startOffsetUs: Long,
    val durationUs: Long,
    val intensity: Double = 1.0,
    val direction: Double = 1.0,
    val distancePx: Double = 160.0,
    val targetX: Double? = null,
    val targetY: Double? = null,
    val lipSyncCues: List<LipSyncCue> = emptyList(),
    val mouthRegion: MouthRegion? = null,
)

enum class SceneAnimationKind {
    PAN_ZOOM,
    PARALLAX,
    CROSSFADE,
}

data class SceneAnimationRequest(
    val kind: SceneAnimationKind,
    val primaryClipId: String,
    val startOffsetUs: Long,
    val durationUs: Long,
    val incomingClipId: String? = null,
    val secondaryClipIds: List<String> = emptyList(),
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val zoomTo: Double = 1.0,
)

data class AnimationClipState(
    val clipId: String,
    val durationUs: Long,
    val transform: TransformValue,
)

data class ClipAnimationPatch(
    val clipId: String,
    val propertyAnimations: List<PropertyAnimation>,
)

data class ClipEffectPatch(
    val clipId: String,
    val effects: List<EffectInstance>,
)

data class CharacterAnimationPlan(
    val patches: List<ClipAnimationPatch>,
    val effectPatches: List<ClipEffectPatch> = emptyList(),
) {
    init {
        require(patches.isNotEmpty() || effectPatches.isNotEmpty()) { "animation plan must not be empty" }
        require(patches.map { it.clipId }.distinct().size == patches.size) { "animation plan clip ids must be unique" }
        require(effectPatches.map { it.clipId }.distinct().size == effectPatches.size) { "animation effect patch clip ids must be unique" }
        require(patches.all { it.clipId.isNotBlank() }) { "animation plan clip id must not be blank" }
        require(effectPatches.all { it.clipId.isNotBlank() }) { "animation effect clip id must not be blank" }
        require(patches.all { it.propertyAnimations.isNotEmpty() }) { "animation plan patches must not be empty" }
        require(effectPatches.all { it.effects.isNotEmpty() }) { "animation effect patches must not be empty" }
        require(patches.all { patch -> patch.propertyAnimations.all { it.validationIssues().isEmpty() } }) {
            "animation plan contains invalid property animation"
        }
        require(effectPatches.all { patch ->
            patch.effects.map { it.id }.distinct().size == patch.effects.size &&
                patch.effects.all { it.validationIssues().isEmpty() }
        }) { "animation plan contains invalid effect patch" }
    }
}

object CharacterSceneAnimationPlanner {
    fun character(
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
    ): CharacterAnimationPlan {
        validateCharacterRequest(request, clips)
        val start = request.startOffsetUs
        val end = Math.addExact(start, request.durationUs)
        val body = requireClip(request.rig.bodyClipId, clips)
        val plan = PlanBuilder(clips)

        when (request.action) {
            CharacterAction.IDLE -> {
                idle(plan.patch(body.clipId), body, start, end, request.intensity)
                request.rig.headClipId?.let { id -> headIdle(plan.patch(id), requireClip(id, clips), start, end, request.intensity) }
                request.rig.eyesClipId?.let { id -> blink(plan.patch(id), requireClip(id, clips), start, end) }
            }
            CharacterAction.TALK -> {
                val flatPortrait = request.mouthRegion != null && listOfNotNull(
                    request.rig.headClipId, request.rig.eyesClipId, request.rig.mouthClipId,
                    request.rig.leftArmClipId, request.rig.rightArmClipId,
                    request.rig.leftLegClipId, request.rig.rightLegClipId,
                    request.rig.heldObjectClipId,
                ).isEmpty()
                if (flatPortrait) {
                    // A single baked portrait may include the entire background.
                    // Keep its framing fixed; face-local effects supply motion.
                    // Write neutral keys so reapplying TALK replaces old sway keys
                    // inside the requested interval without changing base framing.
                    val builder = plan.patch(body.clipId)
                    builder.scalar(PROP_Y, start, body.transform.y, hold())
                    builder.scalar(PROP_Y, end, body.transform.y)
                    builder.scalar(PROP_ROTATION, start, body.transform.rotationDeg, hold())
                    builder.scalar(PROP_ROTATION, end, body.transform.rotationDeg)
                } else {
                    talkBody(plan.patch(body.clipId), body, start, end, request.intensity)
                }
                request.rig.headClipId?.let { id -> headTalk(plan.patch(id), requireClip(id, clips), start, end, request.intensity) }
                val mouthId = request.rig.mouthClipId
                if (mouthId != null) {
                    mouth(plan.patch(mouthId), requireClip(mouthId, clips), request)
                } else {
                    request.mouthRegion?.let { region ->
                        plan.effect(body.clipId, flatMouthEffect(request, region))
                    }
                }
                request.rig.eyesClipId?.let { id -> blink(plan.patch(id), requireClip(id, clips), start, end) }
            }
            CharacterAction.WALK -> walk(plan, request, clips, body, start, end)
            CharacterAction.CROUCH -> crouch(plan, request, clips, body, start, end, reverse = false)
            CharacterAction.STAND -> crouch(plan, request, clips, body, start, end, reverse = true)
            CharacterAction.PICK_UP -> pickup(plan, request, clips, body, start, end, putDown = false)
            CharacterAction.PUT_DOWN -> pickup(plan, request, clips, body, start, end, putDown = true)
            CharacterAction.POINT -> point(plan, request, clips, body, start, end)
            CharacterAction.ENTER -> enterExit(plan.patch(body.clipId), body, request, start, end, entering = true)
            CharacterAction.EXIT -> enterExit(plan.patch(body.clipId), body, request, start, end, entering = false)
        }
        return plan.build()
    }

    fun scene(
        request: SceneAnimationRequest,
        clips: Map<String, AnimationClipState>,
    ): CharacterAnimationPlan {
        validateSceneRequest(request, clips)
        val start = request.startOffsetUs
        val end = Math.addExact(start, request.durationUs)
        val primary = requireClip(request.primaryClipId, clips)
        val plan = PlanBuilder(clips)

        when (request.kind) {
            SceneAnimationKind.PAN_ZOOM -> {
                panZoom(plan.patch(primary.clipId), primary, start, end, request.panX, request.panY, request.zoomTo)
            }
            SceneAnimationKind.PARALLAX -> {
                panZoom(
                    plan.patch(primary.clipId),
                    primary,
                    start,
                    end,
                    request.panX * 0.45,
                    request.panY * 0.45,
                    1.0 + (request.zoomTo - 1.0) * 0.45,
                )
                request.secondaryClipIds.forEachIndexed { index, id ->
                    val state = requireClip(id, clips)
                    val factor = 0.8 + index * 0.2
                    panZoom(
                        plan.patch(id),
                        state,
                        start,
                        end,
                        request.panX * factor,
                        request.panY * factor,
                        1.0 + (request.zoomTo - 1.0) * factor,
                    )
                }
            }
            SceneAnimationKind.CROSSFADE -> {
                val incomingId = requireNotNull(request.incomingClipId) { "crossfade requires incomingClipId" }
                val incoming = requireClip(incomingId, clips)
                val easing = smooth()
                plan.patch(primary.clipId).scalar(PROP_OPACITY, start, primary.transform.opacity, easing)
                plan.patch(primary.clipId).scalar(PROP_OPACITY, end, 0.0)
                plan.patch(incoming.clipId).scalar(PROP_OPACITY, start, 0.0, easing)
                plan.patch(incoming.clipId).scalar(PROP_OPACITY, end, incoming.transform.opacity)
            }
        }
        return plan.build()
    }

    private fun validateCharacterRequest(
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
    ) {
        require(request.rig.bodyClipId.isNotBlank()) { "body clip id must not be blank" }
        require(request.startOffsetUs >= 0L) { "animation start must be non-negative" }
        require(request.durationUs > 0L) { "animation duration must be positive" }
        require(request.intensity.isFinite() && request.intensity in 0.0..2.0) { "animation intensity must be in 0..2" }
        require(request.direction.isFinite() && request.direction in -1.0..1.0) { "animation direction must be in -1..1" }
        require(request.distancePx.isFinite() && request.distancePx >= 0.0) { "animation distance must be finite and non-negative" }
        require(request.targetX == null || request.targetX.isFinite()) { "targetX must be finite" }
        require(request.targetY == null || request.targetY.isFinite()) { "targetY must be finite" }
        request.mouthRegion?.let(::validateMouthRegion)

        val ids = listOfNotNull(
            request.rig.bodyClipId,
            request.rig.headClipId,
            request.rig.eyesClipId,
            request.rig.mouthClipId,
            request.rig.leftArmClipId,
            request.rig.rightArmClipId,
            request.rig.leftLegClipId,
            request.rig.rightLegClipId,
            request.rig.heldObjectClipId,
        )
        require(ids.all { it.isNotBlank() }) { "rig clip ids must not be blank" }
        require(ids.distinct().size == ids.size) { "rig clip ids must be unique" }
        val end = Math.addExact(request.startOffsetUs, request.durationUs)
        ids.forEach { id ->
            val state = requireClip(id, clips)
            validateState(state)
            require(end <= state.durationUs) { "animation exceeds clip duration: $id" }
        }
        if (request.action == CharacterAction.PICK_UP || request.action == CharacterAction.PUT_DOWN) {
            require(!request.rig.heldObjectClipId.isNullOrBlank()) { "${request.action.name.lowercase()} requires held object clip" }
        }
        validateLipSync(request.lipSyncCues, request.durationUs)
    }

    private fun validateMouthRegion(region: MouthRegion) {
        require(region.centerX.isFinite() && region.centerX in 0.0..1.0) { "mouth centerX must be in 0..1" }
        require(region.centerY.isFinite() && region.centerY in 0.0..1.0) { "mouth centerY must be in 0..1" }
        require(region.radiusX.isFinite() && region.radiusX in 0.01..0.50) { "mouth radiusX must be in 0.01..0.50" }
        require(region.radiusY.isFinite() && region.radiusY in 0.005..0.30) { "mouth radiusY must be in 0.005..0.30" }
    }

    private fun validateSceneRequest(
        request: SceneAnimationRequest,
        clips: Map<String, AnimationClipState>,
    ) {
        require(request.primaryClipId.isNotBlank()) { "primary clip id must not be blank" }
        require(request.startOffsetUs >= 0L) { "scene animation start must be non-negative" }
        require(request.durationUs > 0L) { "scene animation duration must be positive" }
        require(request.panX.isFinite() && request.panY.isFinite()) { "scene pan must be finite" }
        require(request.zoomTo.isFinite() && request.zoomTo > 0.0) { "scene zoom must be finite and positive" }
        require(request.secondaryClipIds.all { it.isNotBlank() }) { "secondary clip ids must not be blank" }
        require(request.secondaryClipIds.distinct().size == request.secondaryClipIds.size) { "secondary clip ids must be unique" }
        require(request.primaryClipId !in request.secondaryClipIds) { "primary clip cannot also be a secondary clip" }
        if (request.kind == SceneAnimationKind.CROSSFADE) {
            val incoming = requireNotNull(request.incomingClipId) { "crossfade requires incomingClipId" }
            require(incoming.isNotBlank() && incoming != request.primaryClipId) { "crossfade incoming clip must be distinct" }
        }
        val ids = buildList {
            add(request.primaryClipId)
            addAll(request.secondaryClipIds)
            request.incomingClipId?.let(::add)
        }.distinct()
        val end = Math.addExact(request.startOffsetUs, request.durationUs)
        ids.forEach { id ->
            val state = requireClip(id, clips)
            validateState(state)
            require(end <= state.durationUs) { "scene animation exceeds clip duration: $id" }
        }
    }

    private fun validateLipSync(cues: List<LipSyncCue>, actionDurationUs: Long) {
        cues.forEach { cue ->
            require(cue.offsetUs >= 0L) { "lip cue offset must be non-negative" }
            require(cue.durationUs > 0L) { "lip cue duration must be positive" }
            require(cue.openness.isFinite() && cue.openness in 0.0..1.0) { "lip cue openness must be in 0..1" }
            require(Math.addExact(cue.offsetUs, cue.durationUs) <= actionDurationUs) { "lip cue exceeds action duration" }
        }
        require(cues.zipWithNext().all { (left, right) -> left.offsetUs <= right.offsetUs }) { "lip cues must be sorted" }
        require(cues.zipWithNext().all { (left, right) -> left.offsetUs + left.durationUs <= right.offsetUs }) {
            "lip cues must not overlap"
        }
    }

    private fun validateState(state: AnimationClipState) {
        require(state.clipId.isNotBlank()) { "clip id must not be blank" }
        require(state.durationUs > 0L) { "clip duration must be positive" }
        require(state.transform.x.isFinite() && state.transform.y.isFinite()) { "clip position must be finite" }
        require(state.transform.scale.isFinite() && state.transform.scale > 0.0) { "clip scale must be positive" }
        require(state.transform.rotationDeg.isFinite()) { "clip rotation must be finite" }
        require(state.transform.opacity.isFinite() && state.transform.opacity in 0.0..1.0) { "clip opacity must be in 0..1" }
    }

    private fun requireClip(id: String, clips: Map<String, AnimationClipState>): AnimationClipState {
        val state = requireNotNull(clips[id]) { "animation clip not found: $id" }
        require(state.clipId == id) { "animation clip map key mismatch: $id" }
        return state
    }

    private fun idle(builder: PatchBuilder, state: AnimationClipState, start: Long, end: Long, intensity: Double) {
        val q1 = interpolateTime(start, end, 0.25)
        val q2 = interpolateTime(start, end, 0.50)
        val q3 = interpolateTime(start, end, 0.75)
        val easing = smooth()
        builder.scalar(PROP_Y, start, state.transform.y, easing)
        builder.scalar(PROP_Y, q1, state.transform.y - 2.5 * intensity, easing)
        builder.scalar(PROP_Y, q2, state.transform.y, easing)
        builder.scalar(PROP_Y, q3, state.transform.y + 1.5 * intensity, easing)
        builder.scalar(PROP_Y, end, state.transform.y)
        builder.scalar(PROP_SCALE, start, state.transform.scale, easing)
        builder.scalar(PROP_SCALE, q2, state.transform.scale * (1.0 + 0.008 * intensity), easing)
        builder.scalar(PROP_SCALE, end, state.transform.scale)
    }

    private fun headIdle(builder: PatchBuilder, state: AnimationClipState, start: Long, end: Long, intensity: Double) {
        val mid = interpolateTime(start, end, 0.5)
        builder.scalar(PROP_ROTATION, start, state.transform.rotationDeg, smooth())
        builder.scalar(PROP_ROTATION, mid, state.transform.rotationDeg + 1.5 * intensity, smooth())
        builder.scalar(PROP_ROTATION, end, state.transform.rotationDeg)
    }

    private fun talkBody(builder: PatchBuilder, state: AnimationClipState, start: Long, end: Long, intensity: Double) {
        val mid = interpolateTime(start, end, 0.5)
        builder.scalar(PROP_Y, start, state.transform.y, smooth())
        builder.scalar(PROP_Y, mid, state.transform.y - 1.5 * intensity, smooth())
        builder.scalar(PROP_Y, end, state.transform.y)
        builder.scalar(PROP_ROTATION, start, state.transform.rotationDeg, smooth())
        builder.scalar(PROP_ROTATION, mid, state.transform.rotationDeg + 0.8 * intensity, smooth())
        builder.scalar(PROP_ROTATION, end, state.transform.rotationDeg)
    }

    private fun headTalk(builder: PatchBuilder, state: AnimationClipState, start: Long, end: Long, intensity: Double) {
        val q1 = interpolateTime(start, end, 0.33)
        val q2 = interpolateTime(start, end, 0.66)
        builder.scalar(PROP_ROTATION, start, state.transform.rotationDeg, smooth())
        builder.scalar(PROP_ROTATION, q1, state.transform.rotationDeg - 1.2 * intensity, smooth())
        builder.scalar(PROP_ROTATION, q2, state.transform.rotationDeg + 1.2 * intensity, smooth())
        builder.scalar(PROP_ROTATION, end, state.transform.rotationDeg)
    }

    private fun mouth(builder: PatchBuilder, state: AnimationClipState, request: CharacterActionRequest) {
        val start = request.startOffsetUs
        val end = Math.addExact(start, request.durationUs)
        builder.scalar(PROP_SCALE, start, state.transform.scale, hold())
        if (request.lipSyncCues.isEmpty()) {
            val pulseCount = (request.durationUs / 220_000L).toInt().coerceIn(1, 8)
            repeat(pulseCount) { index ->
                val openFraction = (index + 0.30) / pulseCount.toDouble()
                val closeFraction = (index + 0.72) / pulseCount.toDouble()
                val openAt = interpolateTime(start, end, openFraction.coerceIn(0.0, 1.0))
                val closeAt = interpolateTime(start, end, closeFraction.coerceIn(0.0, 1.0))
                if (openAt > start && openAt < end) {
                    builder.scalar(
                        PROP_SCALE,
                        openAt,
                        state.transform.scale * (1.0 + 0.12 * request.intensity),
                        smooth(),
                    )
                }
                if (closeAt > openAt && closeAt < end) {
                    builder.scalar(PROP_SCALE, closeAt, state.transform.scale, smooth())
                }
            }
        } else {
            request.lipSyncCues.forEach { cue ->
                val cueStart = Math.addExact(start, cue.offsetUs)
                val cueEnd = Math.addExact(cueStart, cue.durationUs)
                val attack = minOf(50_000L, maxOf(1L, cue.durationUs / 2L))
                val peak = minOf(cueEnd, Math.addExact(cueStart, attack))
                builder.scalar(PROP_SCALE, cueStart, state.transform.scale, smooth())
                builder.scalar(
                    PROP_SCALE,
                    peak,
                    state.transform.scale * (1.0 + 0.18 * cue.openness * request.intensity),
                    smooth(),
                )
                builder.scalar(PROP_SCALE, cueEnd, state.transform.scale, hold())
            }
        }
        builder.scalar(PROP_SCALE, end, state.transform.scale)
    }

    private fun flatMouthEffect(request: CharacterActionRequest, region: MouthRegion): EffectInstance {
        val animation = mouthOpennessAnimation(request)
        return EffectInstance(
            id = MOUTH_WARP_EFFECT_ID,
            typeId = MOUTH_WARP_TYPE_ID,
            parameters = mapOf(
                "center" to EffectParameter.Point(region.centerX, region.centerY),
                "radiusX" to EffectParameter.Number(region.radiusX),
                "radiusY" to EffectParameter.Number(region.radiusY),
                "openness" to EffectParameter.Number(0.0),
            ),
            animations = listOf(animation),
        )
    }

    private fun mouthOpennessAnimation(request: CharacterActionRequest): PropertyAnimation {
        val start = request.startOffsetUs
        val end = Math.addExact(start, request.durationUs)
        val frames = sortedMapOf<Long, ScalarFrame>()
        fun scalar(offsetUs: Long, value: Double, easing: Easing = Easing(EasingKind.LINEAR)) {
            frames[offsetUs] = ScalarFrame(value.coerceIn(0.0, 1.0), easing)
        }
        scalar(start, 0.0, hold())
        if (request.lipSyncCues.isEmpty()) {
            val pulseCount = (request.durationUs / 220_000L).toInt().coerceIn(1, 8)
            repeat(pulseCount) { index ->
                val openAt = interpolateTime(start, end, ((index + 0.30) / pulseCount.toDouble()).coerceIn(0.0, 1.0))
                val closeAt = interpolateTime(start, end, ((index + 0.72) / pulseCount.toDouble()).coerceIn(0.0, 1.0))
                if (openAt > start && openAt < end) scalar(openAt, 0.65 * request.intensity, smooth())
                if (closeAt > openAt && closeAt < end) scalar(closeAt, 0.0, smooth())
            }
        } else {
            request.lipSyncCues.forEach { cue ->
                val cueStart = Math.addExact(start, cue.offsetUs)
                val cueEnd = Math.addExact(cueStart, cue.durationUs)
                val attack = minOf(50_000L, maxOf(1L, cue.durationUs / 2L))
                val peak = minOf(cueEnd, Math.addExact(cueStart, attack))
                scalar(cueStart, 0.0, smooth())
                scalar(peak, cue.openness * request.intensity, smooth())
                scalar(cueEnd, 0.0, hold())
            }
        }
        scalar(end, 0.0)
        return PropertyAnimation(
            propertyId = "openness",
            keyframes = frames.entries.mapIndexed { index, (offsetUs, frame) ->
                PropertyKeyframe(
                    id = "mouth-openness-$index-$offsetUs",
                    offsetUs = offsetUs,
                    value = PropertyValue.Scalar(frame.value),
                    easingToNext = frame.easing,
                )
            },
        ).also { animation ->
            require(animation.validationIssues().isEmpty()) { "generated invalid mouth openness animation" }
        }
    }

    private fun blink(builder: PatchBuilder, state: AnimationClipState, start: Long, end: Long) {
        val blinkStart = interpolateTime(start, end, 0.35)
        val remaining = end - blinkStart
        if (remaining < 2L) {
            builder.scalar(PROP_OPACITY, start, state.transform.opacity)
            builder.scalar(PROP_OPACITY, end, state.transform.opacity)
            return
        }
        val closeDuration = minOf(45_000L, maxOf(1L, remaining / 3L))
        val closedAt = minOf(end, blinkStart + closeDuration)
        val reopenAt = minOf(end, closedAt + closeDuration)
        builder.scalar(PROP_OPACITY, start, state.transform.opacity, hold())
        builder.scalar(PROP_OPACITY, blinkStart, state.transform.opacity, hold())
        builder.scalar(PROP_OPACITY, closedAt, 0.0, hold())
        builder.scalar(PROP_OPACITY, reopenAt, state.transform.opacity, hold())
        builder.scalar(PROP_OPACITY, end, state.transform.opacity)
    }

    private fun walk(
        plan: PlanBuilder,
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
        body: AnimationClipState,
        start: Long,
        end: Long,
    ) {
        val bodyBuilder = plan.patch(body.clipId)
        bodyBuilder.scalar(PROP_X, start, body.transform.x, smooth())
        bodyBuilder.scalar(PROP_X, end, body.transform.x + request.direction * request.distancePx)
        bodyBuilder.scalar(PROP_Y, start, body.transform.y, smooth())
        val samples = 8
        for (index in 1 until samples) {
            val fraction = index.toDouble() / samples.toDouble()
            val time = interpolateTime(start, end, fraction)
            val bob = if (index % 2 == 0) 0.0 else -5.0 * request.intensity
            bodyBuilder.scalar(PROP_Y, time, body.transform.y + bob, smooth())
        }
        bodyBuilder.scalar(PROP_Y, end, body.transform.y)

        fun swing(id: String?, phase: Double) {
            if (id == null) return
            val state = requireClip(id, clips)
            val builder = plan.patch(id)
            val amplitude = 16.0 * request.intensity
            builder.scalar(PROP_ROTATION, start, state.transform.rotationDeg + amplitude * phase, smooth())
            for (index in 1 until samples) {
                val time = interpolateTime(start, end, index.toDouble() / samples.toDouble())
                val sign = if (index % 2 == 0) phase else -phase
                builder.scalar(PROP_ROTATION, time, state.transform.rotationDeg + amplitude * sign, smooth())
            }
            builder.scalar(PROP_ROTATION, end, state.transform.rotationDeg + amplitude * phase)
        }
        swing(request.rig.leftArmClipId, 1.0)
        swing(request.rig.rightArmClipId, -1.0)
        swing(request.rig.leftLegClipId, -1.0)
        swing(request.rig.rightLegClipId, 1.0)
        request.rig.headClipId?.let { id ->
            val head = requireClip(id, clips)
            val builder = plan.patch(id)
            builder.scalar(PROP_Y, start, head.transform.y, smooth())
            builder.scalar(PROP_Y, interpolateTime(start, end, 0.5), head.transform.y - 2.0 * request.intensity, smooth())
            builder.scalar(PROP_Y, end, head.transform.y)
        }
    }

    private fun crouch(
        plan: PlanBuilder,
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
        body: AnimationClipState,
        start: Long,
        end: Long,
        reverse: Boolean,
    ) {
        val drop = 42.0 * request.intensity
        val crouchedScale = body.transform.scale * (1.0 - 0.035 * request.intensity).coerceAtLeast(0.8)
        val builder = plan.patch(body.clipId)
        val fromY = if (reverse) body.transform.y + drop else body.transform.y
        val toY = if (reverse) body.transform.y else body.transform.y + drop
        val fromScale = if (reverse) crouchedScale else body.transform.scale
        val toScale = if (reverse) body.transform.scale else crouchedScale
        builder.scalar(PROP_Y, start, fromY, smooth())
        builder.scalar(PROP_Y, end, toY)
        builder.scalar(PROP_SCALE, start, fromScale, smooth())
        builder.scalar(PROP_SCALE, end, toScale)

        request.rig.headClipId?.let { id ->
            val state = requireClip(id, clips)
            val b = plan.patch(id)
            val delta = 12.0 * request.intensity
            b.scalar(PROP_Y, start, if (reverse) state.transform.y + delta else state.transform.y, smooth())
            b.scalar(PROP_Y, end, if (reverse) state.transform.y else state.transform.y + delta)
        }
        listOfNotNull(request.rig.leftLegClipId, request.rig.rightLegClipId).forEachIndexed { index, id ->
            val state = requireClip(id, clips)
            val bend = (if (index == 0) -12.0 else 12.0) * request.intensity
            val b = plan.patch(id)
            b.scalar(PROP_ROTATION, start, if (reverse) state.transform.rotationDeg + bend else state.transform.rotationDeg, smooth())
            b.scalar(PROP_ROTATION, end, if (reverse) state.transform.rotationDeg else state.transform.rotationDeg + bend)
        }
    }

    private fun pickup(
        plan: PlanBuilder,
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
        body: AnimationClipState,
        start: Long,
        end: Long,
        putDown: Boolean,
    ) {
        val objectId = requireNotNull(request.rig.heldObjectClipId) { "object clip required" }
        val objectState = requireClip(objectId, clips)
        val mid = interpolateTime(start, end, 0.5)
        val reach = interpolateTime(start, end, 0.62)
        val bodyBuilder = plan.patch(body.clipId)
        bodyBuilder.scalar(PROP_Y, start, body.transform.y, smooth())
        bodyBuilder.scalar(PROP_Y, mid, body.transform.y + 30.0 * request.intensity, smooth())
        bodyBuilder.scalar(PROP_Y, end, body.transform.y)

        request.rig.rightArmClipId?.let { id ->
            val arm = requireClip(id, clips)
            val b = plan.patch(id)
            b.scalar(PROP_ROTATION, start, arm.transform.rotationDeg, smooth())
            b.scalar(PROP_ROTATION, mid, arm.transform.rotationDeg + 28.0 * request.direction * request.intensity, smooth())
            b.scalar(PROP_ROTATION, end, arm.transform.rotationDeg)
        }

        val defaultX = body.transform.x + 30.0 * request.direction
        val defaultY = body.transform.y - 20.0
        val targetX = request.targetX ?: defaultX
        val targetY = request.targetY ?: defaultY
        val objectBuilder = plan.patch(objectId)
        objectBuilder.scalar(PROP_X, start, objectState.transform.x, smooth())
        objectBuilder.scalar(PROP_Y, start, objectState.transform.y, smooth())
        if (!putDown) {
            objectBuilder.scalar(PROP_X, reach, objectState.transform.x, smooth())
            objectBuilder.scalar(PROP_Y, reach, objectState.transform.y, smooth())
        }
        objectBuilder.scalar(PROP_X, end, targetX)
        objectBuilder.scalar(PROP_Y, end, targetY)
    }

    private fun point(
        plan: PlanBuilder,
        request: CharacterActionRequest,
        clips: Map<String, AnimationClipState>,
        body: AnimationClipState,
        start: Long,
        end: Long,
    ) {
        val mid = interpolateTime(start, end, 0.55)
        val bodyBuilder = plan.patch(body.clipId)
        bodyBuilder.scalar(PROP_ROTATION, start, body.transform.rotationDeg, smooth())
        bodyBuilder.scalar(PROP_ROTATION, mid, body.transform.rotationDeg + 2.5 * request.direction * request.intensity, smooth())
        bodyBuilder.scalar(PROP_ROTATION, end, body.transform.rotationDeg)
        request.rig.rightArmClipId?.let { id ->
            val arm = requireClip(id, clips)
            val builder = plan.patch(id)
            builder.scalar(PROP_ROTATION, start, arm.transform.rotationDeg, smooth())
            builder.scalar(PROP_ROTATION, mid, arm.transform.rotationDeg + 35.0 * request.direction * request.intensity, smooth())
            builder.scalar(PROP_ROTATION, end, arm.transform.rotationDeg)
        }
    }

    private fun enterExit(
        builder: PatchBuilder,
        state: AnimationClipState,
        request: CharacterActionRequest,
        start: Long,
        end: Long,
        entering: Boolean,
    ) {
        val offset = request.direction * request.distancePx
        if (entering) {
            builder.scalar(PROP_X, start, state.transform.x - offset, smooth())
            builder.scalar(PROP_X, end, state.transform.x)
            builder.scalar(PROP_OPACITY, start, 0.0, smooth())
            builder.scalar(PROP_OPACITY, end, state.transform.opacity)
        } else {
            builder.scalar(PROP_X, start, state.transform.x, smooth())
            builder.scalar(PROP_X, end, state.transform.x + offset)
            builder.scalar(PROP_OPACITY, start, state.transform.opacity, smooth())
            builder.scalar(PROP_OPACITY, end, 0.0)
        }
    }

    private fun panZoom(
        builder: PatchBuilder,
        state: AnimationClipState,
        start: Long,
        end: Long,
        panX: Double,
        panY: Double,
        zoomFactor: Double,
    ) {
        require(zoomFactor.isFinite() && zoomFactor > 0.0) { "zoom factor must be finite and positive" }
        builder.scalar(PROP_X, start, state.transform.x, smooth())
        builder.scalar(PROP_X, end, state.transform.x + panX)
        builder.scalar(PROP_Y, start, state.transform.y, smooth())
        builder.scalar(PROP_Y, end, state.transform.y + panY)
        builder.scalar(PROP_SCALE, start, state.transform.scale, smooth())
        builder.scalar(PROP_SCALE, end, state.transform.scale * zoomFactor)
    }

    private fun interpolateTime(start: Long, end: Long, fraction: Double): Long {
        require(fraction.isFinite() && fraction in 0.0..1.0)
        return start + ((end - start).toDouble() * fraction).toLong()
    }

    private fun smooth(): Easing = Easing(EasingKind.EASE_IN_OUT)
    private fun hold(): Easing = Easing(EasingKind.HOLD)
}

private class PlanBuilder(
    private val clips: Map<String, AnimationClipState>,
) {
    private val builders = linkedMapOf<String, PatchBuilder>()
    private val effects = linkedMapOf<String, MutableList<EffectInstance>>()

    fun patch(clipId: String): PatchBuilder {
        require(clips.containsKey(clipId)) { "animation clip not found: $clipId" }
        return builders.getOrPut(clipId) { PatchBuilder(clipId) }
    }

    fun effect(clipId: String, effect: EffectInstance) {
        require(clips.containsKey(clipId)) { "animation clip not found: $clipId" }
        require(effect.validationIssues().isEmpty()) { "animation effect must be valid" }
        val target = effects.getOrPut(clipId) { mutableListOf() }
        require(target.none { it.id == effect.id }) { "animation effect id must be unique for clip: $clipId" }
        target += effect
    }

    fun build(): CharacterAnimationPlan {
        val patches = builders.values.map(PatchBuilder::build).filter { it.propertyAnimations.isNotEmpty() }
        val effectPatches = effects.map { (clipId, clipEffects) ->
            ClipEffectPatch(clipId = clipId, effects = clipEffects.toList())
        }
        require(patches.isNotEmpty() || effectPatches.isNotEmpty()) { "animation plan must not be empty" }
        return CharacterAnimationPlan(patches = patches, effectPatches = effectPatches)
    }
}

private data class ScalarFrame(
    val value: Double,
    val easing: Easing,
)

private class PatchBuilder(
    private val clipId: String,
) {
    private val frames = linkedMapOf<String, java.util.SortedMap<Long, ScalarFrame>>()

    fun scalar(
        propertyId: String,
        offsetUs: Long,
        value: Double,
        easing: Easing = Easing(EasingKind.LINEAR),
    ) {
        require(offsetUs >= 0L) { "animation keyframe offset must be non-negative" }
        require(value.isFinite()) { "animation scalar value must be finite" }
        require(easing.validationIssues().isEmpty()) { "animation easing must be valid" }
        when (propertyId) {
            PROP_SCALE -> require(value > 0.0) { "animation scale must be positive" }
            PROP_OPACITY -> require(value in 0.0..1.0) { "animation opacity must be in 0..1" }
        }
        val track = frames.getOrPut(propertyId) { sortedMapOf() }
        track[offsetUs] = ScalarFrame(value, easing)
    }

    fun build(): ClipAnimationPatch {
        val animations = frames.toSortedMap().map { (propertyId, timedFrames) ->
            PropertyAnimation(
                propertyId = propertyId,
                keyframes = timedFrames.entries.mapIndexed { index, (offsetUs, frame) ->
                    PropertyKeyframe(
                        id = "anim-${clipId.sanitize()}-${propertyId.sanitize()}-$index-$offsetUs",
                        offsetUs = offsetUs,
                        value = PropertyValue.Scalar(frame.value),
                        easingToNext = frame.easing,
                    )
                },
            ).also { animation ->
                require(animation.validationIssues().isEmpty()) {
                    "generated invalid animation for $clipId/$propertyId: ${animation.validationIssues().first()}"
                }
            }
        }
        return ClipAnimationPatch(clipId = clipId, propertyAnimations = animations)
    }

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9_-]"), "-")
}
