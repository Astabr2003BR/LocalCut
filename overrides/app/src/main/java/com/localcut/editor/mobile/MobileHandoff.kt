package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.data.project.jsonString
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses explicit user-triggered Android handoff links. The link itself never executes silently:
 * MobileHandoffActivity always asks for local confirmation before dispatching a command.
 */
enum class MobileHandoffReturnTarget {
    CHATGPT,
    EDITOR,
}

data class MobileHandoffCommand(
    val capability: BridgeCapability?,
    val payloadJson: String,
    val description: String,
    val returnTarget: MobileHandoffReturnTarget = MobileHandoffReturnTarget.EDITOR,
    val mobilePairingCode: String? = null,
    val remoteMediaUrl: String? = null,
    val resolveSelectedClip: Boolean = false,
    val resolveVisualClipAtPositionUs: Long? = null,
)

object MobileHandoffParser {
    private const val MAX_URI_LENGTH = 8_192
    private const val HTTPS_HOST = "localcut-bridge-v1.onrender.com"
    private val PAIRING_CODE_REGEX = Regex("^[0-9]{6}$")

    fun parse(rawUri: String?): Result<MobileHandoffCommand> = runCatching {
        require(!rawUri.isNullOrBlank()) { "HANDOFF_URI_MISSING" }
        require(rawUri.length <= MAX_URI_LENGTH) { "HANDOFF_URI_TOO_LONG" }
        val uri = URI(rawUri)
        val normalizedScheme = uri.scheme?.lowercase()
        val accepted = when (normalizedScheme) {
            "localcut" -> uri.host == "command"
            "https" -> uri.host == HTTPS_HOST && uri.path == "/open"
            else -> false
        }
        require(accepted) { "HANDOFF_URI_NOT_ALLOWED" }

        val params = parseQuery(uri.rawQuery)
        val returnTarget = when (params["returnTo"]?.lowercase()) {
            null, "" -> if (normalizedScheme == "https") {
                MobileHandoffReturnTarget.CHATGPT
            } else {
                MobileHandoffReturnTarget.EDITOR
            }
            "chatgpt" -> MobileHandoffReturnTarget.CHATGPT
            "editor" -> MobileHandoffReturnTarget.EDITOR
            else -> error("HANDOFF_RETURN_TARGET_INVALID")
        }
        val action = params["action"]?.lowercase() ?: error("HANDOFF_ACTION_MISSING")
        val command = when (action) {
            "pair_mobile" -> {
                val code = requiredText(params, "code", 6)
                require(PAIRING_CODE_REGEX.matches(code)) { "HANDOFF_CODE_INVALID" }
                MobileHandoffCommand(
                    capability = null,
                    payloadJson = "{}",
                    description = "Autorizar o pareamento do LocalCut com o ChatGPT usando o código $code",
                    mobilePairingCode = code,
                )
            }
            "import_url" -> {
                val rawUrl = requiredText(params, "url", 4_096)
                val mediaUri = RemoteMediaUrlPolicy.parseAndValidate(rawUrl).getOrThrow()
                MobileHandoffCommand(
                    capability = null,
                    payloadJson = "{}",
                    description = "Baixar e importar mídia remota no LocalCut",
                    remoteMediaUrl = mediaUri.toASCIIString(),
                )
            }
            "seek" -> {
                val positionUs = requiredLong(params, "positionUs", min = 0L)
                MobileHandoffCommand(
                    BridgeCapability.SEEK,
                    "{\"positionUs\":$positionUs}",
                    "Ir para ${(positionUs / 1_000_000.0)} s na linha do tempo",
                )
            }
            "undo" -> MobileHandoffCommand(BridgeCapability.UNDO, "{}", "Desfazer a última edição")
            "redo" -> MobileHandoffCommand(BridgeCapability.REDO, "{}", "Refazer a última edição")
            "split_clip" -> {
                val clipId = requiredText(params, "clipId", 256)
                val positionUs = requiredLong(params, "positionUs", min = 0L)
                MobileHandoffCommand(
                    BridgeCapability.SPLIT_CLIP,
                    "{\"clipId\":${jsonString(clipId)},\"positionUs\":$positionUs}",
                    "Dividir o clipe $clipId em ${(positionUs / 1_000_000.0)} s",
                )
            }
            "split_selected_clip" -> {
                val positionUs = requiredLong(params, "positionUs", min = 0L)
                MobileHandoffCommand(
                    capability = BridgeCapability.SPLIT_CLIP,
                    payloadJson = "{\"positionUs\":$positionUs}",
                    description = "Dividir o clipe selecionado em ${(positionUs / 1_000_000.0)} s",
                    resolveSelectedClip = true,
                )
            }
            "cut_delete_after" -> {
                val positionUs = requiredLong(params, "positionUs", min = 1L)
                MobileHandoffCommand(
                    capability = BridgeCapability.TRIM_CLIP,
                    payloadJson = "{}",
                    description = "Cortar em ${(positionUs / 1_000_000.0)} s e apagar a parte da direita",
                    resolveVisualClipAtPositionUs = positionUs,
                )
            }
            "trim_clip" -> {
                val clipId = requiredText(params, "clipId", 256)
                val start = params["newStartUs"]?.let { parseLong(it, "newStartUs", 0L) }
                val end = params["newEndUs"]?.let { parseLong(it, "newEndUs", 0L) }
                require((start == null) != (end == null)) { "HANDOFF_TRIM_EDGE_REQUIRED" }
                val edgeJson = if (start != null) "\"newStartUs\":$start" else "\"newEndUs\":$end"
                MobileHandoffCommand(
                    BridgeCapability.TRIM_CLIP,
                    "{\"clipId\":${jsonString(clipId)},$edgeJson}",
                    if (start != null) "Aparar o início do clipe $clipId" else "Aparar o fim do clipe $clipId",
                )
            }
            "trim_selected_clip" -> {
                val start = params["newStartUs"]?.let { parseLong(it, "newStartUs", 0L) }
                val end = params["newEndUs"]?.let { parseLong(it, "newEndUs", 0L) }
                require((start == null) != (end == null)) { "HANDOFF_TRIM_EDGE_REQUIRED" }
                val edgeJson = if (start != null) "\"newStartUs\":$start" else "\"newEndUs\":$end"
                MobileHandoffCommand(
                    capability = BridgeCapability.TRIM_CLIP,
                    payloadJson = "{$edgeJson}",
                    description = if (start != null) {
                        "Aparar o início do clipe selecionado"
                    } else {
                        "Aparar o fim do clipe selecionado"
                    },
                    resolveSelectedClip = true,
                )
            }
            "move_clip" -> {
                val clipId = requiredText(params, "clipId", 256)
                val newStartUs = requiredLong(params, "newStartUs", min = 0L)
                MobileHandoffCommand(
                    BridgeCapability.MOVE_CLIP,
                    "{\"clipId\":${jsonString(clipId)},\"newStartUs\":$newStartUs}",
                    "Mover o clipe $clipId para ${(newStartUs / 1_000_000.0)} s",
                )
            }
            "delete_clip" -> {
                val clipId = requiredText(params, "clipId", 256)
                MobileHandoffCommand(
                    BridgeCapability.DELETE_CLIP,
                    "{\"clipId\":${jsonString(clipId)}}",
                    "Excluir o clipe $clipId",
                )
            }
            "delete_selected_clip" -> MobileHandoffCommand(
                capability = BridgeCapability.DELETE_CLIP,
                payloadJson = "{\"selectedClip\":true}",
                description = "Excluir o clipe selecionado",
                resolveSelectedClip = true,
            )
            "set_volume" -> {
                val clipId = requiredText(params, "clipId", 256)
                val volume = requiredDouble(params, "volume", min = 0.0, max = 8.0)
                MobileHandoffCommand(
                    BridgeCapability.SET_VOLUME,
                    "{\"clipId\":${jsonString(clipId)},\"volume\":$volume}",
                    "Definir o volume do clipe $clipId para $volume",
                )
            }
            "add_text" -> {
                val id = requiredText(params, "id", 256)
                val text = requiredText(params, "text", 1_000)
                val startUs = requiredLong(params, "startUs", min = 0L)
                val durationUs = requiredLong(params, "durationUs", min = 1L)
                MobileHandoffCommand(
                    BridgeCapability.ADD_TEXT,
                    "{\"id\":${jsonString(id)},\"text\":${jsonString(text)},\"startUs\":$startUs,\"durationUs\":$durationUs}",
                    "Adicionar texto \"${text.take(80)}\"",
                )
            }
            "export_project" -> MobileHandoffCommand(
                BridgeCapability.EXPORT_PROJECT,
                "{}",
                "Exportar o projeto atual para MP4",
            )
            else -> error("HANDOFF_ACTION_UNSUPPORTED")
        }
        command.copy(returnTarget = returnTarget)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').associate { part ->
            val index = part.indexOf('=')
            val rawKey = if (index >= 0) part.substring(0, index) else part
            val rawValue = if (index >= 0) part.substring(index + 1) else ""
            decode(rawKey) to decode(rawValue)
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun requiredText(params: Map<String, String>, key: String, maxLength: Int): String {
        val value = params[key]?.trim().orEmpty()
        require(value.isNotEmpty() && value.length <= maxLength) { "HANDOFF_${key.uppercase()}_INVALID" }
        return value
    }

    private fun requiredLong(params: Map<String, String>, key: String, min: Long): Long =
        parseLong(params[key] ?: error("HANDOFF_${key.uppercase()}_MISSING"), key, min)

    private fun parseLong(value: String, key: String, min: Long): Long {
        val parsed = value.toLongOrNull() ?: error("HANDOFF_${key.uppercase()}_INVALID")
        require(parsed >= min) { "HANDOFF_${key.uppercase()}_INVALID" }
        return parsed
    }

    private fun requiredDouble(
        params: Map<String, String>,
        key: String,
        min: Double,
        max: Double,
    ): Double {
        val value = params[key]?.toDoubleOrNull() ?: error("HANDOFF_${key.uppercase()}_INVALID")
        require(value in min..max) { "HANDOFF_${key.uppercase()}_INVALID" }
        return value
    }
}
