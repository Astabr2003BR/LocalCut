package com.localcut.editor.mobile

import android.content.Intent
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
import androidx.core.content.FileProvider
import com.localcut.editor.LocalCutProcessRuntime
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
    companion object {
        const val PACKAGE_MIME = "application/vnd.localcut.package"
        internal fun sharedPackageUri(action: String?, sources: List<String?>): Uri? {
            if (action != Intent.ACTION_SEND || sources.size != 1) return null
            val uri = sources.single()?.let(Uri::parse) ?: return null
            return uri.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
        }
    }
    private var sharedPackage by mutableStateOf<Uri?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var images by mutableStateOf(emptyList<String>())
    private var audio by mutableStateOf<String?>(null)
    private var recipe by mutableStateOf(AutomaticVideoRecipe.EXAMPLE)
    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var choosingOutput by mutableStateOf(false)
    private var approvedPlan: AutomaticVideoPlan? = null
    private var showApproval by mutableStateOf(false)
    private var packageDirectory: String? = null
    private var packageAudioSource: String? = null
    private var packageAudioMime: String? = null
    private var showPackageReplace by mutableStateOf(false)
    private var importingPackage by mutableStateOf(false)
    private var packageJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 29) { finish(); return }
        images = savedInstanceState?.getStringArrayList("images")?.toList().orEmpty()
        audio = savedInstanceState?.getString("audio")
        recipe = savedInstanceState?.getString("recipe") ?: AutomaticVideoRecipe.EXAMPLE
        choosingOutput = savedInstanceState?.getBoolean("choosing") ?: false
        packageDirectory = savedInstanceState?.getString("packageDirectory")
        packageAudioSource = savedInstanceState?.getString("packageAudioSource")
        packageAudioMime = savedInstanceState?.getString("packageAudioMime")
        if (savedInstanceState == null) sharedPackage = intent.data?.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
        val packagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !busy && !choosingOutput && uri.scheme == "content") importPackage(uri)
        }
        val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!busy && !choosingOutput && uris.isNotEmpty()) {
                val valid = uris.filter { it.scheme == "content" && runCatching { contentResolver.getType(it)?.let { mime -> mime.startsWith("image/") || mime == "video/mp4" } == true }.getOrDefault(false) }
                if (valid.size != uris.size) message = "Seleção contém mídia não reconhecida; nada alterado."
                else {
                    valid.forEach(::persist)
                    images = (images + valid.map(Uri::toString)).distinct()
                    message = "${images.size} mídias visuais selecionadas. A numeração segue a lista abaixo."
                }
            }
        }
        val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (!busy && !choosingOutput && uri != null) {
                if (uri.scheme == "content" && runCatching { contentResolver.getType(uri)?.startsWith("audio/") == true }.getOrDefault(false)) {
                    persist(uri)
                    audio = uri.toString()
                    message = "Áudio selecionado. Será cortado ao final do vídeo, se for mais longo."
                } else message = "Selecione um arquivo de áudio válido."
            }
        }
        val output = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            choosingOutput = false
            val plan = approvedPlan
            // A recreated Activity must not infer consent from an unapproved draft.
            approvedPlan = null
            if (uri != null && !busy) {
                if (plan != null) export(plan, uri)
                else { message = "Autorização anterior indisponível. Nenhum vídeo renderizado; valide e autorize novamente. O seletor pode ter criado um arquivo vazio." }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                sharedPackage?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { sharedPackage = null },
                        title = { Text("Importar pacote compartilhado") },
                        text = { Text("Validar e abrir este pacote local? Nenhum projeto existente será alterado.") },
                        confirmButton = { TextButton(onClick = { sharedPackage = null; importPackage(uri) }) { Text("Validar e abrir") } },
                        dismissButton = { TextButton(onClick = { sharedPackage = null }) { Text("Cancelar") } },
                    )
                }
                if (showPackageReplace) AlertDialog(
                    onDismissRequest = { showPackageReplace = false },
                    title = { Text("Abrir pacote .localcut") },
                    text = { Text("O pacote substituirá apenas a seleção e o roteiro deste Estúdio. O projeto do editor não será alterado.") },
                    confirmButton = { TextButton(onClick = { showPackageReplace = false; packagePicker.launch(arrayOf("*/*")) }) { Text("Selecionar pacote") } },
                    dismissButton = { TextButton(onClick = { showPackageReplace = false }) { Text("Cancelar") } },
                )
                if (showApproval) AlertDialog(
                    onDismissRequest = { showApproval = false; approvedPlan = null },
                    title = { Text("Criar vídeo local") },
                    text = { Text(approvedPlan?.let { AutomaticVideoApproval.describe(it, audio != null) } ?: "Plano indisponível. Cancele e valide novamente.") },
                    confirmButton = { TextButton(enabled = approvedPlan != null, onClick = {
                        showApproval = false
                        choosingOutput = true
                        output.launch("LocalCut-Automatico-${System.currentTimeMillis()}.mp4")
                    }) { Text("Escolher destino e criar") } },
                    dismissButton = { TextButton(onClick = { showApproval = false; approvedPlan = null }) { Text("Cancelar") } },
                )
                BackHandler(enabled = busy) { message = if (importingPackage) "Use Cancelar importação." else "Exportação em andamento. Mantenha esta tela aberta." }
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Editor IA", style = MaterialTheme.typography.headlineSmall)
                    Text("GPT externo • renderização local Android. Crie um novo vídeo com mídias locais e roteiro JSON, sem alterar o projeto aberto.")
                    Text("MP4 achatado: não recupera automaticamente texto, áudio ou cenário em camadas independentes.", style = MaterialTheme.typography.bodySmall)
                    val editable = !busy && !choosingOutput
                    TextButton(onClick = { finish() }, enabled = editable) { Text("Voltar") }
                    Button(onClick = {
                        if (images.isNotEmpty() || audio != null || recipe != AutomaticVideoRecipe.EXAMPLE) showPackageReplace = true
                        else packagePicker.launch(arrayOf("*/*"))
                    }, enabled = editable) { Text("Abrir pacote .localcut") }
                    if (importingPackage) TextButton(onClick = { packageJob?.cancel() }) { Text("Cancelar importação") }
                    Button(onClick = { imagePicker.launch(arrayOf("image/*", "video/mp4")) }, enabled = editable) { Text("Adicionar imagens ou MP4") }
                    images.forEachIndexed { index, source ->
                        Row(Modifier.fillMaxWidth()) {
                            val name by produceState("Mídia local", source) { value = withContext(Dispatchers.IO) { sourceName(source) } }
                            Text("${index + 1}. $name", modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(enabled = editable && index > 0, onClick = {
                                images = images.toMutableList().apply { add(index - 1, removeAt(index)) }
                            }) { Text("↑") }
                            TextButton(enabled = editable, onClick = { images = images.filterIndexed { i, _ -> i != index } }) { Text("Remover") }
                        }
                    }
                    Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, enabled = editable) { Text(if (audio == null) "Adicionar áudio (opcional)" else "Trocar áudio") }
                    audio?.let { source ->
                        val name by produceState("Áudio local", source) { value = withContext(Dispatchers.IO) { sourceName(source) } }
                        Text(name)
                        TextButton(onClick = { audio = null }, enabled = editable) { Text("Remover áudio") }
                    }
                    Text("Cole o roteiro do GPT. image = número da mídia visual (imagem ou MP4); durationMs = duração em milissegundos. Para vídeo, sourceInMs = início do trecho (opcional). Formatos: 9:16, 4:5, 16:9, 1:1.")
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
        outState.putString("packageDirectory", packageDirectory)
        outState.putString("packageAudioSource", packageAudioSource)
        outState.putString("packageAudioMime", packageAudioMime)
        super.onSaveInstanceState(outState)
    }

    private fun packagePath(name: String): File {
        require(name.matches(Regex("[0-9a-f-]{36}")))
        return File(File(cacheDir, "portable-studio"), name)
    }

    private fun importPackage(uri: Uri) {
        val name = UUID.randomUUID().toString()
        val directory = packagePath(name)
        busy = true
        importingPackage = true
        message = "Validando pacote e mídias locais..."
        packageJob = scope.launch {
            var adopted = false
            try {
                val staged = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    PortableStudioStager.stage(directory,
                        openSource = { contentResolver.openInputStream(uri) ?: error("Arquivo indisponível") },
                        checkCancelled = { context.ensureActive() })
                }
                val authority = "$packageName.remote-media"
                val newImages = staged.images.map { FileProvider.getUriForFile(this@AutomaticVideoActivity, authority, it).toString() }
                val newAudio = staged.audio?.let { FileProvider.getUriForFile(this@AutomaticVideoActivity, authority, it).toString() }
                val previous = packageDirectory
                images = newImages
                audio = newAudio
                recipe = staged.recipe
                packageDirectory = name
                packageAudioSource = newAudio
                packageAudioMime = staged.audioMime
                approvedPlan = null
                adopted = true
                message = "Pacote aberto: ${newImages.size} imagens. Confira o roteiro e escolha Validar e criar MP4."
                if (previous != null) withContext(Dispatchers.IO) { packagePath(previous).deleteRecursively() }
            } catch (e: CancellationException) {
                message = "Importação cancelada."
                throw e
            } catch (e: Exception) {
                message = "Não foi possível abrir o pacote: ${e.message ?: "arquivo inválido"}"
            } finally {
                if (!adopted) withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
                importingPackage = false
                busy = false
                packageJob = null
            }
        }
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
                    val context = currentCoroutineContext()
                    AutomaticVideoProjectBuilder.build(
                        this@AutomaticVideoActivity, plan, selected, chosenAudio,
                        if (chosenAudio == packageAudioSource) packageAudioMime else null,
                        dir, checkCancelled = { context.ensureActive() },
                    )
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

    override fun onDestroy() {
        scope.cancel()
        if (isFinishing && !busy) packageDirectory?.let { packagePath(it).deleteRecursively() }
        super.onDestroy()
    }
}
