package com.codex.assistant.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import javax.swing.DefaultListModel
import javax.swing.JPopupMenu
import javax.swing.JTextArea

class AutoCompletePopup(
    private val project: Project,
    private val textArea: JTextArea
) {
    private val popup = JPopupMenu()
    private val listModel = DefaultListModel<String>()
    private val list = JBList(listModel)

    init {
        list.addListSelectionListener {
            if (!list.isSelectionEmpty) {
                val selected = list.selectedValue
                insertCompletion(selected)
                hide()
            }
        }

        val scrollPane = JBScrollPane(list)
        scrollPane.preferredSize = java.awt.Dimension(300, 200)
        popup.add(scrollPane)
    }

    fun show(items: List<String>, x: Int, y: Int) {
        listModel.clear()
        items.forEach { listModel.addElement(it) }
        popup.show(textArea, x, y)
    }

    fun hide() {
        popup.isVisible = false
    }

    private fun insertCompletion(text: String) {
        val caretPos = textArea.caretPosition
        val currentText = textArea.text
        val beforeCaret = currentText.substring(0, caretPos)
        val afterCaret = currentText.substring(caretPos)

        val lastAtOrHash = maxOf(beforeCaret.lastIndexOf('@'), beforeCaret.lastIndexOf('#'))
        if (lastAtOrHash >= 0) {
            val newText = currentText.substring(0, lastAtOrHash + 1) + text + afterCaret
            textArea.text = newText
            textArea.caretPosition = lastAtOrHash + 1 + text.length
        }
    }
}
