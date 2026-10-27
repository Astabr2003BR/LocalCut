package com.localcut.editor.bridge.protocol

enum class BridgeCapability(
    val wireName: String,
    val mutatesProject: Boolean = false,
    val requiresRevision: Boolean = mutatesProject,
) {
    GET_DEVICE_STATUS("get_device_status"),
    GET_PROJECT_STATE("get_project_state"),
    GET_TIMELINE_SUMMARY("get_timeline_summary"),
    LIST_TRACKS("list_tracks"),
    LIST_CLIPS("list_clips"),
    GET_SELECTED_CLIP("get_selected_clip"),
    GET_PLAYHEAD("get_playhead"),
    GET_EXPORT_STATUS("get_export_status"),
    GET_ERRORS("get_errors"),
    GET_PREVIEW_FRAME("get_preview_frame"),
    SEEK("seek"),
    SELECT_CLIP("select_clip"),
    SPLIT_CLIP("split_clip", mutatesProject = true),
    TRIM_CLIP("trim_clip", mutatesProject = true),
    MOVE_CLIP("move_clip", mutatesProject = true),
    DELETE_CLIP("delete_clip", mutatesProject = true),
    DUPLICATE_CLIP("duplicate_clip", mutatesProject = true),
    SET_TRANSFORM("set_transform", mutatesProject = true),
    SET_VOLUME("set_volume", mutatesProject = true),
    SET_KEYFRAMES("set_keyframes", mutatesProject = true),
    ADD_TEXT("add_text", mutatesProject = true),
    ADD_TRANSITION("add_transition", mutatesProject = true),
    IMPORT_MEDIA("import_media", mutatesProject = true),
    UNDO("undo", mutatesProject = true),
    REDO("redo", mutatesProject = true),
    EXPORT_PROJECT("export_project", requiresRevision = true),
    CANCEL_EXPORT("cancel_export"),
    ;

    companion object {
        fun fromWireName(value: String): BridgeCapability? = entries.firstOrNull { it.wireName == value }
    }
}

enum class BridgeErrorCode(val wireName: String) {
    DEVICE_OFFLINE("DEVICE_OFFLINE"),
    BRIDGE_DISABLED("BRIDGE_DISABLED"),
    AUTH_REQUIRED("AUTH_REQUIRED"),
    PAIRING_REQUIRED("PAIRING_REQUIRED"),
    PROTOCOL_UNSUPPORTED("PROTOCOL_UNSUPPORTED"),
    CAPABILITY_NOT_ALLOWED("CAPABILITY_NOT_ALLOWED"),
    COMMAND_EXPIRED("COMMAND_EXPIRED"),
    REPLAY_REJECTED("REPLAY_REJECTED"),
    REVISION_CONFLICT("REVISION_CONFLICT"),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND"),
    TRACK_NOT_FOUND("TRACK_NOT_FOUND"),
    CLIP_NOT_FOUND("CLIP_NOT_FOUND"),
    INVALID_ARGUMENT("INVALID_ARGUMENT"),
    INVALID_TIMELINE("INVALID_TIMELINE"),
    OVERLAPPING_CLIPS("OVERLAPPING_CLIPS"),
    ASSET_UNAVAILABLE("ASSET_UNAVAILABLE"),
    IMPORT_PERMISSION_REQUIRED("IMPORT_PERMISSION_REQUIRED"),
    LOCAL_INTERACTION_REQUIRED("LOCAL_INTERACTION_REQUIRED"),
    PREVIEW_FAILED("PREVIEW_FAILED"),
    EXPORT_FAILED("EXPORT_FAILED"),
    EXPORT_BUSY("EXPORT_BUSY"),
    RATE_LIMITED("RATE_LIMITED"),
    INTERNAL_ERROR("INTERNAL_ERROR"),
    ;

    companion object {
        fun fromWireName(value: String): BridgeErrorCode? = entries.firstOrNull { it.wireName == value }
    }
}

data class BridgeCommandEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val kind: String,
    val deviceId: String,
    val projectId: String,
    val expectedRevision: Long?,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val nonce: String,
    val capability: BridgeCapability,
    val payloadJson: String,
)

data class BridgeResultEnvelope(
    val messageId: String,
    val success: Boolean,
    val code: BridgeErrorCode?,
    val projectRevision: Long?,
    val resultJson: String,
    val retryable: Boolean,
)

class BridgeProtocolException(
    val code: BridgeErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
