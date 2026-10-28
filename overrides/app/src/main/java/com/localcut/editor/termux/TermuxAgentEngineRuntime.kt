package com.localcut.editor.termux

import android.content.Context
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.mobile.GeneratedMediaAssetInspector
import com.localcut.editor.mobile.PreparedGeneratedVideo
import com.localcut.editor.mobile.SharedMediaStager

class TermuxAgentEngineRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TermuxEngineTokenStore = AndroidKeystoreTermuxEngineTokenStore(appContext)
    val jobClient: TermuxEngineJobClient = AdaptiveTermuxEngineJobClient(tokenProvider = tokenStore::load)
    val client: TermuxEngineClient = jobClient
    val approvalClient: TermuxAgentApprovalClient = AdaptiveTermuxApprovalClient(tokenProvider = tokenStore::load)
    val developerAgentClient: BridgeTermuxDeveloperClient =
        AdaptiveTermuxDeveloperAgentClient(tokenProvider = tokenStore::load)
    private val mediaSourceResolver = AndroidTermuxMediaSourceResolver(appContext.contentResolver)
    private val sharedMediaStager = SharedMediaStager(appContext)
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
    val generatedMediaAssetInspector = GeneratedMediaAssetInspector(
        importer = AndroidAssetImporter(appContext.contentResolver),
        sharedMediaStager = sharedMediaStager,
    )
    private val pairingService = TermuxEnginePairingService(client, tokenStore)

    suspend fun pair(pairingCode: String): Result<Unit> = pairingService.pair(pairingCode)

    suspend fun reverseMedia(
        asset: Asset,
        options: TermuxReverseOptions = TermuxReverseOptions(),
    ): Result<PreparedGeneratedVideo> = runCatching {
        val download = mediaReverseCoordinator.reverse(asset, options).getOrThrow()
        val prepared = generatedMediaAssetInspector.inspect(download.file).getOrThrow()
        require(download.media.sizeBytes == prepared.asset.sizeBytes) {
            "TERMUX_REVERSE_PREPARED_SIZE_MISMATCH"
        }
        require(download.media.mimeType == prepared.asset.mimeType) {
            "TERMUX_REVERSE_PREPARED_TYPE_MISMATCH"
        }
        prepared
    }

    companion object {
        @Volatile
        private var instance: TermuxAgentEngineRuntime? = null

        fun get(context: Context): TermuxAgentEngineRuntime = instance ?: synchronized(this) {
            instance ?: TermuxAgentEngineRuntime(context.applicationContext).also { instance = it }
        }
    }
}
