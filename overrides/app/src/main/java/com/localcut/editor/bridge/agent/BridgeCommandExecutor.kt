package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import com.localcut.editor.domain.history.AddClipTransitionCommand
import com.localcut.editor.domain.history.AddTextOverlayCommand
import com.localcut.editor.domain.history.DeleteClipWithTransitionsCommand
import com.localcut.editor.domain.history.DuplicateClipCommand
import com.localcut.editor.domain.history.MoveClipCommand
import com.localcut.editor.domain.history.SetKeyframesCommand
import com.localcut.editor.domain.history.SetTransformCommand
import com.localcut.editor.domain.history.SetVolumeCommand
import com.localcut.editor.domain.history.SplitClipCommand
import com.localcut.editor.domain.history.TrimClipLeftEdgeCommand
import com.localcut.editor.domain.history.TrimClipRightEdgeCommand
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.TransitionKind
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession
import java.util.Base64

interface BridgePlaybackPort {
    val positionUs: Long
    fun seekToUs(positionUs: Long): Result<Unit>
}

class BridgeCommandExecutor(
    private val session: ProjectSession,
    private val playback: BridgePlaybackPort,
    private val jobs: BridgeJobRegistry = BridgeJobRegistry(),
    private val exportPort: BridgeExportPort = UnavailableBridgeExportPort,
    private val previewFramePort: BridgePreviewFramePort = UnavailableBridgePreviewFramePort,
    private val termuxAgentPort: BridgeTermuxAgentPort = UnavailableBridgeTermuxAgentPort,
) {
    private val reader = BridgeProjectReader(session, playback, jobs)

    @Synchronized
    fun execute(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val state = session.state
        if (command.projectId != state.project.id) {
            return error(command, BridgeErrorCode.PROJECT_NOT_FOUND, "project not found", retryable = false)
        }
        if (command.capability.requiresRevision && command.expectedRevision != state.revision) {
            return error(command, BridgeErrorCode.REVISION_CONFLICT, "project revision changed", retryable = true)
        }

        return try {
            when (command.capability) {
                BridgeCapability.GET_DEVICE_STATUS -> success(command, "{\"state\":\"READY\"}")
                BridgeCapability.GET_PROJECT_STATE -> success(command, reader.projectStateJson())
                BridgeCapability.GET_TIMELINE_SUMMARY -> success(command, reader.timelineSummaryJson())
                BridgeCapability.LIST_TRACKS -> success(command, reader.tracksJson())
                BridgeCapability.LIST_CLIPS -> {
                    val payload = payload(command)
                    success(command, reader.clipsJson(payload.optionalString("trackId")))
                }
                BridgeCapability.GET_SELECTED_CLIP -> success(command, reader.selectedClipJson())
                BridgeCapability.GET_PLAYHEAD -> success(command, reader.playheadJson())
                BridgeCapability.GET_ERRORS -> success(command, reader.errorsJson())
                BridgeCapability.GET_EXPORT_STATUS -> {
                    val payload = payload(command)
                    success(command, reader.exportStatusJson(payload.optionalString("jobId")))
                }
                BridgeCapability.GET_PREVIEW_FRAME -> executePreviewFrame(command)
                BridgeCapability.GET_TERMUX_WORKSPACE_STATUS -> submitTermux(
                    command,
                    BridgeTermuxAgentRequest.GetWorkspaceStatus,
                )
                BridgeCapability.LIST_TERMUX_WORKSPACE_FILES -> executeTermuxListFiles(command)
                BridgeCapability.READ_TERMUX_WORKSPACE_FILE -> executeTermuxReadFile(command)
                BridgeCapability.APPLY_TERMUX_TEXT_PATCH -> executeTermuxApplyPatch(command)
                BridgeCapability.RUN_TERMUX_AGENT_OPERATION -> executeTermuxRunOperation(command)
                BridgeCapability.GET_TERMUX_AGENT_TASK -> executeGetTermuxTask(command)
                BridgeCapability.SEEK -> executeSeek(command)
                BridgeCapability.SELECT_CLIP -> executeSelect(command)
                BridgeCapability.MOVE_CLIP -> executeMove(command)
                BridgeCapability.DELETE_CLIP -> executeDelete(command)
                BridgeCapability.DUPLICATE_CLIP -> executeDuplicate(command)
                BridgeCapability.SPLIT_CLIP -> executeSplit(command)
                BridgeCapability.TRIM_CLIP -> executeTrim(command)
                BridgeCapability.SET_TRANSFORM -> executeTransform(command)
                BridgeCapability.SET_VOLUME -> executeVolume(command)
                BridgeCapability.SET_KEYFRAMES -> executeKeyframes(command)
                BridgeCapability.ADD_TEXT -> executeAddText(command)
                BridgeCapability.ADD_TRANSITION -> executeAddTransition(command)
                BridgeCapability.UNDO -> executeHistory(command, EditorAction.Undo)
                BridgeCapability.REDO -> executeHistory(command, EditorAction.Redo)
                BridgeCapability.IMPORT_MEDIA -> error(
                    command,
                    BridgeErrorCode.LOCAL_INTERACTION_REQUIRED,
                    "media reference is not authorized by LocalCut",
                    retryable = false,
                )
                BridgeCapability.EXPORT_PROJECT -> executeExport(command)
                BridgeCapability.CANCEL_EXPORT -> executeCancelExport(command)
            }
        } catch (error: IllegalArgumentException) {
            error(command, BridgeErrorCode.INVALID_ARGUMENT, error.message ?: "invalid argument", retryable = false)
        } catch (error: Throwable) {
            error(command, BridgeErrorCode.INTERNAL_ERROR, error.message ?: "internal error", retryable = false)
        }
    }

    private fun executeTermuxListFiles(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val path = payload.optionalString("path") ?: ""
        val limit = payload.optionalLong("limit") ?: 200L
        require(limit in 1L..500L) { "limit must be in 1..500" }
        return submitTermux(
            command,
            BridgeTermuxAgentRequest.ListWorkspaceFiles(path = path, limit = limit.toInt()),
        )
    }

    private fun executeTermuxReadFile(command: BridgeCommandEnvelope): BrideResultEnvelope {
        val path = payload(command).requiredString("path")
        return submitTermux(command, BridgeTermuxAgentRequest.ReadWorkspaceFile(path))
    }

    private fun executeTermuxApplyPatch(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        return submitTermux(
            command,
            BrideTermuxAgentRequest.ApplyTextPatch(
                path = payload.requiredString("path"),
                expectedSha256 = payload.requiredString("expectedSha256"),
                oldText = payload.requiredString("oldText"),
                newText = payload.required("newText").asString(),
            ),
        )
    }

    private fun executeTermuxRunOperation(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val operation = payload(command).requiredString("operation")
        return submitTermux(command, BridgeTermuxAgentRequest.RunAgentOperation(operation))
    }

    private fun executeGetTermuxTask(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val taskId = payload(command).requiredString("taskId")
        val task = termuxAgentPort.get(taskId)
            ?: return error(command, BridgeErrorCode.INVALID_ARGUMENT, "termux task not found", false)
        return success(command, termuxTaskJson(task))
    }

    private fun submitTermux(
        command: BridgeCommandEnvelope,
        request: BridgeTermuxAgentRequest,
     ): BridgeResultEnvelope {
        val task = termuxAgentPort.submit(request)
        return success(command, termuxTaskJson(task))
    }

    private fun executePreviewFrame(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val frameResult = previewFramePort.capture()
        if (frameResult.isFailure) {
            return error(
                command,
                BridgeErrorCode.PREVIEW_FAILED,
                frameResult.exceptionOrNull()?.message ?: "preview capture failed",
                retryable = true,
            )
        }
        val frame = frameResult.getOrThrow()
        val encoded = Base64.getEncoder().encodeToString(frame.bytes)
        val resultJson = buildString {
            append("{\"mimeType\":")
            append(jsonString(frame.mimeType))
            append(",\"base64\":")
            append(jsonString(encoded))
            append(",\"width\":")
            append(frame.width)
            append(",\"height\":")
            append(frame.height)
            append(",\"positionUs\":")
            append(frame.positionUs)
            append('}')
        }
        return success(command, resultJson)
    }

    private fun executeSeek(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val positionUs = payload(command).requiredLong("positionUs")
        require(positionUs >= 0L) { "positionUs must be non-negative" }
        val result = playback.seekToUs(positionUs)
        if (result.isFailure) {
            return error(command, BridgeErrorCode.INVALID_ARGUMENT, result.exceptionOrNull()?.message ?: "seek failed", false)
        }
        return success(command, "{\"positionUs\":$positionUs}")
    }

    private fun executeSelect(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val clipId = payload(command).optionalString("plipId")
        if (clipId != null && session.state.project.findClip(clipId) == null) {
            return error(command, BridgeErrorCode.CLIP_NOT_FOUND, "clip not found", false)
        }
        session.dispatch(EditorAction.SelectClip(clipId))
        return success(command, "{\"selectedClipId\":${clipId?.let(::ZњЫЫ”Эљ[™КHО€›ќ[џ_HЉB€B‚€љ]]Hќ[€^XЭ]S[Э™JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[™]ФЭ\ќ\ИH^[ШYњ™\]Z\™YЫ™К›™]ФЭ\ќ\ИЉB€™\]Z\™J™]ФЭ\ќ\ИЏH
HИ›™]ФЭ\ќ\И]\Э™H›Ы‹[™YШ]]™H€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]J[Э™PЫ\ЫЫ[X[™
Ы\Y™]ФЭ\ќ\КJJB€B‚€љ]]Hќ[€^XЭ]Q[]JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[Ы\YH^[ШY
ЫЫ[X[™
Kњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]J[]PЫ\Ъ][њЪ][ЫњРЫЫ[X[™
Ы\Y
JJB€B‚€љ]]Hќ[€^XЭ]Q\XШ]JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[\XШ]RYH^[ШY›Ь[Ы[Эљ[™К™\XШ]PЫ\YЉHО€[ШШ]Q\XШ]RY
Ы\Y
B€™\]Z\™JЩ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ™љ[™Ы\
\XШ]RY
HOHќ[
HИ™\XШ]PЫ\Y[™XYH^\ЭИ€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]J\XШ]PЫ\ЫЫ[X[™
Ы\Y\XШ]RY
JJB€B‚€љ]]Hќ[€^XЭ]TЬ]
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[Ы\HЩ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ™љ[™Ы\
Ы\Y
HHB€[ЬЪ][Ы•\ИH^[ШYњ™\]Z\™YЫ™КњЬЪ][Ы•\ИЉB€™\]Z\™JЬЪ][Ы•\И€Ы\њЭ\ќ\И	‰€ЬЪ][Ы•\ИЫ\њЭ\ќ\И
ИЫ\™\][Ы•\К^ИњЬ]ЬЪ][Ы€]\Э™H[њЪYHЫ\€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JЬ]Ы\ЫЫ[X[™
Ы\YЬЪ][Ы•\КJJB€B‚€љ]]Hќ[€^XЭ]Uљ[JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[™]ФЭ\ќ\ИH^[ШY›Ь[Ы[Ы™К›™]ФЭ\ќ\ИЉB€[™]С[™\ИH^[ШY›Ь[Ы[Ы™К›™]С[™\ИЉB€™\]Z\™J
™]ФЭ\ќ\ИOHќ[
HOH
™]С[™\ИOHќ[
JHИњ›ЭљYH^XЭHЫ™HЩ€™]ФЭ\ќ\ЛЫ™]С[™\И€B€[XЭ[Ы€HY€
™]ФЭ\ќ\ИOHќ[
HВ€™\]Z\™J™]ФЭ\ќ\ИЏH
HИ›™]ФЭ\ќ\И]\Э™H›Ы‹[™YШ]]™H€B€Y]ЬђXЭ[Ы‹‘^XЭ]Jљ[PЫ\YќYЩPЫЫ[X[™
Ы\Y™]ФЭ\ќ\КJB€H[ЩHВ€™\]Z\™J™]С[™\ИHHЏH
HИ›™]С[™\И]\Э™H›Ы‹[™YШ]]™H€B€Y]ЬђXЭ[Ы‹‘^XЭ]Jљ[PЫ\љYЪYЩPЫЫ[X[™
Ы\Y™]С[™\КJB€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™XЭ[ЫЉB€B‚€љ]]Hќ[€^XЭ]U[њЩ›Ь›JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[Э\њ™[ќHЩ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ™љ[™Ы\
Ы\Y
HHKќ[њЩ›Ь›B€[[њЩ›Ь›HH[њЩ›Ь›U[YJ€H^[ШY›Ь[Ы[ЭX›JћЉHО€Э\њ™[ќћ€HH^[ШY›Ь[Ы[ЭX›JћHЉHО€Э\њ™[ќћK€ШШ[HH^[ШY›Ь[Ы[ЭX›JњШШ[HЉHО€Э\њ™[ќњШШ[K€›Э][Ы‘YИH^[ШY›Ь[Ы[ЭX›Jњ›Э][Ы‘YИЉHО€Э\њ™[ќњ›Э][Ы‘YЛ€ЬXЪ]HH^[ШY›Ь[Ы[ЭX›J›ЬXЪ]HЉHО€Э\њ™[ќ›ЬXЪ]K€
B€™\]Z\™J[њЩ›Ь›KњШШ[H€Њ
^ИњШШ[H]\Э™HЬЪ]]™H€B€™\]Z\™J[њЩ›Ь›K›ЬXЪ]H[€Њ‹ЊKЊ
HИ›ЬXЪ]H]\Э™H[€‹ЊH€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JЩ][њЩ›Ь›PЫЫ[X[™
Ы\Y[њЩ›Ь›JJJB€B‚€љ]]Hќ[€^XЭ]U›Ы[YJЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[›Ы[YHH^[ШYњ™\]Z\™YЭX›Jќ›Ы[YHЉB€™\]Z\™J›Ы[YHЏHЊ
HИќ›Ы[YH]\Э™H›Ы‹[™YШ]]™H€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JЩ]›Ы[YPЫЫ[X[™
Ы\Y›Ы[YJJJB€B‚€љ]]Hќ[€^XЭ]RЩ^Yњ[Y\КЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ы\YH^[ШYњ™\]Z\™YЭљ[™КЫ\YЉB€™\]Z\™PЫ\
ЫЫ[X[™Ы\Y
OЛ›]И™]\›€]B€[Ы\HЩ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ™љ[™Ы\
Ы\Y
HHB€[Щ^Yњ[Y\ИH^[ШY
ЫЫ[X[™
Kњ™\]Z\™Y
љЩ^Yњ[Y\ИЉK\Р\њ^J
Kќ[Y\Л›X\И]ИO‚€[][HH]Л\УШљ™XЭ

B€[Щ™њЩ]\ИH][Kњ™\]Z\™YЫ™К›Щ™њЩ]\ИЉB€™\]Z\™JЩ™њЩ]\И[€‹Ы\™\][Ы•\КHИљЩ^Yњ[YHЩ™њЩ]]\Э™H[њЪYHЫ\€B€[њЩ›Ь›RЩ^Yњ[YJ€Щ™њЩ]\ИHЩ™њЩ]\Л€[YHH[њЩ›Ь›U[YJ€H][Kњ™\]Z\™YЭX›JћЉK€HH][Kњ™\]Z\™YЭX›JћHЉK€ШШ[HH][Kњ™\]Z\™YЭX›JњШШ[HЉK€›Э][Ы‘YИH][Kњ™\]Z\™YЭX›Jњ›Э][Ы‘YИЉK€ЬXЪ]HH][Kњ™\]Z\™YЭX›J›ЬXЪ]HЉK€
K€
B€B€™\]Z\™JЩ^Yњ[Y\Л›X\И]›Щ™њЩ]\ИK™\Э[Э

KњЪ^™HOHЩ^Yњ[Y\ЛњЪ^™JHИ™\XШ]HЩ^Yњ[YHЩ™њЩ]€B€™\]Z\™JЩ^Yњ[Y\Л[И]ќ[YKњШШ[H€ЊJHИљЩ^Yњ[YHШШ[H]\Э™HЬЪ]]™H€B€™\]Z\™JЩ^Yњ[Y\Л[И]ќ[YK›ЬXЪ]H[€Њ‹ЊKЊJHИљЩ^Yњ[YHЬXЪ]H]\Э™H[€‹ЊH€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JЩ]Щ^Yњ[Y\РЫЫ[X[™
Ы\YЩ^Yњ[Y\ЛњЫЬќYћHИ]›Щ™њЩ]\ИJJJB€B‚€љ]]Hќ[€^XЭ]PY^
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[YH^[ШYњ™\]Z\™YЭљ[™КљYЉB€[Э\ќ\ИH^[ШYњ™\]Z\™YЫ™КњЭ\ќ\ИЉB€[\][Ы•\ИH^[ШYњ™\]Z\™YЫ™К™\][Ы•\ИЉB€[^H^[ШYњ™\]Z\™YЭљ[™Кќ^ЉB€™\]Z\™JЭ\ќ\ИЏH
HИњЭ\ќ\И]\Э™H›Ы‹[™YШ]]™H€B€™\]Z\™J\][Ы•\И€
HИ™\][Ы•\И]\Э™HЬЪ]]™H€B€[›ЫќЪ^™TH^[ШY›Ь[Ы[ЭX›J™›ЫќЪ^™TЉHО€ЌЊ€™\]Z\™J›ЫќЪ^™T€Њ
HИ™›ЫќЪ^™T]\Э™HЬЪ]]™H€B€[љ[\™Ш“Ы™ИH^[ШY›Ь[Ы[Ы™К™љ[\™Ш€ЉHО€‘‘‘‘‘‘‘“€™\]Z\™Jљ[\™Ш“Ы™И[€[ќ“RS—ХђSQKќУЫ™К
K‹Њ‘‘‘‘‘‘‘“
HИ™љ[\™Ш€Э]Щ€[™ЩH€B€[’[™^Ы™ИH^[ШY›Ь[Ы[Ы™Кћ’[™^ЉHО€€™\]Z\™J’[™^Ы™И[€[ќ“RS—ХђSQKќУЫ™К
K‹’[ќ“PVХђSQKќУЫ™К
JHИћ’[™^Э]Щ€[™ЩH€B€[Э™\›^SЬXЪ]HH^[ШY›Ь[Ы[ЭX›J›ЬXЪ]HЉHО€KЊ€™\]Z\™JЭ™\›^SЬXЪ]H[€Њ‹ЊKЊ
HИ›ЬXЪ]H]\Э™H[€‹ЊH€B€[[њЩ›Ь›HH[њЩ›Ь›U[YJ€H^[ШY›Ь[Ы[ЭX›JћЉHО€Њ€HH^[ШY›Ь[Ы[ЭX›JћHЉHО€Њ€ШШ[HH^[ШY›Ь[Ы[ЭX›JњШШ[HЉHО€KЊ€›Э][Ы‘YИH^[ШY›Ь[Ы[ЭX›Jњ›Э][Ы‘YИЉHО€Њ€ЬXЪ]HHKЊ€
B€™\]Z\™J[њЩ›Ь›KњШШ[H€Њ
HИњШШ[H]\Э™HЬЪ]]™H€B€[Э™\›^HH^Э™\›^J€YHY€Э\ќ\ИHЭ\ќ\Л€\][Ы•\ИH\][Ы•\Л€^H^€Э[HH^Э[J€›ЫќЪ^™TH›ЫќЪ^™T€љ[\™Ш€Hљ[\™Ш“Ы™ЛќТ[ќ

K€
K€[њЩ›Ь›HH[њЩ›Ь›K€ЬXЪ]HHЭ™\›^SЬXЪ]K€’[™^H’[™^Ы™ЛќТ[ќ

K€
B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JY^Э™\›^PЫЫ[X[™
Э™\›^JJJB€B‚€љ]]Hќ[€^XЭ]PY[њЪ][ЫЉЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[^[ШYH^[ШY
ЫЫ[X[™
B€[Ъ[™[YHH^[ШYњ™\]Z\™YЭљ[™КљЪ[™ЉB€[Ъ[™Hќ[ђШ]Ъ[™ИИ[њЪ][Ы’Ъ[™ќ[YSЩЉЪ[™[YJHB€™Щ]Ь‘[ЩHИ›ЭИ[YШ[\™Э[Y[ќ^Щ\[ЫЉќ[њЭ\ЬќY[њЪ][Ы€Ъ[™ЉHB€Y€
Ъ[™OH[њЪ][Ы’Ъ[™‘ђQWХЧР“PТКHВ€™]\›€\њ›ЬЉ€ЫЫ[X[™€њљYЩQ\њ›ЬђЫЩKђРTP’SUWУ“ХРSХСQ€ќ[њЪ][Ы€Ъ[™\И›Э™[™\X›H[€њљYЩHЊH‹€™]ћXX›HH[ЩK€
B€B€[[њЪ][Ы€HЫ\[њЪ][ЫЉ€YH^[ШYњ™\]Z\™YЭљ[™КљYЉK€њ›ЫPЫ\YH^[ШYњ™\]Z\™YЭљ[™К™њ›ЫPЫ\YЉK€РЫ\YH^[ШYњ™\]Z\™YЭљ[™КќРЫ\YЉK€\][Ы•\ИH^[ШYњ™\]Z\™YЫ™К™\][Ы•\ИЉK€Ъ[™HЪ[™€
B€™\]Z\™J[њЪ][Ы‹™\][Ы•\И€
HИ™\][Ы•\И]\Э™HЬЪ]]™H€B€™]\›€\Ь]ЪЬљ]JЫЫ[X[™Y]ЬђXЭ[Ы‹‘^XЭ]JYЫ\[њЪ][ЫђЫЫ[X[™
[њЪ][ЫЉJJB€B‚€љ]]Hќ[€^XЭ]R\ЭЬћJЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬKXЭ[ЫЋ€Y]ЬђXЭ[ЫЉN€њљYЩT™\Э[[ќ™[ЬHВ€[™Y›Ь™HHЩ\ЬЪ[Ы‹њЭ]Kњ™]љ\Ъ[Ы‚€Щ\ЬЪ[Ы‹™\Ь]Ъ
XЭ[ЫЉB€™]\›€Y€
Щ\ЬЪ[Ы‹њЭ]Kњ™]љ\Ъ[Ы€OH™Y›Ь™JHВ€ЭXШЩ\ЬКЫЫ[X[™ћЧЪ[™ЩYЋ™[Щ_HЉB€H[ЩHВ€ЭXШЩ\ЬКЫЫ[X[™ћЧЪ[™ЩYЋќќY_HЉB€B€B‚€љ]]Hќ[€^XЭ]Q^Ьќ
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHHЪ[€
[™\Щ\ќ][Ы€H›ШњЛњ™\Щ\ќ™Q^Ьќ
ЫЫ[X[™›Y\ЬШYЩRY
JHВ€\ИњљYЩR›Ш”™\Щ\ќ][Ы‹‘^\Э[™ИO€ЭXШЩ\ЬКЫЫ[X[™›Ш’њЫЫЉ™\Щ\ќ][Ы‹љ›ШЉJB€\ИњљYЩR›Ш”™\Щ\ќ][Ы‹ђќ\ЮHO€\њ›ЬЉ€ЫЫ[X[™€њљYЩQ\њ›ЬђЫЩK‘VФ•Р•TЦK€[›Э\€^Ьќ\ИXЭ]™H‹€™]ћXX›HHќYK€]Z[ТњЫЫ€H›Ш’њЫЫЉ™\Щ\ќ][Ы‹XЭ]™R›ШЉK€
B€\ИњљYЩR›Ш”™\Щ\ќ][Ы‹”Э\ќYO€В€[›Ш’YH™\Щ\ќ][Ы‹љ›Ш‹љ›Ш’Y€[\Э[™\€HШљ™XЭ€њљYЩQ^Ьќ\Э[™\€В€Э™\њљYHќ[€Ы”Э\ќY

HВ€›ШњЛ›X\љФќ[›љ[™К›Ш’Y
B€B€Э™\њљYHќ[€Ы”›ЩЬ™\ЬК\Щ[ќ€[ќ
HВ€›ШњЛќ\]T›ЩЬ™\ЬК›Ш’Y\Щ[ќЫЩ\ЩR[ЉL
JB€B€Э™\њљYHќ[€ЫђЫЫ\]Y
™\Э[њЫЫЋ€Эљ[™КHВ€›ШњЛњЭXШЩYY
›Ш’Y™\Э[њЫЫЉB€B‚€Э™\њљYHќ[€Ы‘Z[Y
Y\ЬШYЩN€Эљ[™КHВ€›ШњЛ™Z[
›Ш’YњљYЩQ\њ›ЬђЫЩK‘VФ•СђRSQШ[љ]^™JY\ЬШYЩJJB€B‚€Э™\њљYHќ[€ЫђШ[Щ[Y

HВ€›ШњЛШ[Щ[
›Ш’Y
B€B€B€[Э\ќYH^ЬќЬќњЭ\ќ
Щ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ\Э[™\ЉB€Y€
Э\ќYљ\СZ[\™JHВ€[Y\ЬШYЩHHЭ\ќY™^Щ\[Ы“Ь“ќ[

OЛ›Y\ЬШYЩHО€™^ЬќЫЭ[›ЭЭ\ќ‚€›ШњЛ™Z[
›Ш’YњљYЩQ\њ›ЬђЫЩK‘VФ•СђRSQШ[љ]^™JY\ЬШYЩJJB€\њ›ЬЉЫЫ[X[™њљYЩQ\њ›ЬђЫЩK‘VФ•СђRSQY\ЬШYЩK™]ћXX›HHќYJB€H[ЩHВ€ЭXШЩ\ЬКЫЫ[X[™›Ш’њЫЫЉ™\]Z\™S›Эќ[
›ШњЛ™Щ]
›Ш’Y
JJJB€B€B€B‚€љ]]Hќ[€^XЭ]PШ[Щ[^Ьќ
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€њљYЩT™\Э[[ќ™[ЬHВ€[›Ш’YH^[ШY
ЫЫ[X[™
Kњ™\]Z\™YЭљ[™Кљ›Ш’YЉB€[›Ш€H›ШњЛ™Щ]
›Ш’Y
HО€™]\›€\њ›ЬЉЫЫ[X[™њљYЩQ\њ›ЬђЫЩK’S•ђSQРT‘ХSQS•љ›Ш€›Э›Э[™‹[ЩJB€[Ш[Щ[YH^ЬќЬќШ[Щ[

B€Y€
Ш[Щ[Yљ\СZ[\™JHВ€™]\›€\њ›ЬЉ€ЫЫ[X[™€њљYЩQ\њ›ЬђЫЩK‘VФ•СђRSQ€Ш[Щ[Y™^Щ\[Ы“Ь“ќ[

OЛ›Y\ЬШYЩHО€™^ЬќШ[Щ[][Ы€Z[Y‹€™]ћXX›HHќYK€
B€B€›ШњЛШ[Щ[
›Ш’Y
B€™]\›€ЭXШЩ\ЬКЫЫ[X[™›Ш’њЫЫЉ™\]Z\™S›Эќ[
›ШњЛ™Щ]
›Ш‹љ›Ш’Y
JJJB€B‚€љ]]Hќ[€\Ь]ЪЬљ]JЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬKXЭ[ЫЋ€Y]ЬђXЭ[ЫЉN€њљYЩT™\Э[[ќ™[ЬHВ€[™Y›Ь™T™]љ\Ъ[Ы€HЩ\ЬЪ[Ы‹њЭ]Kњ™]љ\Ъ[Ы‚€Щ\ЬЪ[Ы‹™\Ь]Ъ
XЭ[ЫЉB€[Yќ\€HЩ\ЬЪ[Ы‹њЭ]B€Y€
Yќ\‹њ™]љ\Ъ[Ы€OH™Y›Ь™T™]љ\Ъ[ЫЉHВ€[Y\ЬШYЩHHYќ\‹›\Э\њ›Ь€О€™Y]Ш\И›Э\YY‚€™]\›€\њ›ЬЉЫЫ[X[™X\ЫXZ[‘\њ›ЬЉY\ЬШYЩJKY\ЬШYЩK[ЩJB€B€™]\›€ЭXШЩ\ЬКЫЫ[X[™ћЧ\YYЋќќY_HЉB€B‚€љ]]Hќ[€™\]Z\™PЫ\
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬKЫ\Y€Эљ[™КN€њљYЩT™\Э[[ќ™[ЬOИВ€[›Ъ™XЭHЩ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ€Y€
›Ъ™XЭ™љ[™Ы\
Ы\Y
HOHќ[
HВ€™]\›€\њ›ЬЉЫЫ[X[™њљYЩQ\њ›ЬђЫЩKђУTУ“ХС“ХS‘Ы\›Э›Э[™‹[ЩJB€B€[ШЪЩYH›Ъ™XЭЫЫ\ЬЪ][Ы‘Ь\››Щ\Л[ћHИ›ЩHO‚€›ЩKљЪ[™OHЫЫ\ЬЪ][Ы“›ЩRЪ[™“VQT€	‰€›ЩKЫ\YOHЫ\Y	‰€›ЩK›ШЪЩY€B€™]\›€Y€
ШЪЩY
HВ€\њ›ЬЉЫЫ[X[™њљYЩQ\њ›ЬђЫЩKђРTP’SUWУ“ХРSХСQЫ\\ИШЪЩY‹[ЩJB€H[ЩHВ€ќ[€B€B‚€љ]]Hќ[€[ШШ]Q\XШ]RY
Ы\Y€Эљ[™КN€Эљ[™ИВ€\€ЭY™љ^HB€Ъ[H
ќYJHВ€[Ш[™Y]HHY€
ЭY™љ^OHJH‰Ы\YXЫЬH€[ЩH‰Ы\YXЫЬKIЭY™љ^‚€Y€
Щ\ЬЪ[Ы‹њЭ]Kњ›Ъ™XЭ™љ[™Ы\
Ш[™Y]JHOHќ[
H™]\›€Ш[™Y]B€ЭY™љ^
КВ€B€B‚€љ]]Hќ[€ЭXШЩ\ЬКЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬK™\Э[њЫЫЋ€Эљ[™КN€њљYЩT™\Э[[ќ™[ЬHHњљYЩT™\Э[[ќ™[ЬJ€Y\ЬШYЩRYHЫЫ[X[™›Y\ЬШYЩRY€ЭXШЩ\ЬИHќYK€ЫЩHHќ[€›Ъ™XЭ™]љ\Ъ[Ы€HЩ\ЬЪ[Ы‹њЭ]Kњ™]љ\Ъ[Ы‹€™\Э[њЫЫ€H™\Э[њЫЫ‹€™]ћXX›HH[ЩK€
B‚€љ]]Hќ[€\њ›ЬЉ€ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬK€ЫЩN€њљYЩQ\њ›ЬђЫЩK€Y\ЬШYЩN€Эљ[™Л€™]ћXX›N€›ЫЫX[‹€]Z[ТњЫЫЋ€Эљ[™ИHћЯH‹€
N€њљYЩT™\Э[[ќ™[ЬHHњљYЩT™\Э[[ќ™[ЬJ€Y\ЬШYЩRYHЫЫ[X[™›Y\ЬШYЩRY€ЭXШЩ\ЬИH[ЩK€ЫЩHHЫЩK€›Ъ™XЭ™]љ\Ъ[Ы€HЩ\ЬЪ[Ы‹њЭ]Kњ™]љ\Ъ[Ы‹€™\Э[њЫЫ€HћЧ›Y\ЬШYЩWЋ‰ЪњЫЫ”Эљ[™КШ[љ]^™JY\ЬШYЩJJ_K™]Z[ЧЋ‰]Z[ТњЫЫџH‹€™]ћXX›HH™]ћXX›K€
B‚€љ]]Hќ[€X\ЫXZ[‘\њ›ЬЉY\ЬШYЩN€Эљ[™КN€њљYЩQ\њ›ЬђЫЩHВ€[ЭЩ\€HY\ЬШYЩK›ЭЩ\Ш\ЩJ
B€™]\›€Ъ[€В€›Э™\›\€[€ЭЩ\€O€њљYЩQ\њ›ЬђЫЩK“Х‘T“TS‘ЧРУTВ€Ы\€[€ЭЩ\€	‰€››Э›Э[™€[€ЭЩ\€O€њљYЩQ\њ›ЬђЫЩKђУTУ“ХС“ХS‘€ќXЪИ€[€ЭЩ\€	‰€››Э›Э[™€[€ЭЩ\€O€њљYЩQ\њ›ЬђЫЩK•ђPТЧУ“ХС“ХS‘€\ЬЩ]€[€ЭЩ\€	‰€ќ[]Z[X›H€[€ЭЩ\€O€њљYЩQ\њ›ЬђЫЩKђTФСUХSђUђRSP“B€[ЩHO€њљYЩQ\њ›ЬђЫЩK’S•ђSQРT‘ХSQS•€B€B‚€љ]]Hќ[€^[ШY
ЫЫ[X[™€њљYЩPЫЫ[X[™[ќ™[ЬJN€“Шљ™XЭHћHВ€Z[љRњЫЫ‹њ\њЩJЫЫ[X[™њ^[ШYњЫЫЉK\УШљ™XЭ

B€HШ]Ъ
\њ›ЬЋ€›ЭШX›JHВ€›ЭИ[YШ[\™Э[Y[ќ^Щ\[ЫЉњ^[ШY]\Э™HH”УУ€Шљ™XЭ‹\њ›ЬЉB€B‚€љ]]Hќ[€“Шљ™XЭњ™\]Z\™YЭљ[™КЩ^N€Эљ[™КN€Эљ[™ИH™\]Z\™Y
Щ^JK\ФЭљ[™К
K[ЫИВ€™\]Z\™J]љ\У›Э›[љК
JHИ‰Щ^H]\Э›Э™H›[љИ€B€B‚€љ]]Hќ[€“Шљ™XЭ›Ь[Ы[Эљ[™КЩ^N€Эљ[™КN€Эљ[™ПИHЪ[€
[[YHHЬ[Ы[
Щ^JJHВ€ќ[“ќ[O€ќ[€[ЩHO€[YK\ФЭљ[™К
B€B‚€љ]]Hќ[€“Шљ™XЭњ™\]Z\™YЫ™КЩ^N€Эљ[™КN€Ы™ИH™\]Z\™Y
Щ^JK\УЫ™К
B€љ]]Hќ[€“Шљ™XЭ›Ь[Ы[Ы™КЩ^N€Эљ[™КN€Ы™ПИHЪ[€
[[YHHЬ[Ы[
Щ^JJHВ€ќ[“ќ[O€ќ[€[ЩHO€[YK\УЫ™К
B€B€љ]]Hќ[€“Шљ™XЭњ™\]Z\™YЭX›JЩ^N€Эљ[™КN€ЭX›HH™\]Z\™Y
Щ^JK\СЭX›J
B€љ]]Hќ[€“Шљ™XЭ›Ь[Ы[ЭX›JЩ^N€Эљ[™КN€ЭX›OИHЪ[€
[[YHHЬ[Ы[
Щ^JJHВ€ќ[“ќ[O€ќ[€[ЩHO€[YK\СЭX›J
B€B‚€љ]]Hќ[€›Ш’њЫЫЉ›ШЋ€њљYЩR›Ш”Ы\ЪЭ
N€Эљ[™ИH€€ћИљ›Ш’YЋ‰ЪњЫЫ”Эљ[™К›Ш‹љ›Ш’Y
_KњЭ]HЋ‰ЪњЫЫ”Эљ[™К›Ш‹њЭ]K›[YJ_Kњ›ЩЬ™\ЬФ\Щ[ќЋ‰Ъ›Ш‹њ›ЩЬ™\ЬФ\Щ[ќО€›ќ[џ_H€€‚‚€љ]]Hќ[€\›]^\ЪТњЫЫЉ\ЪО€њљYЩU\›]^YЩ[ќ\ЪФЫ\ЪЭ
N€Эљ[™ИHќZ[Эљ[™ИВ€\[™
	ЮЙКB€\[™
—ќ\ЪТYЋ€ЉNИ\[™
њЫЫ”Эљ[™К\ЪЛќ\ЪТY
JB€\[™
‹њЭ]WЋ€ЉNИ\[™
њЫЫ”Эљ[™К\ЪЛњЭ]K›[YJJB€\[™
‹›Ь\][Ы—Ћ€ЉNИ\[™
њЫЫ”Эљ[™К\ЪЛ›Ь\][ЫЉJB€\[™
‹њ™\Э[Ћ€ЉNИ\[™
\ЪЛњ™\Э[њЫЫ€О€›ќ[ЉB€\[™
‹™\њ›ЬђЫЩWЋ€ЉNИ\[™
\ЪЛ™\њ›ЬђЫЩOЛ›]
ЋљњЫЫ”Эљ[™КHО€›ќ[ЉB€\[™
	ЯIКB€B‚€љ]]Hќ[€Ш[љ]^™JY\ЬШYЩN€Эљ[™КN€Эљ[™ИHY\ЬШYЩB€њ™\XЩJ™YЩ^
ЉО™љ[_ЫЫќ[ќ
N‹ЛЧКИЉK–Ь™YXЭY]\љWHЉB€ќZЩJL
BџB