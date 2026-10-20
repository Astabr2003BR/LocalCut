package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.animation.TransformInterpolation
import com.localcut.editor.domain.model.*

enum class TimelineErrorCode {
    TRACK_NOT_FOUND,
    CLIP_NOT_FOUND,
    DUPLICATE_ASSET_ID,
    DUPLICATE_CLIP_ID,
    INVALID_PROJECT,
    INVALID_START,
    INVALID_SOURCE_RANGE,
    SOURCE_RANGE_OUT_OF_BOUNDS,
    SPLIT_OUTSIDE_CLIP
}

class TimelineEditException(val code: TimelineErrorCode, message: String) : IllegalArgumentException(message)

object TimelineOps {
    fun insertAssetClip(project: Project, trackId: String, asset: Asset, clip: Clip): Project {
        if (project.assets.any { it.id == asset.id }) {
            throw TimelineEditException(TimelineErrorCode.DUPLICATE_ASSET_ID, "duplicate asset id ${asset.id}")
        }
        if (clip.assetId != asset.id) {
            throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "clip ${clip.id} does not reference imported asset ${asset.id}")
        }
        if (project.findClip(clip.id) != null) {
            throw TimelineEditException(TimelineErrorCode.DUPLICATE_CLIP_ID, "duplicate clip id ${clip.id}")
        }
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        val track = project.tracks[trackIndex]
        val tracks = project.tracks.toMutableList().also {
            it[trackIndex] = track.copy(clips = track.clips + clip)
        }
        return validated(project.copy(assets = project.assets + asset, tracks = tracks))
    }

    fun removeImportedAssetClip(project: Project, trackId: String, assetId: String, clipId: String): Project {
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        val track = project.tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId && it.assetId == assetId }
        if (clipIndex < 0) throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found for asset $assetId")
        val otherReferences = project.tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .any { it.id != clipId && it.assetId == assetId }
        if (otherReferences) {
            throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "asset $assetId is still referenced")
        }
        val clips = track.clips.toMutableList().also { it.removeAt(clipIndex) }
        val tracks = project.tracks.toMutableList().also { it[trackIndex] = track.copy(clips = clips) }
        return validated(project.copy(assets = project.assets.filterNot { it.id == assetId }, tracks = tracks))
    }

    fun insertClip(project: Project, trackId: String, clip: Clip): Project {
        if (project.findClip(clip.id) != null) throw TimelineEditException(TimelineErrorCode.DUPLICATE_CLIP_ID, "duplicate clip id ${clip.id}")
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        val tracks = project.tracks.toMutableList()
        tracks[index] = tracks[index].copy(clips = tracks[index].clips + clip)
        return validated(project.copy(tracks = tracks))
    }

    fun moveClip(project: Project, clipId: String, newStartUs: Long): Project {
        if (newStartUs < 0) throw TimelineEditException(TimelineErrorCode.INVALID_START, "start must be >= 0")
        return updateClip(project, clipId) { it.copy(startUs = newStartUs) }
    }

    fun setTransform(project: Project, clipId: String, transform: TransformValue): Project {
        return updateClip(project, clipId) { it.copy(transform = transform) }
    }

    fun setVolume(project: Project, clipId: String, volume: Double): Project {
        return updateClip(project, clipId) { it.copy(volume = volume) }
    }

    fun setAudioFades(project: Project, clipId: String, fadeInUs: Long, fadeOutUs: Long): Project {
        return updateClip(project, clipId) { it.copy(fadeInUs = fadeInUs, fadeOutUs = fadeOutUs) }
    }

    fun setKeyframes(project: Project, clipId: String, keyframes: List<TransformKeyframe>): Project {
        val ordered = keyframes.sortedBy { it.offsetUs }
        return updateClip(project, clipId) { it.copy(keyframes = ordered) }
    }

    fun trimClip(project: Project, clipId: String, newSourceInUs: Long, newSourceOutUs: Long): Project {
        if (newSourceInUs < 0 || newSourceOutUs <= newSourceInUs) {
            throw TimelineEditException(TimelineErrorCode.INVALID_SOURCE_RANGE, "invalid source range")
        }
        val clip = project.findClip(clipId) ?: throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
        val asset = project.assets.firstOrNull { it.id == clip.assetId }
            ?: throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "asset ${clip.assetId} missing")
        if (asset.sourceDurationUs != null && newSourceOutUs > asset.sourceDurationUs) {
            throw TimelineEditException(TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS, "trim exceeds source duration")
        }
        val duration = newSourceOutUs - newSourceInUs
        val keyframes = remapKeyframesForTrim(clip, newSourceInUs, newSourceOutUs)
        val (fadeInUs, fadeOutUs) = fitAudioFades(clip.fadeInUs, clip.fadeOutUs, duration)
        return updateClip(project, clipId) {
            it.copy(
                sourceInUs = newSourceInUs,
                sourceOutUs = newSourceOutUs,
                durationUs = duration,
                keyframes = keyframes,
                fadeInUs = fadeInUs,
                fadeOutUs = fadeOutUs,
            )
        }
    }

    fun trimClipEdges(project: Project, clipId: String, newStartUs: Long, newEndUs: Long): Project {
        if (newStartUs < 0) throw TimelineEditException(TimelineErrorCode.INVALID_START, "start must be >= 0")
        if (newEndUs <= newStartUs) {
            throw TimelineEditException(TimelineErrorCode.INVALID_SOURCE_RANGE, "end must be greater than start")
        }
        val clip = project.findClip(clipId)
            ?: throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
        val asset = project.assets.firstOrNull { it.id == clip.assetId }
            ?: throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "asset ${clip.assetId} missing")
        val oldEndUs = checkedEnd(clip)
        val leftDeltaUs = newStartUs - clip.startUs
        val rightDeltaUs = newEndUs - oldEndUs
        val newSourceInUs = checkedSourceAdd(clip.sourceInUs, leftDeltaUs)
        val newSourceOutUs = checkedSourceAdd(clip.sourceOutUs, rightDeltaUs)
        if (newSourceInUs < 0 || newSourceOutUs <= newSourceInUs) {
            throw TimelineEditException(TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS, "trim exceeds source start")
        }
        if (asset.sourceDurationUs != null && newSourceOutUs > asset.sourceDurationUs) {
            throw TimelineEditException(TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS, "trim exceeds source duration")
        }
        val durationUs = newEndUs - newStartUs
        val keyframes = remapKeyframesForTrim(clip, newSourceInUs, newSourceOutUs)
        val (fadeInUs, fadeOutUs) = fitAudioFades(clip.fadeInUs, clip.fadeOutUs, durationUs)
        return updateClip(project, clipId) {
            it.copy(
                startUs = newStartUs,
                durationUs = durationUs,
                sourceInUs = newSourceInUs,
                sourceOutUs = newSourceOutUs,
                keyframes = keyframes,
                fadeInUs = fadeInUs,
                fadeOutUs = fadeOutUs,
            )
        }
    }

    fun splitClip(project: Project, clipId: String, splitAtProjectUs: Long): Project {
        val location = locate(project, clipId)
        val original = location.clip
        val end = checkedEnd(original)
        if (splitAtProjectUs <= original.startUs || splitAtProjectUs >= end) {
            throw TimelineEditException(TimelineErrorCode.SPLIT_OUTSIDE_CLIP, "split must be inside clip")
        }
        val firstDuration = splitAtProjectUs - original.startUs
        val secondDuration = original.durationUs - firstDuration
        val splitSourceUs = original.sourceInUs + firstDuration
        val (firstKeyframes, secondKeyframes) = splitKeyframes(original, firstDuration)
        val first = original.copy(
            durationUs = firstDuration,
            sourceOutUs = splitSourceUs,
            keyframes = firstKeyframes,
            fadeInUs = minOf(original.fadeInUs, firstDuration),
            fadeOutUs = 0L,
        )
        val second = original.copy(
            id = uniqueSplitId(project, original.id),
            startUs = splitAtProjectUs,
            durationUs = secondDuration,
            sourceInUs = splitSourceUs,
            keyframes = secondKeyframes,
            fadeInUs = 0L,
            fadeOutUs = minOf(original.fadeOutUs, secondDuration),
        )
        val track = project.tracks[location.trackIndex]
        val clips = track.clips.toMutableList().also {
            it[location.clipIndex] = first
            it.add(location.clipIndex + 1, second)
        }
        val tracks = project.tracks.toMutableList().also { it[location.trackIndex] = track.copy(clips = clips) }
        return validated(project.copy(tracks = tracks))
    }

    fun deleteClip(project: Project, clipId: String): Project {
        val location = locate(project, clipId)
        val track = project.tracks[location.trackIndex]
        val clips = track.clips.toMutableList().also { it.removeAt(location.clipIndex) }
        val tracks = project.tracks.toMutableList().also { it[location.trackIndex] = track.copy(clips = clips) }
        return validated(project.copy(tracks = tracks))
    }

    private fun updateClip(project: Project, clipId: String, update: (Clip) -> Clip): Project {
        val location = locate(project, clipId)
        val track = project.tracks[location.trackIndex]
        val clips = track.clips.toMutableList().also { it[location.clipIndex] = update(location.clip) }
        val tracks = project.tracks.toMutableList().also { it[location.trackIndex] = track.copy(clips = clips) }
        return validated(project.copy(tracks = tracks))
    }

    private data class Location(val trackIndex: Int, val clipIndex: Int, val clip: Clip)

    private fun locate(project: Project, clipId: String): Location {
        project.tracks.forEachIndexed { ti, track ->
            track.clips.forEachIndexed { ci, clip -> if (clip.id == clipId) return Location(ti, ci, clip) }
        }
        throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
    }

    private fun remapKeyframesForTrim(
        clip: Clip,
        newSourceInUs: Long,
        newSourceOutUs: Long
    ): List<TransformKeyframe> {
        if (clip.keyframes.isEmpty()) return emptyList()
        val oldWindowStartUs = newSourceInUs - clip.sourceInUs
        val oldWindowEndUs = newSourceOutUs - clip.sourceInUs
        val newDurationUs = newSourceOutUs - newSourceInUs
        return buildList {
            add(TransformKeyframe(0L, TransformInterpolation.valueAt(clip.keyframes, oldWindowStartUs)))
            addAll(
                clip.keyframes
                    .filter { it.offsetUs > oldWindowStartUs && it.offsetUs < oldWindowEndUs }
                    .map { it.copy(offsetUs = it.offsetUs - oldWindowStartUs) }
            )
            add(TransformKeyframe(newDurationUs, TransformInterpolation.valueAt(clip.keyframes, oldWindowEndUs)))
        }.distinctBy { it.offsetUs }.sortedBy { it.offsetUs }
    }

    private fun splitKeyframes(clip: Clip, splitOffsetUs: Long): Pair<List<TransformKeyframe>, List<TransformKeyframe>> {
        if (clip.keyframes.isEmpty()) return emptyList<TransformKeyframe>() to emptyList()
        val boundary = TransformInterpolation.valueAt(clip.keyframes, splitOffsetUs)
        val first = buildList {
            addAll(clip.keyframes.filter { it.offsetUs < splitOffsetUs })
            add(TransformKeyframe(splitOffsetUs, boundary))
        }.distinctBy { it.offsetUs }.sortedBy { it.offsetUs }
        val second = buildList {
            add(TransformKeyframe(0L, boundary))
            addAll(
                clip.keyframes
                    .filter { it.offsetUs > splitOffsetUs }
                    .map { it.copy(offsetUs = it.offsetUs - splitOffsetUs) }
            )
        }.distinctBy { it.offsetUs }.sortedBy { it.offsetUs }
        return first to second
    }

    private fun fitAudioFades(fadeInUs: Long, fadeOutUs: Long, durationUs: Long): Pair<Long, Long> {
        if (durationUs <= 0L) return 0L to 0L
        val clampedIn = fadeInUs.coerceIn(0L, durationUs)
        val clampedOut = fadeOutUs.coerceIn(0L, durationUs)
        if (clampedIn <= durationUs - clampedOut) return clampedIn to clampedOut
        val total = clampedIn.toDouble() + clampedOut.toDouble()
        if (total <= 0.0) return 0L to 0L
        val fittedIn = (durationUs.toDouble() * clampedIn.toDouble() / total).toLong().coerceIn(0L, durationUs)
        return fittedIn to (durationUs - fittedIn)
    }

    private fun uniqueSplitId(project: Project, base: String): String {
        var n = 1
        while (true) {
            val id = "$base-split-$n"
            if (project.findClip(id) == null) return id
            n++
        }
    }

    private fun checkedSourceAdd(value: Long, delta: Long): Long = try {
        Math.addExact(value, delta)
    } catch (_: ArithmeticException) {
        throw TimelineEditException(TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS, "source range overflow")
    }

    private fun checkedEnd(clip: Clip): Long {
        if (clip.startUs > Long.MAX_VALUE - clip.durationUs) throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "timeline overflow")
        return clip.startUs + clip.durationUs
    }

    private fun validated(project: Project): Project {
        val issues = TimelineValidator.validate(project)
        if (issues.isEmpty()) return project
        val first = issues.first()
        val code = when (first.code) {
            ProjectIssueCode.SOURCE_RANGE_OUT_OF_BOUNDS -> TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS
            ProjectIssueCode.DUPLICATE_CLIP_ID -> TimelineErrorCode.DUPLICATE_CLIP_ID
            else -> TimelineErrorCode.INVALID_PROJECT
        }
        throw TimelineEditException(code, "invalid project: $first")
    }
}
