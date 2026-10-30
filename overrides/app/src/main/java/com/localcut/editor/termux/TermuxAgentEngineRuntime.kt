package com.localcut.editor.termux

import android.content.Context
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.Project
import com.localcut.editor.mobile.GeneratedMediaAssetInspector
import com.localcut.editor.mobile.PreparedGeneratedVideo
import com.localcut.editor.mobile.SharedMediaStager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TermuxAgentEngineRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val generatedMediaDirectory = TermuxGeneratedMediaStoragePolicy.durableDirectory(appContext.filesDir)

    val tokenStore: TermuxEngineTokenStore = AndroidKeystoreTermuxEngineTokenStore(appContext)
    private val baseJobClient: TermuxEngineJobClient = AdaptiveTermuxEngineJobClient(tokenProvider = tokenStore::load)
    val jobClient: TermuxEngineJobClient = RecoveringTermuxEngineJobClient(
        delegate = baseJobClient,
        wakeAction = { wakeAutomation() },
    )
    val client: TermuxEngineClient = jobClient
    val approvalClient: TermuxAgentApprovalClient = AdaptiveTermuxApprovalClient(tokenProvider = tokenStore::load)
    private val baseDeveloperAgentClient: BridgeTermuxDeveloperClient =
        AdaptiveTermuxDeveloperAgentClient(tokenProvider = tokenStore::load)
    val developerAgentClient: BridgeTermuxDeveloperClient = PreflightTermuxDeveloperAgentClient(
        delegate = baseDeveloperAgentClient,
        engineClient = client,
    )
    private val mediaSourceResolver = AndroidTermuxMediaSourceResolver(appContext.contentResolver)
    private val sharedMediaStager = SharedMediaStager(appContext)
    private val generatedMediaGarbageCollector = GeneratedMediaGarbageCollector(
        remoteMediaDirectory = generatedMediaDirectory,
        providerAuthority = "${appContext.packageName}.remote-media",
    )
    val mediaAnalyzeCoordinator = TermuxMediaAnalyzeCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
    )
    val mediaTrackingCoordinator = TermuxMediaTrackingCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
    )
    val mediaObjectTrackingCoordinator = TermuxMediaObjectTrackingCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
    )
    val mediaReverseCoordinator = TermuxMediaReverseCoordinator(
        client = jobClient,
        sourceResolver = mediaSourceResolver,
        outputDirectory = generatedMediaDirectory,
    )
    val generatedMediaAssetInspector = GeneratedMediaAssetInspector(
        importer = AndroidAssetImporter(appContext.contentResolver),
        sharedMediaStager = sharedMediaStager,
    )
    private val pairingService = TermuxEnginePairingService(client, tokenStore)

    suspend fun pair(pairingCode: String): Result<Unit> = pairingService.pair(pairingCode)

    fun wakeAutomation(force: Boolean = false): Result<Boolean> =
        AndroidTermuxAutomation.wake(appContext, force = force)

    suspend fun reverseMedia(
        asset: Asset,
        options: TermuxReverseOptions = TermuxReverseOptions(),
    ): Result<PreparedGeneratedVideo> = runCatching {
        val download = mediaReverseCoordinator.reverse(asset, options).getOrThrow()
        try {
            val prepared = generatedMediaAssetInspector.inspect(download.file).getOrThrow()
            require(download.media.sizeBytes == prepared.asset.sizeBytes) {
                "TERMUX_REVERSE_PREPARED_SIZE_MISMATCH"
            }
            require(download.media.mimeType == prepared.asset.mimeType) {
                "TERMUX_REVERSE_PREPARED_TYPE_MISMATCH"
            }
            prepared
        } catch (error: Throwable) {
            download.file.delete()
            throw error
        }
    }

    internal suspend fun collectGeneratedMediaOrphans(
        project: Project,
        canUndo: Boolean,
        canRedo: Boolean,
    ): Result<GeneratedMediaCleanupResult> = withContext(Dispatchers.IO) {
        generatedMediaGarbageCollector.collect(
            assetUris = project.assets.map(Asset::uri),
            canUndo = canUndo,
            canRedo = canRedo,
        )
    }

    companion object {
        @Volatile
        private var instance: TermuxAgentEngineRuntime? = null

        fun get(context: Context): TermuxAgentEngineRuntime = instance ?: synchronized(this) {
            instance ?: TermuxAgentEngineRuntime(context.applicationContext).also { instance = it }
        }
    }
}
