package com.codex.assistant.toolwindow

import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

class ProgressIndicator : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {

    private val label = JLabel("0%")

    init {
        isOpaque = false
        AssistantUiTheme.meta(label)
        add(label)
    }

    fun updateProgress(percentage: Int) {
        label.text = "$percentage%"
    }
}
