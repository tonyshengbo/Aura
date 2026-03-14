package com.codex.assistant.toolwindow

import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JPanel

class WelcomePanel : JPanel(BorderLayout()) {

    init {
        val title = JBLabel("Welcome to Codex Assistant").apply {
            font = font.deriveFont(24f)
        }
        val subtitle = JBLabel("Start a conversation to get help with your code")

        val content = JPanel().apply {
            add(title)
            add(subtitle)
        }

        add(content, BorderLayout.CENTER)
    }
}
