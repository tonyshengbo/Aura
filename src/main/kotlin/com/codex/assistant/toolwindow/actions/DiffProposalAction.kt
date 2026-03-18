package com.codex.assistant.toolwindow.actions

import com.codex.assistant.model.TimelineAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

internal class DiffProposalAction(
    private val supportsDiffProposal: () -> Boolean,
    private val resolveVirtualFile: (String) -> VirtualFile?,
    private val shouldApplyDiffProposal: () -> Boolean,
    private val emitSystemMessage: (String) -> Unit,
    private val refreshMessages: () -> Unit,
) {
    fun apply(action: TimelineAction.DiffProposalReceived) {
        if (!supportsDiffProposal()) {
            return
        }
        val vFile = resolveVirtualFile(action.filePath)
        if (vFile == null) {
            emitSystemMessage("Diff target not found: ${action.filePath}")
            refreshMessages()
            return
        }

        val document = FileDocumentManager.getInstance().getDocument(vFile)
        if (document == null) {
            emitSystemMessage("Cannot open target file: ${action.filePath}")
            refreshMessages()
            return
        }

        if (shouldApplyDiffProposal()) {
            ApplicationManager.getApplication().runWriteAction {
                document.setText(action.newContent)
                FileDocumentManager.getInstance().saveDocument(document)
            }
            emitSystemMessage("Applied diff to ${vFile.path}")
            refreshMessages()
        }
    }
}
