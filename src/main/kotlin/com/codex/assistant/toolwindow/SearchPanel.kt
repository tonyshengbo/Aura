package com.codex.assistant.toolwindow

import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.JPanel

class SearchPanel(private val onSearch: (String) -> Unit) : JPanel(BorderLayout()) {

    private val searchField = JBTextField()

    init {
        searchField.emptyText.text = "Search conversations..."
        searchField.addActionListener {
            onSearch(searchField.text)
        }

        add(searchField, BorderLayout.CENTER)
    }

    fun clear() {
        searchField.text = ""
    }
}
