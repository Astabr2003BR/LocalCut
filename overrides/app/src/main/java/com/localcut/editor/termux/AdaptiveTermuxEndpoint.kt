package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient
import java.net.ConnectException
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.Collections

internal data class TermuxEngineEndpoint(
    val host: String,
    val mediaPort: Int,
    val agentPort: Int,
)

internal object TermuxEngineEndpointResolver {
    private const val PRIVATE_MEDIA_PORT = 43227
    private const val PRIVATE_AGENT_PORT = 43228

    fun candidates(): List<TermuxEngineEndpoint> {
        val result = mutableListOf(
            TermuxEngineEndpoint(
                host = TermuxEngineProtocol.DEFAULT_HOST,
                mediaPort = TermuxEngineProtocol.DEFAULT_PORT,
                agentPort = TermuxDeveloperAgentProtocol.PORT,
            ),
        )
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrDefault(emptyList())
        interfaces.asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .mapNotNull { address ->
                val host = address.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                if (TermuxEngineEndpointPolicy.isAllowedHost(host) && host != TermuxEngineProtocol.DEFAULT_HOST) host else null
            }
            .distinct()
            .forEach { host -> result += TermuxEngineEndpoint(host, PRIVATE_MEDIA_PORT, PRIVATE_AGENT_PORT) }
        return result.distinct()
    }
}

private fun Throwable.isEndpointUnavailable(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ConnectException || current is SocketTimeoutException || current is NoRouteToHostException) return true
        current = current.cause
    }
    return message in setOf("TERMUX_ENGINE_UNAVAILABLE", "TERMUX_AGENT_UNAVAILABLE")
}

private suspend fun <T> tryEndpoints(
    endpoints: List<TermuxEngineEndpoint>,
    action: suspend (TermuxEngineEndpoint) -> Result<T>,
): Result<T> {
    var lastFailure: Throwable? = null
    for (endpoint in endpoints) {
        val result = action(endpoint)
        if (result.isSuccess) return result
        val error = result.exceptionOrNull() ?: continue
        lastFailure = error
        if (!error.isEndpointUnavailable()) return result
    }
    return Result.failure(lastFailure ?: IllegalStateException("TERMUX_ENGINE_UNAVAILABLE"))
}

internal class AdaptiveTermuxEngineJobClient(
    private val tokenProvider: () -> String?,
    private val endpointProvider: () -> List<TermuxEngineEndpoint> = TermuxEngineEndpointResolver::candidates,
) : TermuxEngineJobClient {
    private fun client(endpoint: TermuxEngineEndpoint) = SocketTermuxEngineJobClient(
        host = endpoint.host,
        port = endpoint.mediaPort,
        tokenProvider = tokenProvider,
    )

    override suspend fun pair(pairingCode: String) = tryEndpoints(endpointProvider()) { client(it).pair(pairingCode) }
    override suspend fun ping() = tryEndpoints(endpointProvider()) { client(it).ping() }
    override suspend fun getStatus() = tryEndpoints(endpointProvider()) { client(it).getStatus() }
    override suspend fun getCapabilities() = tryEndpoints(endpointProvider()) { client(it).getCapabilities() }
    override suspend fun createAnalyzeMediaJob(input: TermuxAnalyzeMediaInput, idempotencyKey: String) =
        tryEndpoints(endpointProvider()) { client(it).createAnalyzeMediaJob(input, idempotencyKey) }
    override suspend fun getJob(jobId: String) = tryEndpoints(endpointProvider()) { client(it).getJob(jobId) }
    override suspend fun cancelJob(jobId: String) = tryEndpoints(endpointProvider()) { client(it).cancelJob(jobId) }
    override suspend fun stageMedia(jobId: String, source: TermuxEngineMediaSource) =
        tryEndpoints(endpointProvider()) { client(it).stageMedia(jobId, source) }
}

internal class AdaptiveTermuxApprovalClient(
    private val tokenProvider: () -> String?,
    private val endpointProvider: () -> List<TermuxEngineEndpoint> = TermuxEngineEndpointResolver::candidates,
) : TermuxAgentApprovalClient {
    override suspend fun getPendingApprovals() = tryEndpoints(endpointProvider()) { endpoint ->
        SocketTermuxAgentApprovalClient(endpoint.host, endpoint.agentPort, tokenProvider).getPendingApprovals()
    }

    override suspend fun resolveApproval(approvalId: String, decision: TermuxApprovalDecision) =
        tryEndpoints(endpointProvider()) { endpoint ->
            SocketTermuxAgentApprovalClient(endpoint.host, endpoint.agentPort, tokenProvider)
                .resolveApproval(approvalId, decision)
        }
}

internal class AdaptiveTermuxDeveloperAgentClient(
    private val tokenProvider: () -> String?,
    private val endpointProvider: () -> List<TermuxEngineEndpoint> = TermuxEngineEndpointResolver::candidates,
) : BridgeTermuxDeveloperClient {
    override suspend fun execute(request: BridgeTermuxAgentRequest, taskId: String) =
        tryEndpoints(endpointProvider()) { endpoint ->
            SocketTermuxDeveloperAgentClient(endpoint.host, endpoint.agentPort, tokenProvider)
                .execute(request, taskId)
        }
}
