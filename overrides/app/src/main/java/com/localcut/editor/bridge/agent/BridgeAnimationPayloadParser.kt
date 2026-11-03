package com.localcut.editor.bridge.agent

import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import com.localcut.editor.domain.animation.AnimationClipState
import com.localcut.editor.domain.animation.CharacterAction
import com.localcut.editor.domain.animation.CharacterActionRequest
import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.animation.CharacterRigBinding
import com.localcut.editor.domain.animation.CharacterSceneAnimationPlanner
import com.localcut.editor.domain.animation.LipSyncCue
import com.localcut.editor.domain.animation.SceneAnimationKind
import com.localcut.editor.domain.animation.SceneAnimationRequest
import com.localcut.editor.domain.model.Project

object BridgeAnimationPayloadParser {
    fun character(payloadJson: String, project: Project): CharacterAnimationPlan {
        val payload = parse(payloadJson)
        val action = enumValue<CharacterAction>(payload.requiredString("action"), "character action")
        val request = CharacterActionRequest(
            action = action,
            rig = CharacterRigBinding(
                bodyClipId = payload.requiredString("bodyClipId"),
                headClipId = payload.optionalString("headClipId"),
                eyesClipId = payload.optionalString("eyesClipId"),
                mouthClipId = payload.optionalString("mouthClipId"),
                leftArmClipId = payload.optionalString("leftArmClipId"),
                rightArmClipId = payload.optionalString("rightArmClipId"),
                leftLegClipId = payload.optionalString("leftLegClipId"),
                rightLegClipId = payload.optionalString("rightLegClipId"),
                heldObjectClipId = payload.optionalString("heldObjectClipId"),
            ),
            startOffsetUs = payload.requiredLong("startOffsetUs"),
            durationUs = payload.requiredLong("durationUs"),
            intensity = payload.optionalDouble("intensity") ?: 1.0,
            direction = payload.optionalDouble("direction") ?: 1.0,
            distancePx = payload.optionalDouble("distancePx") ?: 160.0,
            targetX = payload.optionalDouble("targetX"),
            targetY = payload.optionalDouble("targetY"),
            lipSyncCues = payload.optionalArray("lipSyncCues").map { raw ->
                val cue = raw.asObject()
                LipSyncCue(
                    offsetUs = cue.requiredLong("offsetUs"),
                    durationUs = cue.requiredLong("durationUs"),
                    openness = cue.requiredDouble("openness"),
                )
            },
        )
        return CharacterSceneAnimationPlanner.character(request, clipStates(project))
    }

    fun scene(payloadJson: String, project: Project): CharacterAnimationPlan {
        val payload = parse(payloadJson)
        val kind = enumValue<SceneAnimationKind>(payload.requiredString("kind"), "scene animation kind")
        val request = SceneAnimationRequest(
            kind = kind,
            primaryClipId = payload.requiredString("primaryClipId"),
            startOffsetUs = payload.requiredLong("startOffsetUs"),
            durationUs = payload.requiredLong("durationUs"),
            incomingClipId = payload.optionalString("incomingClipId"),
            secondaryClipIds = payload.optionalArray("secondaryClipIds").map { it.asString().also { id -> require(id.isNotBlank()) } },
            panX = payload.optionalDouble("panX") ?: 0.0,
            panY = payload.optionalDouble("panY") ?: 0.0,
            zoomTo = payload.optionalDouble("zoomTo") ?: 1.0,
        )
        return CharacterSceneAnimationPlanner.scene(request, clipStates(project))
    }

    private fun clipStates(project: Project): Map<String, AnimationClipState> {
        val states = linkedMapOf<String, AnimationClipState>()
        project.tracks.forEach { track ->
            track.clips.forEach { clip ->
                require(states.put(
                    clip.id,
                    AnimationClipState(
                        clipId = clip.id,
                        durationUs = clip.durationUs,
                        transform = clip.transform,
                    ),
                ) == null) { "clip ${clip.id} exists in more than one track" }
            }
        }
        return states
    }

    private fun parse(payloadJson: String): JObject = try {
        MiniJson.parse(payloadJson).asObject()
    } catch (error: Throwable) {
        throw IllegalArgumentException("animation payload must be a JSON object", error)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T =
        runCatching { enumValueOf<T>(value.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("unsupported $label") }

    private fun JObject.requiredString(key: String): String = required(key).asString().also {
        require(it.isNotBlank()) { "$key must not be blank" }
    }

    private fun JObject.requiredLong(key: String): Long = required(key).asLong()
    private fun JObject.requiredDouble(key: String): Double = required(key).asDouble()

    private fun JObject.optionalString(key: String): String? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asString().also { require(it.isNotBlank()) { "$key must not be blank" } }
    }

    private fun JObject.optionalDouble(key: String): Double? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asDouble()
    }

    private fun JObject.optionalArray(key: String) = when (val value = optional(key)) {
        null, JNull -> emptyList()
        else -> value.asArray().values
    }
}
