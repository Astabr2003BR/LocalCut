package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeUiPhase
import com.localcut.editor.bridge.agent.BridgeUiState

object BridgePanelUiPolicy {
    fun summary(state: BridgeUiState): String = when (state.phase) {
        BridgeUiPhase.OFFLINE -> if (state.paired) "Offline • pareado" else "Offline"
        BridgeUiPhase.PAIRING -> state.pairingCode?.let { "Pareando • $it" } ?: "Pareando"
        BridgeUiPhase.CONNECTING -> "Conectando"
        BridgeUiPhase.ONLINE -> "Online"
        BridgeUiPhase.EDITING -> "Editando"
        BridgeUiPhase.EXPORTING -> state.exportProgressPercent?.let { "Exportando • $it%" } ?: "Exportando"
        BridgeUiPhase.ERROR -> "Erro de conexão"
    }

    fun needsAttention(state: BridgeUiState): Boolean = state.phase == BridgeUiPhase.ERROR

    fun defaultExpanded(state: BridgeUiState): Boolean = false
}
