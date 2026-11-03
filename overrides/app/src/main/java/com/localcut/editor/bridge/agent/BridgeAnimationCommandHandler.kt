package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

class BridgeAnimationCommandHandler(
    private val session: ProjectSession,
) {
    fun executeCharacter(command: BridgeCommandEnvelope): BridgeResultEnvelope = execute(
        command,
        BridgeAnimationPayloadParser.character(command.payloadJson, session.state.project),
    )

    fun executeScene(command: BridgeCommandEnvelope): BridgeResultEnvelope = execute(
        command,
        BridgeAnimationPayloadParser.scene(command.payloadJson, session.state.project),
    )

    private fun execute(command: BridgeCommandEnvelope, plan: CharacterAnimationPlan): BridgeResultEnvelope {
        val beforeRevision = session.state.revision
        session.dispatch(EditorAction.Execute(ApplyAnimationPlanCommand(plan)))
        val after = session.state
        if (after.revision == beforeRevision) {
            throw IllegalArgumentException(after.lastError ?: "animation plan was not applied")
        }
        val affectedProperties = plan.patches.sumOf { it.propertyAnimations.size }
        return BridgeResultEnvelope(
            messageId = command.messageId,
            success = true,
            code = null,
            projectRevision = after.revision,
            resultJson = "{\"applied\":true,\"affectedClips\":${plan.patches.size},\"affectedProperties\":$affectedProperties}",
            retryable = false,
        )
    }
}
