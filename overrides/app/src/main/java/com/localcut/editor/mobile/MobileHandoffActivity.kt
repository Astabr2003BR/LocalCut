package com.localcut.editor.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.MainActivity
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import java.util.UUID

@UnstableApi
@OptIn(ExperimentalApi::class)
class MobileHandoffActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parsed = MobileHandoffParser.parse(intent?.dataString)
        if (parsed.isFailure) {
            Toast.makeText(
                this,
                parsed.exceptionOrNull()?.message ?: "Comando LocalCut inválido",
                Toast.LENGTH_LONG,
            ).show()
            openEditorAndFinish()
            return
        }

        val command = parsed.getOrThrow()
        AlertDialog.Builder(this)
            .setTitle("Executar no LocalCut?")
            .setMessage(command.description)
            .setNegativeButton("Cancelar") { _, _ -> openEditorAndFinish() }
            .setPositiveButton("Executar") { _, _ -> execute(command) }
            .setOnCancelListener { openEditorAndFinish() }
            .show()
    }

    private fun execute(command: MobileHandoffCommand) {
        val runtime = LocalCutProcessRuntime.get(this)
        val now = System.currentTimeMillis()
        val project = runtime.session.state.project
        val result = runtime.bridgeCommandExecutor.execute(
            BridgeCommandEnvelope(
                protocolVersion = 1,
                messageId = "mobile-${UUID.randomUUID()}",
                kind = "command",
                deviceId = "local-android-handoff",
                projectId = project.id,
                expectedRevision = if (command.capability.requiresRevision) runtime.session.state.revision else null,
                issuedAtEpochMs = now,
                expiresAtEpochMs = now + 60_000L,
                nonce = UUID.randomUUID().toString(),
                capability = command.capability,
                payloadJson = command.payloadJson,
            ),
        )
        runtime.editorViewModel.refreshSnapshot()

        val message = if (result.success) {
            "Comando executado no LocalCut"
        } else {
            result.code?.wireName ?: "Falha ao executar comando"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        openEditorAndFinish()
    }

    private fun openEditorAndFinish() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
