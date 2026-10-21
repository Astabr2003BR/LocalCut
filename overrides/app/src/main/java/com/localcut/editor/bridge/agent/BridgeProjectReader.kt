package com.localcut.editor.bridge.agent

import com.localcut.editor.data.project.jsonString
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.session.ProjectSession

class BridgeProjectReader(
    private val session: ProjectSession,
    private val playback: BridgePlaybackPort,
    private val jobs: BridgeJobRegistry,
) {
    fun projectStateJson(): String {
        val state = session.state
        val project = state.project
        val clipCount = project.tracks.sumOf { it.clips.size }
        return buildString {
            append('{')
            field("projectId", jsonString(project.id)); comma()
            field("revision", state.revision.toString()); comma()
            field("title", jsonString(project.title)); comma()
            field("width", project.width.toString()); comma()
            field("height", project.height.toString()); comma()
            field("fps", project.fps.toString()); comma()
            field("durationUs", TimelineMath.durationUs(project).toString()); comma()
            field("trackCount", project.tracks.size.toString()); comma()
            field("clipCount", clipCount.toString()); comma()
            field("selectedClipId", state.selectedClipId?.let(::jsonString) ?: "null"); comma()
            field("selectedTrackId", state.selectedTrackId?.let(::jsonString) ?: "null"); comma()
            field("playheadUs", playback.positionUs.toString()); comma()
            field("canUndo", state.canUndo.toString()); comma()
            field("canRedo", state.canRedo.toString()); comma()
            append("\"issues\":[")
            state.issues.forEachIndexed { index, issue ->
                if (index > 0) comma()
                append('{')
                field("code", jsonString(issue.code.name)); comma()
                field("assetId", issue.assetId?.let(::jsonString) ?: "null"); comma()
                field("message", jsonString(sanitize(issue.message)))
                append('}')
            }
            append("]}")
        }
    }

    fun timelineSummaryJson(): String {
        val project = session.state.project
        return buildString {
            append('{')
            field("projectId", jsonString(project.id)); comma()
            field("revision", session.state.revision.toString()); comma()
            field("durationUs", TimelineMath.durationUs(project).toString()); comma()
            append("\"tracks\":[")
            project.tracks.sortedBy { it.order }.forEachIndexed { index, track ->
                if (index > 0) comma()
                append('{')
                field("id", jsonString(track.id)); comma()
                field("type", jsonString(track.type.name)); comma()
                field("order", track.order.toString()); comma()
                field("clipCount", track.clips.size.toString())
                append('}')
            }
            append("]}")
        }
    }

    fun tracksJson(): String {
        val project = session.state.project
        return buildString {
            append('{')
            field("revision", session.state.revision.toString()); comma()
            append("\"tracks\":[")
            project.tracks.sortedBy { it.order }.forEachIndexed { index, track ->
                if (index > 0) comma()
                append('{')
                field("id", jsonString(track.id)); comma()
                field("type", jsonString(track.type.name)); comma()
                field("order", track.order.toString()); comma()
                field("collisionPolicy", jsonString(track.collisionPolicy.name)); comma()
                field("muted", track.muted.toString()); comma()
                field("solo", track.solo.toString()); comma()
                field("hidden", track.hidden.toString()); comma()
                field("locked", track.locked.toString()); comma()
                field("clipCount", track.clips.size.toString())
                append('}')
            }
            append("]}")
        }
    }

    fun clipsJson(trackId: String? = null): String {
        val project = session.state.project
        val tracks = if (trackId == null) project.tracks else project.tracks.filter { it.id == trackId }
        return buildString {
            append('{')
            field("revision", session.state.revision.toString()); comma()
            append("\"clips\":[")
            var first = true
            tracks.forEach { track ->
                track.clips.forEach { clip ->
                    if (!first) comma()
                    first = false
                    append('{')
                    field("id", jsonString(clip.id)); comma()
                    field("assetId", jsonString(clip.assetId)); comma()
                    field("trackId", jsonString(track.id)); comma()
                    field("trackType", jsonString(track.type.name)); comma()
                    field("startUs", clip.startUs.toString()); comma()
                    field("durationUs", clip.durationUs.toString()); comma()
                    field("sourceInUs", clip.sourceInUs.toString()); comma()
                    field("sourceOutUs", clip.sourceOutUs.toString()); comma()
                    field("opacity", clip.opacity.toString()); comma()
                    field("volume", clip.volume.toString()); comma()
                    field("keyframeCount", clip.keyframes.size.toString())
                    append('}')
                }
            }
            append("]}")
        }
    }

    fun selectedClipJson(): String {
        val clipId = session.state.selectedClipId ?: return "{\"selectedClip\":null}"
        val project = session.state.project
        val track = project.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
            ?: return "{\"selectedClip\":null}"
        val clip = project.findClip(clipId) ?: return "{\"selectedClip\":null}"
        return """{"selectedClip":{"id":${jsonString(clip.id)},"assetId":${jsonString(clip.assetId)},"trackId":${jsonString(track.id)},"startUs":${clip.startUs},"durationUs":${clip.durationUs}}}"""
    }

    fun playheadJson(): String = "{\"positionUs\":${playback.positionUs}}"

    fun errorsJson(): String {
        val state = session.state
        return buildString {
            append('{')
            field("revision", state.revision.toString()); comma()
            field("lastError", state.lastError?.let { jsonString(sanitize(it)) } ?: "null"); comma()
            append("\"issues\":[")
            state.issues.forEachIndexed { index, issue ->
                if (index > 0) comma()
                append('{')
                field("code", jsonString(issue.code.name)); comma()
                field("message", jsonString(sanitize(issue.message)))
                append('}')
            }
            append("]}")
        }
    }

    fun exportStatusJson(jobId: String?): String {
        val job = jobId?.let(jobs::get) ?: jobs.active()
        if (job == null) return "{\"job\":null}"
        return buildString {
            append("{\"job\":{")
            field("jobId", jsonString(job.jobId)); comma()
            field("state", jsonString(job.state.name)); comma()
            field("progressPercent", job.progressPercent?.toString() ?: "null"); comma()
            field("errorCode", job.errorCode?.let { jsonString(it.wireName) } ?: "null"); comma()
            field("message", job.message?.let { jsonString(sanitize(it)) } ?: "null"); comma()
            field("result", job.resultJson ?: "null")
            append("}}")
        }
    }

    private fun sanitize(message: String): String = message
        .replace(Regex("(?:file|content)://\\S+"), "[redacted-uri]")
        .take(500)

    private fun StringBuilder.field(name: String, encodedValue: String) {
        append(jsonString(name)); append(':'); append(encodedValue)
    }

    private fun StringBuilder.comma() {
        append(',')
    }
}
