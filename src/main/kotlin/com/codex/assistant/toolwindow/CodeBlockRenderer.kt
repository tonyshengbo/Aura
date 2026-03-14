package com.codex.assistant.toolwindow

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class CodeBlockRenderer(private val project: Project) {

    fun createCodeBlock(code: String, language: String): JPanel {
        val panel = JPanel(BorderLayout())

        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(language)
        val document = EditorFactory.getInstance().createDocument(code)
        val editor = EditorFactory.getInstance().createEditor(document, project, fileType, true) as EditorEx

        editor.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
        }

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            add(JButton("Copy"))
            add(JButton("Apply"))
        }

        panel.add(editor.component, BorderLayout.CENTER)
        panel.add(actions, BorderLayout.SOUTH)

        return panel
    }
}
