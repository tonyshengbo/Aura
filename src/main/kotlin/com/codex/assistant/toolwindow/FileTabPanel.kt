package com.codex.assistant.toolwindow

import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class FileTabPanel(
    private val fileName: String,
    private val onClose: () -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)) {

    init {
        isOpaque = false

        val label = JLabel(fileName)
        val closeButton = JButton("×").apply {
            isFocusable = false
            addActionListener { onClose() }
        }

        AssistantUiTheme.meta(label)
        AssistantUiTheme.ghostButton(closeButton)

        add(label)
        add(closeButton)
    }
}
