package com.localcut.editor.termux

enum class TermuxEngineUiPhase {
    NOT_PAIRED,
    CONNECTING,
    PAIRED_OFFLINE,
    CONNECTED,
    ERROR,
}

data class TermuxEngineUiState(
    val phase: TermuxEngineUiPhase,
    val paired: Boolean,
    val busy: Boolean = false,
    val statusText: String,
    val engineVersion: String? = null,
    val capabilities: Set<String> = emptySet(),
    val errorCode: String? = null,
) {
    val actionLabel: String
        get() = when {
            busy -> "Conectando…"
            paired -> "Testar conexão"
            phase == TermuxEngineUiPhase.ERROR -> "Tentar novamente"
            else -> "Conectar Agent Engine"
        }

    fun pairing(): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.CONNECTING,
        busy = true,
        statusText = if (paired) {
            "Testando LocalCut Agent Engine…"
        } else {
            "Conectando ao LocalCut Agent Engine…"
        },
        errorCode = null,
    )

    fun connected(engineVersion: String?, capabilities: Set<String>): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.CONNECTED,
        paired = true,
        busy = false,
        statusText = "LocalCut Agent Engine conectado",
        engineVersion = engineVersion,
        capabilities = capabilities,
        errorCode = null,
    )

    fun failed(code: String): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.ERROR,
        busy = false,
        statusText = "Falha ao conectar ao LocalCut Agent Engine",
        errorCode = code,
    )

    companion object {
        fun initial(paired: Boolean): TermuxEngineUiState = if (paired) {
            TermuxEngineUiState(
                phase = TermuxEngineUiPhase.PAIRED_OFFLINE,
                paired = true,
                statusText = "Agent Engine pareado • conexão ainda não verificada",
            )
        } else {
            TermuxEngineUiState(
                phase = TermuxEngineUiPhase.NOT_PAIRED,
                paired = false,
                statusText = "LocalCut Agent Engine não conectado",
            )
        }
    }
}
