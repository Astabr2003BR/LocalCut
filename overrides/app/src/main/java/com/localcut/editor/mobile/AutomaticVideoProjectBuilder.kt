package com.localcut.editor.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import java.io.File
import java.util.UUID

/** Shared preparation for the automatic studio and Android integration tests. */
internal object AutomaticVideoProjectBuilder {
    fun build(
        context: Context,
        plan: AutomaticVideoPlan,
        selected: List<String>,
        chosenAudio: String?,
        audioMime: String?,
        dir: File,
        checkCancelled: () -> Unit = {},
    ): Project {
        require(plan.scenes.all { it.imageIndex in selected.indices }) { "Imagem indisponível" }
        checkCancelled()
        check(dir.mkdirs())
        var completed = false
        try {
            val assets = plan.scenes.map { it.imageIndex }.distinct().map { index ->
                checkCancelled()
                val uri = Uri.parse(selected[index])
                if (context.contentResolver.getType(uri) == "video/mp4") {
                    val file = File(dir, "video-$index.mp4")
                    val duration = prepareVideo(context, uri, file, checkCancelled)
                    require(plan.scenes.filter { it.imageIndex == index }.all { it.sourceOutUs <= duration }) {
                        "Cena excede a duração do vídeo"
                    }
                    return@map Asset(id = "image-$index", type = AssetType.VIDEO, uri = Uri.fromFile(file).toString(),
                        sourceDurationUs = duration, sizeBytes = file.length(), displayName = file.name,
                        mimeType = "video/mp4", fingerprint = file.name + "-" + file.length())
                }
                require(plan.scenes.filter { it.imageIndex == index }.all { it.sourceInUs == 0L }) { "Início de trecho só é permitido em vídeo" }
                val file = File(dir, "image-$index.png")
                prepareImage(context, uri, plan, file, checkCancelled)
                Asset(id = "image-$index", type = AssetType.IMAGE, uri = Uri.fromFile(file).toString(),
                    sourceDurationUs = null, sizeBytes = file.length(), displayName = file.name,
                    mimeType = "image/png", fingerprint = "${file.name}-${file.length()}")
            }.toMutableList()
            val clips = plan.scenes.mapIndexed { index, scene ->
                Clip(id = "scene-$index", assetId = "image-${scene.imageIndex}", startUs = scene.startUs,
                    durationUs = scene.durationUs, sourceInUs = scene.sourceInUs, sourceOutUs = scene.sourceOutUs)
            }
            var audioClip: Clip? = null
            if (chosenAudio != null) {
                val uri = Uri.parse(chosenAudio)
                val mime = (audioMime ?: context.contentResolver.getType(uri))
                    ?.takeIf { it.startsWith("audio/") } ?: error("Áudio inválido")
                val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                val file = File(dir, "audio.$extension")
                (context.contentResolver.openInputStream(uri) ?: error("Áudio indisponível")).use { input ->
                    file.outputStream().use { out ->
                        copyStudioMedia(input, out, checkCancelled, availableBytes = { dir.usableSpace })
                    }
                }
                checkCancelled()
                val retriever = MediaMetadataRetriever()
                val duration = try {
                    retriever.setDataSource(file.absolutePath)
                    check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "Arquivo sem trilha de áudio" }
                    val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Duração do áudio desconhecida")
                    check(ms in 1L..Long.MAX_VALUE / 1000L)
                    ms * 1000L
                } finally { retriever.release() }
                checkCancelled()
                assets += Asset(id = "audio", type = AssetType.AUDIO, uri = Uri.fromFile(file).toString(),
                    sourceDurationUs = duration, sizeBytes = file.length(), displayName = file.name,
                    mimeType = mime, fingerprint = "${file.name}-${file.length()}")
                val end = minOf(duration, plan.durationUs)
                audioClip = Clip(id = "audio-clip", assetId = "audio", startUs = 0L, durationUs = end, sourceInUs = 0L, sourceOutUs = end)
            }
            checkCancelled()
            val base = Project.create("Estúdio automático", plan.width, plan.height, 30, id = UUID.randomUUID().toString())
            val visual = base.tracks.first { it.type == TrackType.VISUAL }.id
            val sound = base.tracks.first { it.type == TrackType.AUDIO }.id
            val project = base.copy(assets = assets, tracks = base.tracks.map {
                when (it.id) {
                    visual -> it.copy(clips = clips)
                    sound -> it.copy(clips = listOfNotNull(audioClip))
                    else -> it
                }
            })
            completed = true
            return project
        } finally {
            // Only clean the directory created by this invocation; never an existing project.
            if (!completed) dir.deleteRecursively()
        }
    }

    private fun prepareVideo(context: Context, uri: Uri, file: File, checkCancelled: () -> Unit): Long {
        (context.contentResolver.openInputStream(uri) ?: error("Vídeo indisponível")).use { input ->
            file.outputStream().use { output ->
                copyStudioMedia(input, output, checkCancelled, availableBytes = { file.parentFile!!.usableSpace })
            }
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Arquivo sem vídeo" }
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Duração do vídeo desconhecida")
            require(ms in 1L..Long.MAX_VALUE / 1000L)
            checkCancelled()
            return ms * 1000L
        } finally { retriever.release() }
    }

    private fun prepareImage(context: Context, uri: Uri, plan: AutomaticVideoPlan, file: File, checkCancelled: () -> Unit) {
        val source = File(file.parentFile!!, file.name + ".source")
        try {
            (context.contentResolver.openInputStream(uri) ?: error("Imagem indisponível")).use { input ->
                source.outputStream().use { output ->
                    copyStudioMedia(input, output, checkCancelled, availableBytes = { source.parentFile!!.usableSpace })
                }
            }
            checkCancelled()
            decodeImage(source, plan, file, checkCancelled)
        } finally { source.delete() }
    }

    private fun decodeImage(source: File, plan: AutomaticVideoPlan, file: File, checkCancelled: () -> Unit) {
        val input = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            val (w, h) = TikTokCarouselPolicy.fittedSize(info.size.width, info.size.height, plan.width, plan.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            checkCancelled()
            val output = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
            try {
                val (w, h) = TikTokCarouselPolicy.fittedSize(input.width, input.height, plan.width, plan.height)
                val left = (plan.width - w) / 2
                val top = (plan.height - h) / 2
                Canvas(output).apply { drawColor(Color.BLACK); drawBitmap(input, null, Rect(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                checkCancelled()
                file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                checkCancelled()
            } finally { output.recycle() }
        } finally { input.recycle() }
    }

}
