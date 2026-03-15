package com.codex.assistant.toolwindow

import com.codex.assistant.toolwindow.timeline.ConversationTimelinePanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class MessageBoxView(
    private val timelinePanel: ConversationTimelinePanel,
) {
    val placeholderPanel = JPanel(BorderLayout())
    val centerCards = JPanel(CardLayout())
    val consoleView = JPanel(BorderLayout())
    val root: JComponent = build()

    private fun build(): JComponent {
        val placeholderContainer = JPanel(BorderLayout())
        placeholderContainer.background = AssistantUiTheme.APP_BG
        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.border = BorderFactory.createEmptyBorder(72, 0, 0, 0)
        inner.isOpaque = false

        val main = JLabel("开始一个新任务")
        main.font = main.font.deriveFont(Font.BOLD, 20f)
        main.foreground = AssistantUiTheme.TEXT_PRIMARY
        main.alignmentX = 0f

        val sub = JLabel("在一个对话里完成提问、执行、改文件和查看结果。")
        sub.foreground = AssistantUiTheme.TEXT_MUTED
        sub.alignmentX = 0f

        inner.add(main)
        inner.add(Box.createVerticalStrut(6))
        inner.add(sub)

        placeholderContainer.add(inner, BorderLayout.NORTH)
        placeholderPanel.isOpaque = true
        placeholderPanel.background = AssistantUiTheme.APP_BG
        placeholderPanel.add(placeholderContainer, BorderLayout.CENTER)

        centerCards.isOpaque = true
        centerCards.background = AssistantUiTheme.APP_BG
        centerCards.add(placeholderPanel, CARD_PLACEHOLDER)
        centerCards.add(timelinePanel, CARD_MESSAGES)

        consoleView.isOpaque = true
        consoleView.background = AssistantUiTheme.APP_BG
        consoleView.add(centerCards, BorderLayout.CENTER)
        return consoleView
    }

    fun showMessages(forceMessages: Boolean) {
        val layout = centerCards.layout as CardLayout
        if (forceMessages) {
            layout.show(centerCards, CARD_MESSAGES)
        } else {
            layout.show(centerCards, CARD_PLACEHOLDER)
        }
    }

    companion object {
        const val CARD_PLACEHOLDER = "placeholder"
        const val CARD_MESSAGES = "messages"
    }
}
