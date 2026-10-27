package com.localcut.editor.termux

object TermuxEngineAutoConnectPolicy {
    const val HEALTH_CHECK_INTERVAL_MS: Long = 30_000L

    fun shouldProbe(state: TermuxEngineUiState): Boolean =
        state.paired && !state.busy && state.phase == TermuxEngineUiPhase.PAIRED_OFFLINE

    fun shouldHealthCheck(state: TermuxEngineUiState): Boolean =
        state.paired &&
            !state.busy &&
            state.phase != TermuxEngineUiPhase.NOT_PAIRED &&
            state.phase != TermuxEngineUiPhase.CONNECTING
}
