package com.localcut.editor.termux

object TermuxEngineProtocol {
    const val PROTOCOL_VERSION = 1
    const val PROJECT_ID = "localcut"
    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 43127
    const val MAX_RESPONSE_BYTES = 16_384
    const val PAIRING_CODE_LENGTH = 12

    val REQUIRED_CAPABILITIES: Set<String> = setOf(
        "PING",
        "GET_STATUS",
        "GET_CAPABILITIES",
    )
}

enum class TermuxEngineOperation {
    PING,
    GET_STATUS,
    GET_CAPABILITIES,
}

data class TermuxEnginePairing(val protocolVersion: Int, val token: String)
data class TermuxEnginePing(val protocolVersion: Int, val status: String)
data class TermuxEngineStatus(
    val protocolVersion: Int,
    val engineVersion: String,
    val projectId: String,
    val status: String,
    val pid: Long,
)
data class TermuxEngineCapabilities(val protocolVersion: Int, val capabilities: Set<String>)

interface TermuxEngineClient {
    suspend fun pair(pairingCode: String): Result<TermuxEnginePairing>
    suspend fun ping(): Result<TermuxEnginePing>
    suspend fun getStatus(): Result<TermuxEngineStatus>
    suspend fun getCapabilities(): Result<TermuxEngineCapabilities>
}

interface TermuxEngineTokenStore {
    fun save(token: String)
    fun load(): String?
    fun clear()
}

object TermuxEngineEndpointPolicy {
    fun validate(host: String, port: Int) {
        require(isAllowedHost(host)) { "TERMUX_ENGINE_ENDPOINT_PRIVATE_ONLY" }
        require(port in 1..65535) { "TERMUX_ENGINE_ENDPOINT_PORT_INVALID" }
    }

    fun isAllowedHost(host: String): Boolean {
        if (host == TermuxEngineProtocol.DEFAULT_HOST) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }
}

object TermuxEngineTokenPolicy {
    private val TOKEN_REGEX = Regex("^[A-Za-z0-9._~-]{32,256}$")

    fun normalize(raw: String): String {
        val token = raw.trim()
        require(TOKEN_REGEX.matches(token)) { "TERMUX_ENGINE_TOKEN_INVALID" }
        return token
    }
}

object TermuxEnginePairingCodePolicy {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun normalize(raw: String): String {
        val normalized = raw.trim()
            .replace("-", "")
            .replace(" ", "")
            .uppercase()
        require(
            normalized.length == TermuxEngineProtocol.PAIRING_CODE_LENGTH &&
                normalized.all(ALPHABET::contains),
        ) { "TERMUX_ENGINE_PAIRING_CODE_INVALID" }
        return normalized
    }
}
