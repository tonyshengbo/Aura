package com.codex.assistant.toolwindow

import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JMenuItem

class ModeSelector(private val onModeChanged: (String) -> Unit) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

    private val button = JButton("全自动 ▼")
    private val modes = listOf("全自动", "半自动", "手动")

    init {
        isOpaque = false
        AssistantUiTheme.chipButton(button)

        button.addActionListener {
            showModeMenu()
        }

        add(button)
    }

    private fun showModeMenu() {
        val menu = JPopupMenu()
        modes.forEach { mode ->
            menu.add(JMenuItem(mode).apply {
                addActionListener {
                    button.text = "$mode ▼"
                    onModeChanged(mode)
                }
            })
        }
        menu.show(button, 0, button.height)
    }
}
