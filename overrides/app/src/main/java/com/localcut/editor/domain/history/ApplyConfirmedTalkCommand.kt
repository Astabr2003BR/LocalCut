package com.localcut.editor.domain.history

import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.animation.LocalTalkClipCoverage
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator

/** One undo covers both optional still-frame extension and the mouth animation. */
data class ApplyConfirmedTalkCommand(
    val imageClipId: String,
    val audioClipId: String,
    val plan: CharacterAnimationPlan,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val extended = LocalTalkClipCoverage.projectedProject(project, imageClipId, audioClipId)
        val applied = ApplyAnimationPlanCommand(plan).apply(extended)
        require(applied.project != project) { "A operação TALK não mudou o projeto" }
        return CommandResult(
            project = applied.project,
            inverse = RestoreConfirmedTalkSnapshotCommand(project, applied.project),
        )
    }
}

private data class RestoreConfirmedTalkSnapshotCommand(
    val target: Project,
    val inverseTarget: Project,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.id == target.id) { "Projeto alterado: não restaurar outra sessão" }
        require(TimelineValidator.validate(target).isEmpty()) { "Snapshot do TALK inválido" }
        return CommandResult(
            project = target,
            inverse = RestoreConfirmedTalkSnapshotCommand(inverseTarget, target),
        )
    }
}
