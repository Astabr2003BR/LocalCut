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
            else -> "Conectar Termux"
        }

    fun pairing(): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.CONNECTING,
        busy = true,
        statusText = if (paired) {
            "Testando Termux Agent Engine…"
        } else {
            "Conectando ao Termux Agent Engine…"
        },
        errorCode = null,
    )

    fun connected(engineVersion: String?, capabilities: Set<String>): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.CONNECTED,
        paired = true,
        busy = false,
        statusText = "Termux Agent Engine conectado",
        engineVersion = engineVersion,
        capabilities = capabilities,
        errorCode = null,
    )

    fun failed(code: String): TermuxEngineUiState = copy(
        phase = TermuxEngineUiPhase.ERROR,
        busy = false,
        statusText = "Falha ao conectar ao Termux Agent Engine",
        errorCode = code,
    )

    companion object {
        fun initial(paired: Boolean): TermuxEngineUiState = if (paired) {
            TermuxEngineUiState(
                phase = TermuxEngineUiPhase.PAIRED_OFFLINE,
                paired = true,
                statusText = "Termux pareado • conexão ainda não verificada",
            )
        } else {
            TermuxEngineUiState(
                phase = TermuxEngineUiPhase.NOT_PAIRED,
                paired = false,
                statusText = "Termux Agent Engine não conectado",
            )
        }
    }
}
