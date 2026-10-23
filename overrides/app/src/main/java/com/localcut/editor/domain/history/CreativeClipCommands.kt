package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.time.TimeMapEvaluator
import com.localcut.editor.domain.timeline.TimelineValidator

data class SetClipEffectsCommand(
    val clipId: String,
    val effects: List<EffectInstance>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult = replaceCreativeClip(project, clipId) { clip ->
        clip.copy(effects = effects)
    }
}

data class SetClipCompositingCommand(
    val clipId: String,
    val compositing: CompositingSettings,
) : EditorCommand {
    override fun apply(project: Project): CommandResult = replaceCreativeClip(project, clipId) { clip ->
        clip.copy(compositing = compositing)
    }
}

data class SetClipPropertyAnimationsCommand(
    val clipId: String,
    val propertyAnimations: List<PropertyAnimation>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult = replaceCreativeClip(project, clipId) { clip ->
        clip.copy(propertyAnimations = propertyAnimations)
    }
}

data class SetClipTimeMapCommand(
    val clipId: String,
    val timeMap: TimeMap,
) : EditorCommand {
    override fun apply(project: Project): CommandResult = replaceCreativeClip(project, clipId) { clip ->
        require(timeMap.validationIssues().isEmpty()) { "invalid time map: ${timeMap.validationIssues().first()}" }
        val sourceRangeUs = clip.sourceOutUs - clip.sourceInUs
        require(sourceRangeUs > 0L) { "clip source range must be positive" }
        val durationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, timeMap)
        clip.copy(durationUs = durationUs, timeMap = timeMap)
    }
}

private data class RestoreCreativeClipCommand(
    val clip: Clip,
) : EditorCommand {
    override fun apply(project: Project): CommandResult = replaceCreativeClip(project, clip.id) { clip }
}

private fun replaceCreativeClip(
    project: Project,
    clipId: String,
    transform: (Clip) -> Clip,
): CommandResult {
    var previous: Clip? = null
    var replacement: Clip? = null
    var found = false
    val tracks = project.tracks.map { track ->
        val index = track.clips.indexOfFirst { it.id == clipId }
        if (index < 0) return@map track
        require(!found) { "clip $clipId exists in more than one track" }
        found = true
        val old = track.clips[index]
        val next = transform(old)
        require(next.id == old.id) { "creative clip edit cannot change clip id" }
        previous = old
        replacement = next
        track.copy(clips = track.clips.toMutableList().also { it[index] = next })
    }
    require(found) { "clip $clipId not found" }
    val edited = project.copy(tracks = tracks)
    val issues = TimelineValidator.validate(edited)
    require(issues.isEmpty()) { "invalid creative clip edit: ${issues.first()}" }
    return CommandResult(
        project = edited,
        inverse = RestoreCreativeClipCommand(requireNotNull(previous)),
    )
}
