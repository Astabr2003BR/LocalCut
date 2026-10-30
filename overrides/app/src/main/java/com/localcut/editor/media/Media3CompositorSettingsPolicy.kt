package com.localcut.editor.media

object Media3CompositorSettingsPolicy {
    fun requiresCustomSettings(sequences: List<Media3SequenceBlueprint>): Boolean =
        Media3SequenceOpacityResolver.requiresCustomAlpha(sequences) ||
            Media3SequenceBlendModeResolver.requiresCustomBlend(sequences)
}
