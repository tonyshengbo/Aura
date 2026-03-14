package com.codex.assistant.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class EditorContextProvider(private val project: Project) {

    fun getOpenFiles(): List<String> {
        val editorManager = FileEditorManager.getInstance(project)
        return editorManager.openFiles.mapNotNull { it.path }
    }

    fun getCurrentFile(): String? {
        val editorManager = FileEditorManager.getInstance(project)
        return editorManager.selectedFiles.firstOrNull()?.path
    }

    fun getSelectedText(): String? {
        val editorManager = FileEditorManager.getInstance(project)
        val editor = editorManager.selectedTextEditor ?: return null
        return editor.selectionModel.selectedText
    }

    companion object {
        fun getInstance(project: Project): EditorContextProvider =
            project.getService(EditorContextProvider::class.java)
    }
}
