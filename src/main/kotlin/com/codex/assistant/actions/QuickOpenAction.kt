package com.codex.assistant.actions

import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.toolwindow.shared.ToolWindowUiText
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class QuickOpenAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.text = CodexBundle.message("action.open.text")
        e.presentation.description = CodexBundle.message("action.open.description")
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        toolWindow?.show()
    }

    companion object {
        val TOOL_WINDOW_ID: String = ToolWindowUiText.PRIMARY_TOOL_WINDOW_ID
    }
}
