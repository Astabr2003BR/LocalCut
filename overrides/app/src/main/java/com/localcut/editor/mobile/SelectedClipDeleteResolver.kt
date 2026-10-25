package com.localcut.editor.mobile

import com.localcut.editor.data.project.jsonString

object SelectedClipDeleteResolver {
    fun resolvePayload(command: MobileHandoffCommand, selectedClipId: String?): Result<String> = runCatching {
        if (!command.resolveSelectedClip) return@runCatching command.payloadJson
        val clipId = selectedClipId?.takeIf { it.isNotBlank() } ?: error("CLIP_NOT_FOUND")
        val trimmed = command.payloadJson.trim()
        require(trimmed.startsWith('{') && trimmed.endsWith('}')) { "HANDOFF_PAYLOAD_INVALID" }
        val fields = trimmed.substring(1, trimmed.length - 1).trim()
        if (fields.isEmpty() || fields == "\"selectedClip\":true") {
            "{\"clipId\":${jsonString(clipId)}}"
        } else {
            "{\"clipId\":${jsonString(clipId)},$fields}"
        }
    }
}
