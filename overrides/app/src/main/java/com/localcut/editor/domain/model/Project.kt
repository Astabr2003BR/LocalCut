package com.localcut.editor.domain.model

import com.localcut.editor.BuildContract

data class Project(
    val id: String,
    val schemaVersion: Int,
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val assets: List<Asset>,
    val tracks: List<Track>,
    val textOverlays: List<TextOverlay> = emptyList(),
    val transitions: List<ClipTransition> = emptyList(),
    val compositionGraph: CompositionGraph = CompositionGraph(),
) {
    fun findClip(clipId: String): Clip? = tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == clipId }

    companion object {
        fun create(title: String, width: Int, height: Int, fps: Int, id: String = "project-1"): Project = Project(
            id = id,
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = title,
            width = width,
            height = height,
            fps = fps,
            assets = emptyList(),
            tracks = listOf(
                Track(id = "visual-1", type = TrackType.VISUAL, order = 0),
                Track(id = "audio-1", type = TrackType.AUDIO, order = 1),
            ),
        )
    }
}
