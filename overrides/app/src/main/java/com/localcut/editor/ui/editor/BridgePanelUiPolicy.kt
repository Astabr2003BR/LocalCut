package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeUiPhase
import com.localcut.editor.bridge.agent.BridgeUiState

object BridgePanelUiPolicy {
    fun summary(state: BridgeUiState): String = when (state.phase) {
        BridgeUiPhase.OFFLINE -> if (state.paired) "Agente offline • pareado" else "Agente offline"
        BridgeUiPhase.PAIRING -> state.pairingCode?.let { "Pareando • $it" } ?: "Pareando"
        BridgeUiPhase.CONNECTING -> "Agente • conectando"
        BridgeUiPhase.ONLINE -> "Agente ao vivo • pronto"
        BridgeUiPhase.EDITING -> "Agente ao vivo • editando"
        BridgeUiPhase.EXPORTING -> state.exportProgressPercent
            ?.let { "Agente ao vivo • exportando $it%" }
            ?: "Agente ao vivo • exportando"
        BridgeUiPhase.ERROR -> "Erro na conexão do agente"
    }

    fun agentReady(state: BridgeUiState): Boolean = state.paired && when (state.phase) {
        BridgeUiPhase.ONLINE,
        BridgeUiPhase.EDITING,
        BridgeUiPhase.EXPORTING -> true
        else -> false
    }

    fun needsAttention(state: BridgeUiState): Boolean = state.phase == BridgeUiPhase.ERROR

    fun defaultExpanded(state: BridgeUiState): Boolean = false
}
