package com.localcut.editor.mobile

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.SafExportDestination
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@UnstableApi
class TikTokCarouselActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pages by mutableStateOf(emptyList<String>())
    private var format by mutableStateOf(CarouselFormat.MANGA)
    private var selected by mutableStateOf(0)
    private var seconds by mutableStateOf(5)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var videoPickerOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 29) {
            android.widget.Toast.makeText(this, "Carrossel requer Android 10 ou superior.", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pages = savedInstanceState?.getStringArrayList("pages")?.toList().orEmpty()
        format = CarouselFormat.entries.firstOrNull { it.name == savedInstanceState?.getString("format") } ?: CarouselFormat.MANGA
        selected = (savedInstanceState?.getInt("selected") ?: 0).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        seconds = (savedInstanceState?.getInt("seconds") ?: 5).coerceIn(2, 15)
        videoPickerOpen = savedInstanceState?.getBoolean("videoPickerOpen") ?: false

        val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (busy || uris.isEmpty()) return@registerForActivityResult
            val valid = uris.filter { it.scheme == "content" }
            val combined = TikTokCarouselPolicy.add(pages, valid.map(Uri::toString))
            valid.filter { it.toString() in combined }.forEach { uri ->
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            pages = combined
            message = "Páginas: ${pages.size}/35. Confira a ordem antes de exportar."
        }
        val video = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            videoPickerOpen = false
            if (uri != null && !busy && pages.isNotEmpty()) exportVideo(uri)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BackHandler(enabled = busy) { message = "Exportação em andamento. Aguarde a conclusão." }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                    Text("Carrossel TikTok", style = MaterialTheme.typography.headlineSmall)
                    Text("Fotos em ordem ou vídeo de páginas. O projeto do editor fica intacto.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { finish() }, enabled = !busy) { Text("Voltar") }
                        Button(onClick = { pick.launch(arrayOf("image/*")) }, enabled = !busy && pages.size < TikTokCarouselPolicy.MAX_PAGES) { Text("Adicionar fotos") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = format == CarouselFormat.MANGA, onClick = { format = CarouselFormat.MANGA }, enabled = !busy, label = { Text("4:5") })
                        FilterChip(selected = format == CarouselFormat.VERTICAL, onClick = { format = CarouselFormat.VERTICAL }, enabled = !busy, label = { Text("9:16") })
                        Text("${pages.size}/35")
                    }
                    val current = pages.getOrNull(selected)
                    if (current != null) {
                        val preview by produceState<Bitmap?>(null, current) {
                            value = null
                            value = withContext(Dispatchers.IO) {
                                try { decode(Uri.parse(current), 420, 480) }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { null }
                            }
                        }
                        preview?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), contentDescription = "Prévia da página ${selected + 1}",
                                modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
                        } ?: Text("Prévia indisponível ou carregando.")
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(pages, key = { _, page -> page }) { index, page ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { selected = index }, modifier = Modifier.weight(1f)) {
                                    Text(if (selected == index) "Página ${index + 1} • prévia" else "Página ${index + 1}")
                                }
                                TextButton(enabled = !busy && index > 0, onClick = {
                                    pages = TikTokCarouselPolicy.move(pages, index, index - 1); selected = index - 1
                                }) { Text("↑") }
                                TextButton(enabled = !busy && index < pages.lastIndex, onClick = {
                                    pages = TikTokCarouselPolicy.move(pages, index, index + 1); selected = index + 1
                                }) { Text("↓") }
                                TextButton(enabled = !busy, onClick = {
                                    pages = pages.filterNot { it == page }; selected = selected.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                                }) { Text("Remover") }
                            }
                        }
                    }
                    Text("Duração por página no vídeo: ${seconds}s")
                    Slider(value = seconds.toFloat(), onValueChange = { seconds = it.toInt().coerceIn(2, 15) }, valueRange = 2f..15f, steps = 12, enabled = !busy)
                    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick = { exportPhotos() }, enabled = !busy && pages.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar fotos para galeria")
                    }
                    OutlinedButton(onClick = {
                        videoPickerOpen = true
                        video.launch("LocalCut-Carrossel-${System.currentTimeMillis()}.mp4")
                    }, enabled = !busy && !videoPickerOpen && pages.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar vídeo MP4")
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("pages", ArrayList(pages))
        outState.putString("format", format.name)
        outState.putInt("selected", selected)
        outState.putInt("seconds", seconds)
        outState.putBoolean("videoPickerOpen", videoPickerOpen)
        super.onSaveInstanceState(outState)
    }

    private fun decode(uri: Uri, width: Int, height: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            val (w, h) = TikTokCarouselPolicy.fittedSize(info.size.width, info.size.height, width, height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun writePage(uri: Uri, chosen: CarouselFormat, output: java.io.OutputStream) {
        val source = decode(uri, chosen.width, chosen.height)
        try {
            val canvasBitmap = Bitmap.createBitmap(chosen.width, chosen.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(canvasBitmap)
                canvas.drawColor(AndroidColor.BLACK)
                val (w, h) = TikTokCarouselPolicy.fittedSize(source.width, source.height, chosen.width, chosen.height)
                val x = (chosen.width - w) / 2
                val y = (chosen.height - h) / 2
                canvas.drawBitmap(source, null, Rect(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                check(canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Falha ao gravar imagem" }
            } finally { canvasBitmap.recycle() }
        } finally { source.recycle() }
    }

    private fun exportPhotos() {
        if (busy || pages.isEmpty()) return
        val snapshot = pages.toList()
        val chosen = format
        val batch = "LocalCut-Carrossel-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        busy = true
        message = "Preparando fotos…"
        scope.launch {
            val created = mutableListOf<Uri>()
            try {
                withContext(Dispatchers.IO) {
                    snapshot.forEachIndexed { index, page ->
                        ensureActive()
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Pagina-${(index + 1).toString().padStart(2, '0')}.png")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$batch")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: error("Não foi possível criar imagem na galeria")
                        created.add(uri)
                        (contentResolver.openOutputStream(uri) ?: error("Não foi possível abrir imagem")).use {
                            writePage(Uri.parse(page), chosen, it)
                        }
                    }
                    ensureActive()
                    created.forEach { uri ->
                        check(contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
                    }
                }
                message = "${created.size} fotos salvas em Pictures/$batch. Selecione em ordem no TikTok."
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    created.forEach { uri -> runCatching { contentResolver.delete(uri, null, null) } }
                }
                if (e is CancellationException) throw e
                message = "Falha ao exportar fotos: ${e.message ?: "verifique as imagens"}"
            } finally { busy = false }
        }
    }

    private fun exportVideo(destination: Uri) {
        val snapshot = pages.toList()
        val chosen = format
        val duration = TikTokCarouselPolicy.durationUs(seconds)
        busy = true
        message = "Preparando vídeo…"
        val dir = File(cacheDir, "carousel-${UUID.randomUUID()}")
        val target = SafExportDestination(contentResolver, destination)
        scope.launch {
            var submitted = false
            var terminal = false
            try {
                val project = withContext(Dispatchers.IO) {
                    check(dir.mkdirs())
                    val assets = snapshot.mapIndexed { index, page ->
                        ensureActive()
                        val file = File(dir, "page-$index.png")
                        file.outputStream().use { writePage(Uri.parse(page), chosen, it) }
                        Asset(id = "page-$index", type = AssetType.IMAGE, uri = Uri.fromFile(file).toString(),
                            sourceDurationUs = null, sizeBytes = file.length(), displayName = file.name,
                            mimeType = "image/png", fingerprint = "${file.name}-${file.length()}")
                    }
                    val base = Project.create("Carrossel", chosen.width, chosen.height, 30, id = UUID.randomUUID().toString())
                    val clips = assets.mapIndexed { index, asset ->
                        Clip(id = "clip-$index", assetId = asset.id, startUs = index * duration,
                            durationUs = duration, sourceInUs = 0L, sourceOutUs = duration)
                    }
                    base.copy(assets = assets, tracks = base.tracks.map { if (it.id == "visual-1") it.copy(clips = clips) else it })
                }
                val controller = LocalCutProcessRuntime.get(this@TikTokCarouselActivity).exportController
                submitted = true
                controller.export(project, target, ExportProfile.defaultFor(project)).collect { event ->
                    when (event) {
                        ExportEvent.Started -> message = "Exportando vídeo…"
                        is ExportEvent.Progress -> message = "Exportando vídeo: ${event.percent}%"
                        ExportEvent.Completed -> { terminal = true; message = "Vídeo MP4 salvo. Publique como vídeo normal." }
                        ExportEvent.Cancelled -> { terminal = true; message = "Exportação cancelada." }
                        is ExportEvent.Failed -> { terminal = true; message = "Falha no vídeo: ${event.message}" }
                    }
                }
            } catch (e: Exception) {
                if (!submitted) withContext(NonCancellable + Dispatchers.IO) { target.discard() }
                if (e is CancellationException) throw e
                message = "Falha ao exportar vídeo: ${e.message ?: "verifique as imagens"}"
            } finally {
                // Do not delete decoder inputs while an asynchronously cancelled renderer may still be reading them.
                if (!submitted || terminal) withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
                busy = false
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
