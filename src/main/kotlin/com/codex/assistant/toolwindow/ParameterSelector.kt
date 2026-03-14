package com.codex.assistant.toolwindow

import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

class ParameterSelector(private val onParameterChanged: (String) -> Unit) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

    private val button = JButton("中等 ▼")
    private val parameters = listOf("低", "中等", "高")

    init {
        isOpaque = false
        AssistantUiTheme.chipButton(button)

        button.addActionListener {
            showParameterMenu()
        }

        add(button)
    }

    private fun showParameterMenu() {
        val menu = JPopupMenu()
        parameters.forEach { param ->
            menu.add(JMenuItem(param).apply {
                addActionListener {
                    button.text = "$param ▼"
                    onParameterChanged(param)
                }
            })
        }
        menu.show(button, 0, button.height)
    }
}
