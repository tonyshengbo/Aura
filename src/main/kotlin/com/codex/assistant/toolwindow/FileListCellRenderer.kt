package com.codex.assistant.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class FileListCellRenderer(private val project: Project) : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        if (value is VirtualFile) {
            val basePath = project.basePath ?: ""
            val relativePath = value.path.removePrefix(basePath).removePrefix("/")
            text = relativePath
        }

        return this
    }
}
