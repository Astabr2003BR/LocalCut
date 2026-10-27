package com.localcut.editor.termux

object TermuxEngineAutoConnectPolicy {
    fun shouldProbe(state: TermuxEngineUiState): Boolean =
        state.paired && !state.busy && state.phase == TermuxEngineUiPhase.PAIRED_OFFLINE
}
