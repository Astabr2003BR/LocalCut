package com.localcut.editor.mobile

import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Read-only package preflight. Never extracts paths or changes a project. */
internal object PortableStudioPackage {
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
    const val MAX_ASSET_BYTES = 128L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 65_536L
    const val MAX_ASSETS = 128
    private val idPattern = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val imageTypes = setOf("image/png", "image/jpeg", "image/webp")
    private val audioTypes = setOf("audio/mpeg", "audio/wav", "audio/mp4", "audio/aac", "audio/ogg", "audio/flac")

    data class Media(val id: String, val mime: String, val size: Long, val sha256: String) {
        val path: String get() = "media/$id"
    }
    data class Verified(val recipe: String, val images: List<Media>, val audio: Media?, val plan: AutomaticVideoPlan)

    fun verify(file: File, checkCancelled: () -> Unit = {}): Verified {
        require(file.isFile && file.length() in 22L..MAX_ARCHIVE_BYTES) { "PACKAGE_ARCHIVE_SIZE" }
        checkCancelled()
        val expectedCount = preflightDirectory(file)
        return ZipFile(file).use { zip ->
            require(zip.size() == expectedCount && zip.size() in 2..MAX_ASSETS + 1) { "PACKAGE_ENTRY_COUNT" }
            val entries = linkedMapOf<String, ZipEntry>()
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                checkCancelled()
                val entry = iterator.nextElement()
                require(!entry.isDirectory && (entry.name == "manifest.json" ||
                    (entry.name.startsWith("media/") && idPattern.matches(entry.name.removePrefix("media/"))))) { "PACKAGE_PATH_INVALID" }
                require(entries.put(entry.name, entry) == null) { "PACKAGE_DUPLICATE_ENTRY" }
                require(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED) { "PACKAGE_COMPRESSION_UNSUPPORTED" }
                require(entry.compressedSize >= 0 && entry.compressedSize <= MAX_ARCHIVE_BYTES && entry.crc >= 0) { "PACKAGE_ENTRY_METADATA" }
            }
            val manifest = entries["manifest.json"] ?: error("PACKAGE_MANIFEST_MISSING")
            require(manifest.size in 1L..MAX_MANIFEST_BYTES) { "PACKAGE_MANIFEST_SIZE" }
            val buffer = ByteArrayOutputStream()
            readChecked(zip, manifest, manifest.size, checkCancelled) { bytes, count -> buffer.write(bytes, 0, count) }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
            val verified = parseManifest(text)
            val assets = verified.images + listOfNotNull(verified.audio)
            require(entries.keys == (assets.map { it.path } + "manifest.json").toSet()) { "PACKAGE_ASSET_SET_MISMATCH" }
            require(assets.sumOf { it.size } <= MAX_TOTAL_BYTES) { "PACKAGE_TOTAL_SIZE" }
            for (asset in assets) {
                val entry = entries.getValue(asset.path)
                require(entry.size == asset.size) { "PACKAGE_ASSET_SIZE_MISMATCH" }
                val digest = MessageDigest.getInstance("SHA-256")
                readChecked(zip, entry, asset.size, checkCancelled) { bytes, count -> digest.update(bytes, 0, count) }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual == asset.sha256) { "PACKAGE_ASSET_HASH_MISMATCH" }
            }
            verified
        }
    }

    // Restrict v1 to single-disk, non-ZIP64, no archive comment, bounded central directory.
    // This runs before ZipFile constructs an in-memory directory.
    private fun preflightDirectory(file: File): Int = RandomAccessFile(file, "r").use { input ->
        val end = ByteArray(22)
        input.seek(input.length() - end.size)
        input.readFully(end)
        fun u16(i: Int): Int = (end[i].toInt() and 255) or ((end[i + 1].toInt() and 255) shl 8)
        fun u32(i: Int): Long = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
        require(u32(0) == 0x06054b50L && u16(4) == 0 && u16(6) == 0 && u16(20) == 0) { "PACKAGE_ZIP_END_INVALID" }
        val count = u16(10)
        require(count in 2..MAX_ASSETS + 1 && u16(8) == count) { "PACKAGE_ENTRY_COUNT" }
        val size = u32(12)
        val offset = u32(16)
        require(size in 1L..1_048_576L && offset > 0 && offset + size == input.length() - 22) { "PACKAGE_DIRECTORY_INVALID" }
        count
    }

    private fun readChecked(zip: ZipFile, entry: ZipEntry, limit: Long, checkCancelled: () -> Unit,
                            consume: (ByteArray, Int) -> Unit) {
        val crc = CRC32()
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        zip.getInputStream(entry).use { input ->
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) error("PACKAGE_READ_STALLED")
                total += count
                require(total <= limit) { "PACKAGE_ASSET_SIZE_MISMATCH" }
                crc.update(buffer, 0, count)
                consume(buffer, count)
            }
        }
        require(total == limit && crc.value == entry.crc) { "PACKAGE_ENTRY_INTEGRITY" }
    }

    private fun parseManifest(text: String): Verified = JsonReader(StringReader(text)).use { reader ->
        reader.isLenient = false
        val fields = mutableSetOf<String>()
        var version: Long? = null
        var recipe: String? = null
        var images: List<Media>? = null
        var audio: Media? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(fields.add(name)) { "PACKAGE_DUPLICATE_FIELD" }
            when (name) {
                "version" -> version = integer(reader)
                "recipe" -> recipe = string(reader)
                "images" -> {
                    val list = mutableListOf<Media>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(list.size < MAX_ASSETS) { "PACKAGE_ENTRY_COUNT" }
                        list += media(reader, imageTypes)
                    }
                    reader.endArray()
                    images = list.toList()
                }
                "audio" -> audio = media(reader, audioTypes)
                else -> error("PACKAGE_UNKNOWN_FIELD")
            }
        }
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT && version == 1L) { "PACKAGE_VERSION_OR_TRAILING_DATA" }
        val ordered = requireNotNull(images) { "PACKAGE_IMAGES_MISSING" }
        val assets = ordered + listOfNotNull(audio)
        require(ordered.isNotEmpty() && assets.size <= MAX_ASSETS && assets.map { it.id }.toSet().size == assets.size) { "PACKAGE_ASSET_IDS" }
        val rawRecipe = requireNotNull(recipe) { "PACKAGE_RECIPE_MISSING" }
        Verified(rawRecipe, ordered, audio, AutomaticVideoRecipe.parse(rawRecipe, ordered.size))
    }

    private fun media(reader: JsonReader, allowed: Set<String>): Media {
        val fields = mutableSetOf<String>()
        var id: String? = null
        var mime: String? = null
        var size: Long? = null
        var hash: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(fields.add(key)) { "PACKAGE_DUPLICATE_FIELD" }
            when (key) {
                "id" -> id = string(reader)
                "mime" -> mime = string(reader)
                "bytes" -> size = integer(reader)
                "sha256" -> hash = string(reader)
                else -> error("PACKAGE_UNKNOWN_FIELD")
            }
        }
        reader.endObject()
        val i = requireNotNull(id)
        val m = requireNotNull(mime)
        val n = requireNotNull(size)
        val h = requireNotNull(hash)
        require(idPattern.matches(i) && m in allowed && n in 1L..MAX_ASSET_BYTES && hashPattern.matches(h)) { "PACKAGE_ASSET_INVALID" }
        return Media(i, m, n, h)
    }

    private fun string(reader: JsonReader): String {
        require(reader.peek() == JsonToken.STRING) { "PACKAGE_FIELD_TYPE" }
        return reader.nextString()
    }

    private fun integer(reader: JsonReader): Long {
        require(reader.peek() == JsonToken.NUMBER) { "PACKAGE_FIELD_TYPE" }
        return reader.nextString().toLongOrNull() ?: error("PACKAGE_FIELD_TYPE")
    }
}
