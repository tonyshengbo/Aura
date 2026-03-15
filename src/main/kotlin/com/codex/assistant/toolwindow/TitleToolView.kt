package com.codex.assistant.toolwindow

import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class TitleToolView {
    val sessionTitleLabel = JLabel("当前对话")
    val sessionSubtitleLabel = JLabel("Codex Chat")
    val usageLeftLabel = JLabel("--")
    val runContextBar = JPanel(BorderLayout())
    val newChatButton = JButton("新建会话")
    val newWindowButton = JButton("新建窗口")
    val root: JComponent = build()

    data class State(
        val title: String,
        val subtitle: String,
        val usageLabel: String,
        val usageTooltip: String?,
    )

    private fun build(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = true
        panel.background = AssistantUiTheme.CHROME_BG
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AssistantUiTheme.BORDER_SUBTLE),
            BorderFactory.createEmptyBorder(10, 12, 10, 12),
        )

        val titleStack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            AssistantUiTheme.title(sessionTitleLabel)
            AssistantUiTheme.subtitle(sessionSubtitleLabel)
            add(sessionTitleLabel)
        }
        sessionSubtitleLabel.isVisible = false

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(titleStack)
        }

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(newChatButton)
            add(newWindowButton)
        }

        val topRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }

        runContextBar.isOpaque = false
        runContextBar.border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        AssistantUiTheme.meta(usageLeftLabel, AssistantUiTheme.TEXT_SECONDARY)
        val leftStatus = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(usageLeftLabel)
        }

        val stack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(topRow)
            add(runContextBar)
        }
        runContextBar.add(leftStatus, BorderLayout.WEST)
        panel.add(stack, BorderLayout.CENTER)
        return panel
    }

    fun render(state: State) {
        sessionTitleLabel.text = state.title
        sessionSubtitleLabel.text = state.subtitle
        usageLeftLabel.text = state.usageLabel
        usageLeftLabel.toolTipText = state.usageTooltip
    }
}
