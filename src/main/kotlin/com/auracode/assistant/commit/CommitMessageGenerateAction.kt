package com.auracode.assistant.commit

import com.auracode.assistant.i18n.AuraCodeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys

/**
 * Triggers Aura-based commit message generation from the Commit tool window.
 */
class CommitMessageGenerateAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val workflowUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val available = project != null && workflowUi != null && commitMessageControl != null
        e.presentation.text = AuraCodeBundle.message("action.generate.commit.message.text")
        e.presentation.description = AuraCodeBundle.message("action.generate.commit.message.description")
        e.presentation.isEnabledAndVisible = available &&
            !project.getService(CommitMessageGenerationService::class.java).isRunning()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val workflowUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) ?: return
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        project.getService(CommitMessageGenerationService::class.java)
            .generateAndApply(
                commitWorkflowUi = workflowUi,
                commitMessageControl = commitMessageControl,
            )
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
