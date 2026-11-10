package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.animation.PortraitMotionPreset
import com.localcut.editor.domain.animation.PortraitMotionPresets
import com.localcut.editor.domain.animation.PortraitMotionSettings
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

/** Eye locations are normalized to the original portrait, top-left origin. */
data class MobileBlinkRegion(
    val leftX: Double,
    val rightX: Double,
    val y: Double,
    val radiusX: Double,
    val radiusY: Double,
    val firstBlinkSeconds: Double = 1.4,
    val intervalSeconds: Double = 3.2,
    val durationSeconds: Double = 0.18,
    val blinkStrength: Double = 1.0,
    val swayStrength: Double = 0.0,
    val breathStrength: Double = 0.0,
) {
    fun settings() = PortraitMotionSettings(
        leftX = leftX, rightX = rightX, eyeY = y,
        radiusX = radiusX, radiusY = radiusY,
        firstBlinkSeconds = firstBlinkSeconds,
        intervalSeconds = intervalSeconds, durationSeconds = durationSeconds,
        blinkStrength = blinkStrength, swayStrength = swayStrength,
        breathStrength = breathStrength,
    ).also { it.validate() }

    init {
        settings()
    }
}

data class MobileHandoffCommand(
    val capability: BridgeCapability?,
    val payloadJson: String,
    val description: String,
    val returnTarget: MobileHandoffReturnTarget = MobileHandoffReturnTarget.EDITOR,
    val mobilePairingCode: String? = null,
    val remoteMediaUrl: String? = null,
    val lipSyncRegion: MouthRegion? = null,
    val blinkRegion: MobileBlinkRegion? = null,
    val deleteAllVisualClips: Boolean = false,
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
            "blink" -> {
                // A single, backward-compatible handoff accepts future
                // variations as validated numeric data; no new native route
                // or remotely supplied executable code is needed for presets.
                val requestedPreset = params["preset"]?.let { value ->
                    PortraitMotionPreset.entries.singleOrNull {
                        it.name.equals(value, ignoreCase = true)
                    } ?: throw IllegalArgumentException("HANDOFF_UNKNOWN_MOTION_PRESET")
                }
                val defaults = requestedPreset?.let {
                    PortraitMotionPresets.preset(it)
                } ?: PortraitMotionSettings()
                fun number(name: String, min: Double, max: Double, fallback: Double) =
                    if (params.containsKey(name)) requiredDouble(params, name, min, max)
                    else fallback
                val region = MobileBlinkRegion(
                    leftX = number("leftX", 0.01, 0.99, defaults.leftX),
                    rightX = number("rightX", 0.01, 0.99, defaults.rightX),
                    y = number("y", 0.01, 0.99, defaults.eyeY),
                    radiusX = number("radiusX", 0.02, 0.15, defaults.radiusX),
                    radiusY = number("radiusY", 0.01, 0.09, defaults.radiusY),
                    firstBlinkSeconds = number(
                        "firstBlinkSeconds", 0.10, 3.0, defaults.firstBlinkSeconds,
                    ),
                    intervalSeconds = number(
                        "intervalSeconds", 2.0, 6.0, defaults.intervalSeconds,
                    ),
                    durationSeconds = number(
                        "durationSeconds", 0.10, 0.35, defaults.durationSeconds,
                    ),
                    blinkStrength = number(
                        "blinkStrength", 0.0, 1.0, defaults.blinkStrength,
                    ),
                    swayStrength = number(
                        "swayStrength", 0.0, 1.0, defaults.swayStrength,
                    ),
                    breathStrength = number(
                        "breathStrength", 0.0, 1.0, defaults.breathStrength,
                    ),
                )
                MobileHandoffCommand(
                    capability = null,
                    payloadJson = "{}",
                    description = "Configurar piscadas e movimento leve no retrato selecionado. " +
                        "Olhos: ${(region.leftX * 100).toInt()}%, " +
                        "${(region.rightX * 100).toInt()}% e altura " +
                        "${(region.y * 100).toInt()}%. " +
                        "Intervalo ${region.intervalSeconds}s, respiração " +
                        "${(region.breathStrength * 100).toInt()}%, oscilação " +
                        "${(region.swayStrength * 100).toInt()}%. " +
                        "Preserva TALK, áudio e outros efeitos. Desfazer disponível.",
                    blinkRegion = region,
                )
            }
            "lip_sync" -> {
                val x = requiredDouble(params, "x", min = 0.01, max = 0.99)
                val y = requiredDouble(params, "y", min = 0.005, max = 0.995)
                val rx = requiredDouble(params, "radiusX", min = 0.01, max = 0.50)
                val ry = requiredDouble(params, "radiusY", min = 0.005, max = 0.30)
                val region = MouthRegion(x, y, rx, ry)
                require(x >= rx && x <= 1.0 - rx && y >= ry && y <= 1.0 - ry) {
                    "HANDOFF_LIP_SYNC_REGION_OUTSIDE_FRAME"
                }
                MobileHandoffCommand(
                    capability = null,
                    payloadJson = "{}",
                    description = "Aplicar lip-sync local à única imagem e voz elegíveis. " +
                        "Boca: X=${(x * 100).toInt()}%, Y=${(y * 100).toInt()}%. " +
                        "Somente o trecho onde imagem e áudio coincidem será animado. " +
                        "O APK também poderá estender um áudio importado com duração padrão de 5s, " +
                        "quando a origem for maior e não houver conflitos. " +
                        "A operação poderá ser desfeita.",
                    lipSyncRegion = region,
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
            "delete_all_visual_clips" -> MobileHandoffCommand(
                capability = null,
                payloadJson = "{}",
                description = "Excluir todos os vídeos e imagens da timeline (áudio será preservado e a ação poderá ser desfeita)",
                deleteAllVisualClips = true,
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
            "project_edit" -> {
                val payload = requiredText(params, "payload", 7_000)
                MobileHandoffCommand(
                    BridgeCapability.PROJECT_EDIT,
                    payload,
                    "Aplicar edição em lote no projeto (uma única ação de Desfazer)",
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
