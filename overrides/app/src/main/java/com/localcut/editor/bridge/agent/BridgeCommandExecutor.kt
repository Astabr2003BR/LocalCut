package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.JValue
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import com.localcut.editor.domain.history.DeleteClipWithTransitionsCommand
import com.localcut.editor.domain.history.DuplicateClipCommand
import com.localcut.editor.domain.history.MoveClipCommand
import com.localcut.editor.domain.history.SetTransformCommand
import com.localcut.editor.domain.history.SetVolumeCommand
import com.localcut.editor.domain.history.SplitClipCommand
import com.localcut.editor.domain.history.TrimClipLeftEdgeCommand
import com.localcut.editor.domain.history.TrimClipRightEdgeCommand
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

interface BridgePlaybackPort {
    val positionUs: Long
    fun seekToUs(positionUs: Long): Result<Unit>
}

class BridgeCommandExecutor(
    private val session: ProjectSession,
    private val playback: BridgePlaybackPort,
    private val jobs: BridgeJobRegistry = BridgeJobRegistry(),
    private val exportPort: BridgeExportPort = UnavailableBridgeExportPort,
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
                BridgeCapability.SEEK -> executeSeek(command)
                BridgeCapability.SELECT_CLIP -> executeSelect(command)
                BridgeCapability.MOVE_CLIP -> executeMove(command)
                BridgeCapability.DELETE_CLIP -> executeDelete(command)
                BridgeCapability.DUPLICATE_CLIP -> executeDuplicate(command)
                BridgeCapability.SPLIT_CLIP -> executeSplit(command)
                BridgeCapability.TRIM_CLIP -> executeTrim(command)
                BridgeCapability.SET_TRANSFORM -> executeTransform(command)
                BridgeCapability.SET_VOLUME -> executeVolume(command)
                BridgeCapability.UNDO -> executeHistory(command, EditorAction.Undo)
                BridgeCapability.REDO -> executeHistory(command, EditorAction.Redo)
                BridgeCapability.IMPORT_MEDIA -> error(
                    command,
                    BridgeErrorCode.LOCAL_INTERACTION_REQUIRED,
                    "media reference is not authorized by LocalCut",
                    retryable = false,
                )
                BridgeCapability.GET_PREVIEW_FRAME -> error(
                    command,
                    BridgeErrorCode.PREVIEW_FAILED,
                    "preview frame port not attached",
                    retryable = true,
                )
                BridgeCapability.EXPORT_PROJECT -> executeExport(command)
                BridgeCapability.CANCEL_EXPORT -> executeCancelExport(command)
                BridgeCapability.SET_KEYFRAMES,
                BridgeCapability.ADD_TEXT,
                BridgeCapability.ADD_TRANSITION -> error(
                    command,
                    BridgeErrorCode.CAPABILITY_NOT_ALLOWED,
                    "capability not wired into Bridge V1 command executor yet",
                    retryable = false,
                )
            }
        } catch (error: IllegalArgumentException) {
            error(command, BridgeErrorCode.INVALID_ARGUMENT, error.message ?: "invalid argument", retryable = false)
        } catch (error: Throwable) {
            error(command, BridgeErrorCode.INTERNAL_ERROR, error.message ?: "internal error", retryable = false)
        }
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
        val clipId = payload(command).optionalString("clipId")
        if (clipId != null && session.state.project.findClip(clipId) == null) {
            return error(command, BridgeErrorCode.CLIP_NOT_FOUND, "clip not found", false)
        }
        session.dispatch(EditorAction.SelectClip(clipId))
        return success(command, "{\"selectedClipId\":${clipId?.let(::jsonString) ?: "null"}}")
    }

    private fun executeMove(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        val newStartUs = payload.requiredLong("newStartUs")
        require(newStartUs >= 0L) { "newStartUs must be non-negative" }
        return dispatchWrite(command, EditorAction.Execute(MoveClipCommand(clipId, newStartUs)))
    }

    private fun executeDelete(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val clipId = payload(command).requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        return dispatchWrite(command, EditorAction.Execute(DeleteClipWithTransitionsCommand(clipId)))
    }

    private fun executeDuplicate(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        val duplicateId = payload.optionalString("duplicateClipId") ?: allocateDuplicateId(clipId)
        require(session.state.project.findClip(duplicateId) == null) { "duplicateClipId already exists" }
        return dispatchWrite(command, EditorAction.Execute(DuplicateClipCommand(clipId, duplicateId)))
    }

    private fun executeSplit(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        val clip = session.state.project.findClip(clipId)
            ?: return error(command, BridgeErrorCode.CLIP_NOT_FOUND, "clip not found", false)
        val positionUs = payload.requiredLong("positionUs")
        require(positionUs > clip.startUs && positionUs < clip.startUs + clip.durationUs) { "split position must be inside clip" }
        return dispatchWrite(command, EditorAction.Execute(SplitClipCommand(clipId, positionUs)))
    }

    private fun executeTrim(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        val newStartUs = payload.optionalLong("newStartUs")
        val newEndUs = payload.optionalLong("newEndUs")
        require((newStartUs == null) != (newEndUs == null)) { "provide exactly one of newStartUs/newEndUs" }
        val action = if (newStartUs != null) {
            require(newStartUs >= 0L) { "newStartUs must be non-negative" }
            EditorAction.Execute(TrimClipLeftEdgeCommand(clipId, newStartUs))
        } else {
            require(newEndUs!! >= 0L) { "newEndUs must be non-negative" }
            EditorAction.Execute(TrimClipRightEdgeCommand(clipId, newEndUs))
        }
        return dispatchWrite(command, action)
    }

    private fun executeTransform(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        val current = session.state.project.findClip(clipId)!!.transform
        val transform = TransformValue(
            x = payload.optionalDouble("x") ?: current.x,
            y = payload.optionalDouble("y") ?: current.y,
            scale = payload.optionalDouble("scale") ?: current.scale,
            rotationDeg = payload.optionalDouble("rotationDeg") ?: current.rotationDeg,
            opacity = payload.optionalDouble("opacity") ?: current.opacity,
        )
        require(transform.scale > 0.0) { "scale must be positive" }
        require(transform.opacity in 0.0..1.0) { "opacity must be in 0..1" }
        return dispatchWrite(command, EditorAction.Execute(SetTransformCommand(clipId, transform)))
    }

    private fun executeVolume(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val payload = payload(command)
        val clipId = payload.requiredString("clipId")
        requireClip(command, clipId)?.let { return it }
        val volume = payload.requiredDouble("volume")
        require(volume >= 0.0) { "volume must be non-negative" }
        return dispatchWrite(command, EditorAction.Execute(SetVolumeCommand(clipId, volume)))
    }

    private fun executeHistory(command: BridgeCommandEnvelope, action: EditorAction): BridgeResultEnvelope {
        val before = session.state.revision
        session.dispatch(action)
        return if (session.state.revision == before) {
            success(command, "{\"changed\":false}")
        } else {
            success(command, "{\"changed\":true}")
        }
    }

    private fun executeExport(command: BridgeCommandEnvelope): BridgeResultEnvelope = when (val reservation = jobs.reserveExport(command.messageId)) {
        is BridgeJobReservation.Existing -> success(command, jobJson(reservation.job))
        is BridgeJobReservation.Busy -> error(
            command,
            BridgeErrorCode.EXPORT_BUSY,
            "another export is active",
            retryable = true,
            detailsJson = jobJson(reservation.activeJob),
        )
        is BridgeJobReservation.Started -> {
            val jobId = reservation.job.jobId
            val listener = object : BridgeExportListener {
                override fun onStarted() {
                    jobs.markRunning(jobId)
                }

                override fun onProgress(percent: Int) {
                    jobs.updateProgress(jobId, percent.coerceIn(0, 100))
                }

                override fun onCompleted(resultJson: String) {
                    jobs.succeed(jobId, resultJson)
                }

                override fun onFailed(message: String) {
                    jobs.fail(jobId, BridgeErrorCode.EXPORT_FAILED, sanitize(message))
                }

                override fun onCancelled() {
                    jobs.cancel(jobId)
                }
            }
            val started = exportPort.start(session.state.project, listener)
            if (started.isFailure) {
                val message = started.exceptionOrNull()?.message ?: "export could not start"
                jobs.fail(jobId, BridgeErrorCode.EXPORT_FAILED, sanitize(message))
                error(command, BridgeErrorCode.EXPORT_FAILED, message, retryable = true)
            } else {
                success(command, jobJson(requireNotNull(jobs.get(jobId))))
            }
        }
    }

    private fun executeCancelExport(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val jobId = payload(command).requiredString("jobId")
        val job = jobs.get(jobId) ?: return error(command, BridgeErrorCode.INVALID_ARGUMENT, "job not found", false)
        val cancelled = exportPort.cancel()
        if (cancelled.isFailure) {
            return error(
                command,
                BridgeErrorCode.EXPORT_FAILED,
                cancelled.exceptionOrNull()?.message ?: "export cancellation failed",
                retryable = true,
            )
        }
        jobs.cancel(jobId)
        return success(command, jobJson(requireNotNull(jobs.get(job.jobId))))
    }

    private fun dispatchWrite(command: BridgeCommandEnvelope, action: EditorAction): BridgeResultEnvelope {
        val beforeRevision = session.state.revision
        session.dispatch(action)
        val after = session.state
        if (after.revision == beforeRevision) {
            val message = after.lastError ?: "edit was not applied"
            return error(command, mapDomainError(message), message, false)
        }
        return success(command, "{\"applied\":true}")
    }

    private fun requireClip(command: BridgeCommandEnvelope, clipId: String): BridgeResultEnvelope? =
        if (session.state.project.findClip(clipId) == null) {
            error(command, BridgeErrorCode.CLIP_NOT_FOUND, "clip not found", false)
        } else null

    private fun allocateDuplicateId(clipId: String): String {
        var suffix = 1
        while (true) {
            val candidate = if (suffix == 1) "$clipId-copy" else "$clipId-copy-$suffix"
            if (session.state.project.findClip(candidate) == null) return candidate
            suffix++
        }
    }

    private fun success(command: BridgeCommandEnvelope, resultJson: String): BridgeResultEnvelope = BridgeResultEnvelope(
        messageId = command.messageId,
        success = true,
        code = null,
        projectRevision = session.state.revision,
        resultJson = resultJson,
        retryable = false,
    )

    private fun error(
        command: BridgeCommandEnvelope,
        code: BridgeErrorCode,
        message: String,
        retryable: Boolean,
        detailsJson: String = "{}",
    ): BridgeResultEnvelope = BridgeResultEnvelope(
        messageId = command.messageId,
        success = false,
        code = code,
        projectRevision = session.state.revision,
        resultJson = "{\"message\":${jsonString(sanitize(message))},\"details\":$detailsJson}",
        retryable = retryable,
    )

    private fun mapDomainError(message: String): BridgeErrorCode {
        val lower = message.lowercase()
        return when {
            "overlap" in lower -> BridgeErrorCode.OVERLAPPING_CLIPS
            "clip" in lower && "not found" in lower -> BridgeErrorCode.CLIP_NOT_FOUND
            "track" in lower && "not found" in lower -> BridgeErrorCode.TRACK_NOT_FOUND
            "asset" in lower && "unavailable" in lower -> BridgeErrorCode.ASSET_UNAVAILABLE
            else -> BridgeErrorCode.INVALID_ARGUMENT
        }
    }

    private fun payload(command: BridgeCommandEnvelope): JObject = try {
        MiniJson.parse(command.payloadJson).asObject()
    } catch (error: Throwable) {
        throw IllegalArgumentException("payload must be a JSON object", error)
    }

    private fun JObject.requiredString(key: String): String = required(key).asString().also {
        require(it.isNotBlank()) { "$key must not be blank" }
    }

    private fun JObject.optionalString(key: String): String? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asString()
    }

    private fun JObject.requiredLong(key: String): Long = required(key).asLong()
    private fun JObject.optionalLong(key: String): Long? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asLong()
    }
    private fun JObject.requiredDouble(key: String): Double = required(key).asDouble()
    private fun JObject.optionalDouble(key: String): Double? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asDouble()
    }

    private fun jobJson(job: BridgeJobSnapshot): String = """{"jobId":${jsonString(job.jobId)},"state":${jsonString(job.state.name)},"progressPercent":${job.progressPercent ?: "null"}}"""

    private fun sanitize(message: String): String = message
        .replace(Regex("(?:file|content)://\\S+"), "[redacted-uri]")
        .take(500)
}
