package com.localcut.editor.mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.SafExportDestination
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Offline, opt-in creation of a separate image/audio composition. No editor project is mutated. */
@UnstableApi
class AutomaticVideoActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var images by mutableStateOf(emptyList<String>())
    private var audio by mutableStateOf<String?>(null)
    private var recipe by mutableStateOf(AutomaticVideoRecipe.EXAMPLE)
    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var choosingOutput by mutableStateOf(false)
    private var approvedPlan: AutomaticVideoPlan? = null
    private var showApproval by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 29) { finish(); return }
        images = savedInstanceState?.getStringArrayList("images")?.toList().orEmpty()
        audio = savedInstanceState?.getString("audio")
        recipe = savedInstanceState?.getString("recipe") ?: AutomaticVideoRecipe.EXAMPLE
        choosingOutput = savedInstanceState?.getBoolean("choosing") ?: false
        val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!busy && !choosingOutput && uris.isNotEmpty()) {
                val valid = uris.filter { it.scheme == "content" && contentResolver.getType(it)?.startsWith("image/") == true }
                if (valid.size != uris.size) message = "Seleção contém mídia não reconhecida; nada alterado."
                else {
                    valid.forEach(::persist)
                    images = (images + valid.map(Uri::toString)).distinct()
                    message = "${images.size} imagens selecionadas. A numeração segue a lista abaixo."
                }
            }
        }
        val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (!busy && !choosingOutput && uri != null) {
                if (uri.scheme == "content" && contentResolver.getType(uri)?.startsWith("audio/") == true) {
                    persist(uri)
                    audio = uri.toString()
                    message = "Áudio selecionado. Será cortado ao final do vídeo, se for mais longo."
                } else message = "Selecione um arquivo de áudio válido."
            }
        }
        val output = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            choosingOutput = false
            val plan = approvedPlan ?: runCatching { AutomaticVideoRecipe.parse(recipe, images.size) }.getOrNull()
            approvedPlan = null
            if (uri != null && !busy) {
                if (plan != null) export(plan, uri)
                else { SafExportDestination(contentResolver, uri).discard(); message = "Roteiro inválido. Nenhum vídeo criado." }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (showApproval) AlertDialog(
                    onDismissRequest = { showApproval = false; approvedPlan = null },
                    title = { Text("Criar vídeo local") },
                    text = { Text("Criar um novo MP4 com ${approvedPlan?.scenes?.size ?: 0} cenas, ${approvedPlan?.durationUs?.div(1000) ?: 0} ms, ${if (audio == null) "sem áudio" else "com áudio local"}. O projeto do editor não será alterado.") },
                    confirmButton = { TextButton(onClick = {
                        showApproval = false
                        choosingOutput = true
                        output.launch("LocalCut-Automatico-${System.currentTimeMillis()}.mp4")
                    }) { Text("Escolher destino e criar") } },
                    dismissButton = { TextButton(onClick = { showApproval = false; approvedPlan = null }) { Text("Cancelar") } },
                )
                BackHandler(enabled = busy) { message = "Exportação em andamento. Mantenha esta tela aberta." }
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estúdio automático", style = MaterialTheme.typography.headlineSmall)
                    Text("Vídeo offline a partir de imagens, roteiro JSON e áudio opcional. Sem IA interna ou créditos.")
                    val editable = !busy && !choosingOutput
                    TextButton(onClick = { finish() }, enabled = editable) { Text("Voltar") }
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = editable) { Text("Adicionar imagens locais") }
                    images.forEachIndexed { index, source ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${index + 1}. ${sourceName(source)}", modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(enabled = editable && index > 0, onClick = {
                                images = images.toMutableList().apply { add(index - 1, removeAt(index)) }
                            }) { Text("↑") }
                            TextButton(enabled = editable, onClick = { images = images.filterIndexed { i, _ -> i != index } }) { Text("Remover") }
                        }
                    }
                    Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, enabled = editable) { Text(if (audio == null) "Adicionar áudio (opcional)" else "Trocar áudio") }
                    audio?.let { Text(sourceName(it)); TextButton(onClick = { audio = null }, enabled = editable) { Text("Remover áudio") } }
                    Text("Cole o roteiro do GPT. image = número da imagem; durationMs = duração em milissegundos. Formatos: 9:16, 4:5, 16:9, 1:1.")
                    OutlinedTextField(value = recipe, onValueChange = {
                        if (it.length <= AutomaticVideoRecipe.MAX_CHARS) recipe = it
                        else message = "Roteiro excede o limite técnico de texto."
                    }, label = { Text("Roteiro JSON") }, modifier = Modifier.fillMaxWidth(), enabled = editable, minLines = 4, maxLines = 8)
                    TextButton(onClick = { recipe = AutomaticVideoRecipe.EXAMPLE }, enabled = editable) { Text("Restaurar exemplo") }
                    Button(onClick = {
                        try {
                            approvedPlan = AutomaticVideoRecipe.parse(recipe, images.size)
                            showApproval = true
                        } catch (e: Exception) { message = "Roteiro inválido: ${e.message ?: "confira o JSON"}" }
                    }, enabled = editable && images.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Validar e criar MP4") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotEmpty()) Text(message)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("images", ArrayList(images))
        outState.putString("audio", audio)
        outState.putString("recipe", recipe)
        outState.putBoolean("choosing", choosingOutput)
        super.onSaveInstanceState(outState)
    }

    private fun persist(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun sourceName(source: String): String =
        runCatching {
            contentResolver.query(Uri.parse(source), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Mídia local"

    private fun prepareImage(uri: Uri, plan: AutomaticVideoPlan, file: File) {
        val input = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            val (w, h) = TikTokCarouselPolicy.fittedSize(info.size.width, info.size.height, plan.width, plan.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val output = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
            try {
                val (w, h) = TikTokCarouselPolicy.fittedSize(input.width, input.height, plan.width, plan.height)
                val left = (plan.width - w) / 2
                val top = (plan.height - h) / 2
                Canvas(output).apply { drawColor(Color.BLACK); drawBitmap(input, null, Rect(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { output.recycle() }
        } finally { input.recycle() }
    }

    private fun export(plan: AutomaticVideoPlan, destination: Uri) {
        val selected = images.toList()
        val chosenAudio = audio
        val dir = File(cacheDir, "automatic-${UUID.randomUUID()}")
        val target = SafExportDestination(contentResolver, destination)
        busy = true
        message = "Preparando mídias..."
        scope.launch {
            var submitted = false
            var terminal = false
            try {
                val project = withContext(Dispatchers.IO) {
                    check(dir.mkdirs())
                    val assets = plan.scenes.map { it.imageIndex }.distinct().map { index ->
                        ensureActive()
                        val file = File(dir, "image-$index.png")
                        prepareImage(Uri.parse(selected[index]), plan, file)
                        Asset(id = "image-$index", type = AssetType.IMAGE, uri = Uri.fromFile(file).toString(),
                            sourceDurationUs = null, sizeBytes = file.length(), displayName = file.name,
                            mimeType = "image/png", fingerprint = "${file.name}-${file.length()}")
                    }.toMutableList()
                    val clips = plan.scenes.mapIndexed { index, scene ->
                        Clip(id = "scene-$index", assetId = "image-${scene.imageIndex}", startUs = scene.startUs,
                            durationUs = scene.durationUs, sourceInUs = 0L, sourceOutUs = scene.durationUs)
                    }
                    var audioClip: Clip? = null
                    if (chosenAudio != null) {
                        val uri = Uri.parse(chosenAudio)
                        val mime = contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: error("Áudio inválido")
                        val retriever = MediaMetadataRetriever()
                        val duration = try {
                            retriever.setDataSource(this@AutomaticVideoActivity, uri)
                            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "Arquivo sem trilha de áudio" }
                            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Duração do áudio desconhecida")
                            check(ms in 1L..Long.MAX_VALUE / 1000L)
                            ms * 1000L
                        } finally { retriever.release() }
                        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                        val file = File(dir, "audio.$extension")
                        (contentResolver.openInputStream(uri) ?: error("Áudio indisponível")).use { input ->
                            file.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    out.write(buffer, 0, size)
                                }
                            }
                        }
                        check(file.length() > 0)
                        assets += Asset(id = "audio", type = AssetType.AUDIO, uri = Uri.fromFile(file).toString(),
                            sourceDurationUs = duration, sizeBytes = file.length(), displayName = file.name,
                            mimeType = mime, fingerprint = "${file.name}-${file.length()}")
                        val end = minOf(duration, plan.durationUs)
                        audioClip = Clip(id = "audio-clip", assetId = "audio", startUs = 0L, durationUs = end, sourceInUs = 0L, sourceOutUs = end)
                    }
                    val base = Project.create("Estúdio automático", plan.width, plan.height, 30, id = UUID.randomUUID().toString())
                    val visual = base.tracks.first { it.type == TrackType.VISUAL }.id
                    val sound = base.tracks.first { it.type == TrackType.AUDIO }.id
                    base.copy(assets = assets, tracks = base.tracks.map {
                        when (it.id) {
                            visual -> it.copy(clips = clips)
                            sound -> it.copy(clips = listOfNotNull(audioClip))
                            else -> it
                        }
                    })
                }
                val controller = LocalCutProcessRuntime.get(this@AutomaticVideoActivity).exportController
                submitted = true
                controller.export(project, target, ExportProfile.defaultFor(project)).collect { event ->
                    when (event) {
                        ExportEvent.Started -> message = "Renderizando..."
                        is ExportEvent.Progress -> message = "Renderizando: ${event.percent}%"
                        ExportEvent.Completed -> { terminal = true; message = "MP4 salvo no destino selecionado. Confira a reprodução." }
                        ExportEvent.Cancelled -> { terminal = true; message = "Exportação cancelada." }
                        is ExportEvent.Failed -> { terminal = true; message = "Falha: ${event.message}" }
                    }
                }
            } catch (e: Exception) {
                if (!submitted) withContext(NonCancellable + Dispatchers.IO) { target.discard() }
                if (e is CancellationException) throw e
                message = "Não foi possível criar o vídeo: ${e.message ?: "verifique as mídias"}"
            } finally {
                if (!submitted || terminal) withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
                busy = false
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
