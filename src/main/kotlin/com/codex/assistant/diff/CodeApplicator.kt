package com.codex.assistant.diff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class CodeApplicator(private val project: Project) {

    fun applyChanges(filePath: String, newContent: String): Boolean {
        return try {
            val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return false
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return false

            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(newContent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
