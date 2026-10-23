package com.localcut.editor.mobile

import com.localcut.editor.data.project.jsonString

object SelectedClipDeleteResolver {
    fun resolvePayload(command: MobileHandoffCommand, selectedClipId: String?): Result<String> = runCatching {
        if (!command.resolveSelectedClip) return@runCatching command.payloadJson
        val clipId = selectedClipId?.takeIf { it.isNotBlank() } ?: error("CLIP_NOT_FOUND")
        "{\"clipId\":${jsonString(clipId)}}"
    }
}
