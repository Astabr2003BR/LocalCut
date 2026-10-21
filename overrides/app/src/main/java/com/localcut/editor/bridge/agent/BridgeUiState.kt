package com.localcut.editor.bridge.agent

enum class BridgeUiPhase {
    OFFLINE,
    PAIRING,
    CONNECTING,
    ONLINE,
    EDITING,
    EXPORTING,
    ERROR,
}

data class BridgeUiState(
    val phase: BridgeUiPhase = BridgeUiPhase.OFFLINE,
    val paired: Boolean = false,
    val alwaysConnected: Boolean = false,
    val pairingCode: String? = null,
    val exportProgressPercent: Int? = null,
    val message: String? = null,
) {
    fun reduce(event: BridgeUiEvent): BridgeUiState = when (event) {
        is BridgeUiEvent.Pairing -> copy(
            phase = BridgeUiPhase.PAIRING,
            pairingCode = event.code.take(12),
            exportProgressPercent = null,
            message = null,
        )

        BridgeUiEvent.Connecting -> copy(
            phase = BridgeUiPhase.CONNECTING,
            pairingCode = null,
            exportProgressPercent = null,
            message = null,
        )

        BridgeUiEvent.Connected -> copy(
            phase = BridgeUiPhase.ONLINE,
            paired = true,
            pairingCode = null,
            exportProgressPercent = null,
            message = null,
        )

        BridgeUiEvent.Editing -> copy(
            phase = BridgeUiPhase.EDITING,
            exportProgressPercent = null,
            message = null,
        )

        is BridgeUiEvent.Exporting -> copy(
            phase = BridgeUiPhase.EXPORTING,
            exportProgressPercent = event.progressPercent?.coerceIn(0, 100),
            message = null,
        )

        BridgeUiEvent.Disconnected -> copy(
            phase = BridgeUiPhase.OFFLINE,
            pairingCode = null,
            exportProgressPercent = null,
            message = null,
        )

        BridgeUiEvent.PairingRevoked -> BridgeUiState(
            phase = BridgeUiPhase.OFFLINE,
            paired = false,
            alwaysConnected = false,
        )

        is BridgeUiEvent.AlwaysConnectedChanged -> copy(alwaysConnected = event.enabled)

        is BridgeUiEvent.Error -> copy(
            phase = BridgeUiPhase.ERROR,
            exportProgressPercent = null,
            message = sanitizeBridgeUiMessage(event.message),
        )
    }
}

sealed interface BridgeUiEvent {
    data class Pairing(val code: String) : BridgeUiEvent
    data object Connecting : BridgeUiEvent
    data object Connected : BridgeUiEvent
    data object Editing : BridgeUiEvent
    data class Exporting(val progressPercent: Int?) : BridgeUiEvent
    data object Disconnected : BridgeUiEvent
    data object PairingRevoked : BridgeUiEvent
    data class AlwaysConnectedChanged(val enabled: Boolean) : BridgeUiEvent
    data class Error(val message: String) : BridgeUiEvent
}

fun sanitizeBridgeUiMessage(message: String): String = message
    .replace(Regex("(?i)Bearer\\s+\\S+"), "Bearer [redacted]")
    .replace(Regex("(?:content|file)://\\S+"), "[redacted-uri]")
    .take(240)
