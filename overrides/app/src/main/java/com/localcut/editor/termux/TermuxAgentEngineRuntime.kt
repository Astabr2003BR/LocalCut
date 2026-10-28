package com.localcut.editor.termux

import android.content.Context
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient

class TermuxAgentEngineRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TermuxEngineTokenStore = AndroidKeystoreTermuxEngineTokenStore(appContext)
    val jobClient: TermuxEngineJobClient = AdaptiveTermuxEngineJobClient(tokenProvider = tokenStore::load)
    val client: TermuxEngineClient = jobClient
    val approvalClient: TermuxAgentApprovalClient = AdaptiveTermuxApprovalClient(tokenProvider = tokenStore::load)
    val developerAgentClient: BridgeTermuxDeveloperClient =
        AdaptiveTermuxDeveloperAgentClient(tokenProvider = tokenStore::load)
    private val mediaSourceResolver = AndroidTermuxMediaSourceResolver(appContext.contentResolver)
    val mediaAnalyzeCoordinator = TermuxMediaAnalyzeCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
    )
    val mediaTrackingCoordinator = TermuxMediaTrackingCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
    )
    val mediaReverseCoordinator = TermuxMediaReverseCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
        outputDirectory = TermuxGeneratedMediaStoragePolicy.durableDirectory(appContext.filesDir),
    )
    private val pairingService = TermuxEnginePairingService(client, tokenStore)

    suspend fun pair(pairingCode: String): Result<Unit> = pairingService.pair(pairingCode)

    companion object {
        @Volatile
        private var instance: TermuxAgentEngineRuntime? = null

        fun get(context: Context): TermuxAgentEngineRuntime = instance ?: synchronized(this) {
            instance ?: TermuxAgentEngineRuntime(context.applicationContext).also { instance = it }
        }
    }
}
