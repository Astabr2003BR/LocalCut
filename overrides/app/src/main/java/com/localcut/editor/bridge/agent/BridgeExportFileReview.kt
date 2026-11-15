package com.localcut.editor.bridge.agent

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.security.MessageDigest

fun interface BridgeExportFileReviewer {
    /** Inspects the private rendered file on the export commit worker, never a caller-supplied path. */
    fun inspect(file: File, project: Project): String
    fun inspectWithSamples(file: File, project: Project): BridgeExportInspection =
        BridgeExportInspection(inspect(file, project))
}

data class BridgeExportSample(val requestedUs: Long, val width: Int, val height: Int, val jpeg: ByteArray) {
    init {
        require(requestedUs >= 0L && width in 1..320 && height in 1..320)
        require(jpeg.size in 4..65_536)
        require(jpeg[0].toInt() and 255 == 255 && jpeg[1].toInt() and 255 == 216)
        require(jpeg[jpeg.size - 2].toInt() and 255 == 255 && jpeg.last().toInt() and 255 == 217)
    }
}

data class BridgeExportInspection(val reportJson: String, val sha256: String? = null, val samples: List<BridgeExportSample> = emptyList()) {
    init {
        require(samples.size <= 3)
        require(samples.zipWithNext().all { (a, b) -> a.requestedUs < b.requestedUs })
        require(samples.isEmpty() || sha256?.matches(Regex("[a-f0-9]{64}")) == true)
    }
}

internal data class BridgeExportEvidence(val jobId: String, val projectId: String, val revision: Long,
    val sha256: String, val samples: List<BridgeExportSample>) {
    fun toJson(): String = buildString {
        append("{\"jobId\":"); append(jsonString(jobId))
        append(",\"sourceProjectId\":"); append(jsonString(projectId))
        append(",\"sourceRevision\":"); append(revision)
        append(",\"sha256\":"); append(jsonString(sha256))
        append(",\"source\":\"rendered_mp4\",\"frameAccurate\":false,\"frames\":[")
        samples.forEachIndexed { i, sample ->
            if (i > 0) append(',')
            append("{\"requestedUs\":"); append(sample.requestedUs)
            append(",\"width\":"); append(sample.width)
            append(",\"height\":"); append(sample.height)
            append(",\"mimeType\":\"image/jpeg\",\"base64\":")
            append(jsonString(Base64.getEncoder().encodeToString(sample.jpeg))); append('}')
        }
        append("]}")
    }
}

/** Metadata and sampled decode only, not visual quality, complete decode or A/V sync certification. */
object AndroidBridgeExportFileReviewer : BridgeExportFileReviewer {
    override fun inspect(file: File, project: Project): String = inspectWithSamples(file, project).reportJson

    override fun inspectWithSamples(file: File, project: Project): BridgeExportInspection {
        require(file.isFile && file.length() > 0L) { "empty rendered file" }
        val bytes = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            var count = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                digest.update(buffer, 0, n)
                count += n
            }
            check(count == bytes && file.length() == bytes) { "rendered file changed during inspection" }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val extractor = MediaExtractor()
        var videoTracks = 0
        var audioTracks = 0
        var videoMime: String? = null
        var audioMime: String? = null
        var videoDurationUs: Long? = null
        var audioDurationUs: Long? = null
        var audioSampleRate: Int? = null
        var audioChannels: Int? = null
        try {
            extractor.setDataSource(file.absolutePath)
            require(extractor.trackCount in 1..64) { "unsupported track count" }
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> {
                        videoTracks++
                        if (videoMime == null) {
                            videoMime = mime
                            videoDurationUs = format.longOrNull(MediaFormat.KEY_DURATION)
                        }
                    }
                    mime.startsWith("audio/") -> {
                        audioTracks++
                        if (audioMime == null) {
                            audioMime = mime
                            audioDurationUs = format.longOrNull(MediaFormat.KEY_DURATION)
                            audioSampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                            audioChannels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        } finally { extractor.release() }
        val retriever = MediaMetadataRetriever()
        val expectedUs = TimelineMath.durationUs(project)
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("missing export duration")
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: error("missing video width")
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: error("missing video height")
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            require(durationMs in 1..(Long.MAX_VALUE / 1000L) && width > 0 && height > 0 && videoTracks > 0) {
                "invalid rendered video metadata"
            }
            val durationUs = durationMs * 1000L
            val displayWidth = if (rotation == 90 || rotation == 270) height else width
            val displayHeight = if (rotation == 90 || rotation == 270) width else height
            val toleranceUs = maxOf(150_000L, 2_000_000L / project.fps.coerceAtLeast(1))
            val durationMatches = maxOf(expectedUs, durationUs) - minOf(expectedUs, durationUs) <= toleranceUs
            val resolutionMatches = displayWidth == project.width && displayHeight == project.height
            // Decode a few bounded-size frames. Native decoder may still allocate codec buffers;
            // no full-resolution bitmap or image data is retained in the job registry.
            val positions = listOf(0L, durationUs / 2L, (durationUs - 1_000L).coerceAtLeast(0L)).distinct()
            var decoded = 0
            val darkSamplePositions = mutableListOf<Long>()
            val samples = mutableListOf<BridgeExportSample>()
            if (Build.VERSION.SDK_INT >= 27) {
                for (position in positions) {
                    val frame = runCatching {
                        retriever.getScaledFrameAtTime(position, MediaMetadataRetriever.OPTION_CLOSEST, 320, 320)
                    }.getOrNull()
                    if (frame != null) {
                        try {
                            if (frame.width > 0 && frame.height > 0) {
                                decoded++
                                if (isNearBlackSample(frame)) darkSamplePositions.add(position)
                                runCatching { encodeSample(frame, position) }.getOrNull()?.let(samples::add)
                            }
                        } finally { frame.recycle() }
                    }
                }
            }
            val sampleStatus = if (Build.VERSION.SDK_INT < 27) "unsupported" else if (decoded == positions.size) "decoded" else "incomplete"
            val status = if (durationMatches && resolutionMatches && sampleStatus == "decoded" && darkSamplePositions.isEmpty()) "technical_checks_passed" else "needs_review"
            val report = buildString {
                append("{\"status\":"); append(jsonString(status))
                append(",\"scope\":\"rendered_file_metadata_and_sample_decode\"")
                append(",\"bytes\":"); append(bytes)
                append(",\"sha256\":"); append(jsonString(sha))
                append(",\"width\":"); append(displayWidth)
                append(",\"height\":"); append(displayHeight)
                append(",\"durationUs\":"); append(durationUs)
                append(",\"expectedDurationUs\":"); append(expectedUs)
                append(",\"durationToleranceUs\":"); append(toleranceUs)
                append(",\"durationMatches\":"); append(durationMatches)
                append(",\"resolutionMatches\":"); append(resolutionMatches)
                append(",\"videoTracks\":"); append(videoTracks)
                append(",\"audioTracks\":"); append(audioTracks)
                append(",\"videoMime\":"); append(videoMime?.let(::jsonString) ?: "null")
                append(",\"audioMime\":"); append(audioMime?.let(::jsonString) ?: "null")
                append(",\"videoTrackDurationUs\":"); append(videoDurationUs ?: "null")
                append(",\"audioTrackDurationUs\":"); append(audioDurationUs ?: "null")
                append(",\"audioSampleRate\":"); append(audioSampleRate ?: "null")
                append(",\"audioChannels\":"); append(audioChannels ?: "null")
                append(",\"sampleStatus\":"); append(jsonString(sampleStatus))
                append(",\"sampledFrames\":"); append(positions.size)
                append(",\"decodedFrames\":"); append(decoded)
                append(",\"nearBlackSamplePositionsUs\":["); append(darkSamplePositions.joinToString(",")); append("]")
                append(",\"nearBlackHeuristicOnly\":true")
                append(",\"encodedSampleFrames\":"); append(samples.size)
                append(",\"visualQualityVerified\":false,\"audioSyncVerified\":false,\"fullDecodeVerified\":false}")
            }
            return BridgeExportInspection(report, sha, if (samples.size == positions.size) samples.toList() else emptyList())
        } finally { retriever.release() }
    }

    private fun encodeSample(frame: android.graphics.Bitmap, position: Long): BridgeExportSample? {
        if (frame.width !in 1..320 || frame.height !in 1..320) return null
        for (quality in listOf(82, 60, 40)) {
            val bytes = ByteArrayOutputStream().use { out ->
                if (!frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)) return null
                out.toByteArray()
            }
            if (bytes.size <= 65_536) return BridgeExportSample(position, frame.width, frame.height, bytes)
        }
        return null
    }

    /** Sparse RGB sampling: a warning, not proof of an unintended black frame. */
    internal fun isNearBlackSample(frame: android.graphics.Bitmap): Boolean {
        var dark = 0
        var count = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            val pixel = frame.getPixel(x * (frame.width - 1) / 15, y * (frame.height - 1) / 15)
            if (android.graphics.Color.red(pixel) <= 16 &&
                android.graphics.Color.green(pixel) <= 16 &&
                android.graphics.Color.blue(pixel) <= 16) dark++
            count++
        }
        return dark * 100 >= count * 98
    }

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}

/** Only enrich the already object-shaped, trusted target result; no untrusted field interpolation. */
internal fun appendBridgeExportMetadata(resultJson: String, fieldsJson: String): String {
    val base = resultJson.trim()
    require(base.startsWith("{") && base.endsWith("}")) { "invalid export result object" }
    return base.dropLast(1) + (if (base.drop(1).dropLast(1).isBlank()) "" else ",") + fieldsJson + "}"
}
