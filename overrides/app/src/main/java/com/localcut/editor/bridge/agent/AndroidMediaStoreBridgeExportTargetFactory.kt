package com.localcut.editor.bridge.agent

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.ExportDestination
import java.io.File

class AndroidMediaStoreBridgeExportTargetFactory(
    private val contentResolver: ContentResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : BridgeExportTargetFactory {
    override fun create(project: Project): Result<BridgeExportTarget> = runCatching {
        if (sdkInt < Build.VERSION_CODES.Q) {
            throw BridgeLocalInteractionRequiredException(
                "Android 10+ required for unattended shared export; open LocalCut to export on this device",
            )
        }

        val spec = MediaStoreExportNamingPolicy.create(project.title, nowEpochMs())
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, spec.displayName)
            put(MediaStore.Video.Media.MIME_TYPE, spec.mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, spec.relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values)
            ?: throw IllegalStateException("unable to create LocalCut MediaStore export")

        BridgeExportTarget(
            destination = MediaStorePendingVideoDestination(contentResolver, uri),
            resultJson = """{"destination":"LOCALCUT_MEDIASTORE","displayName":${jsonString(spec.displayName)},"relativePath":${jsonString(spec.relativePath)}}""",
        )
    }
}

private class MediaStorePendingVideoDestination(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : ExportDestination {
    private val lock = Any()
    private var published = false
    private var discarded = false

    override fun commitFrom(tempFile: File): Result<Unit> = runCatching {
        synchronized(lock) {
            check(!discarded) { "MediaStore destination already discarded" }
            check(!published) { "MediaStore destination already published" }
        }

        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("unable to open MediaStore output stream")

            val publishValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            val updated = contentResolver.update(uri, publishValues, null, null)
            check(updated > 0) { "unable to publish MediaStore export" }
            synchronized(lock) { published = true }
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(uri, null, null) }
            synchronized(lock) { discarded = true }
            throw error
        }
    }

    override fun discard(): Result<Unit> = runCatching {
        val shouldDelete = synchronized(lock) {
            if (published || discarded) {
                false
            } else {
                discarded = true
                true
            }
        }
        if (shouldDelete) contentResolver.delete(uri, null, null)
    }
}
