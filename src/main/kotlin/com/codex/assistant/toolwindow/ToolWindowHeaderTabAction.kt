package com.codex.assistant.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class ToolWindowHeaderTabAction(
    private val tab: ToolWindowHeaderTab,
    private val onSelect: (String) -> Unit,
    private val onClose: (String) -> Unit,
) : DumbAwareAction(tab.title), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) {
        onSelect(tab.sessionId)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = true
        }
        val titleLabel = JLabel(tab.title)
        val closeButton = JButton("\u00D7").apply {
            isFocusable = false
            isOpaque = false
            setContentAreaFilled(false)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            foreground = AssistantUiTheme.TEXT_SECONDARY
            toolTipText = "关闭标签"
            isVisible = tab.closable
            addActionListener { onClose(tab.sessionId) }
        }
        panel.add(titleLabel)
        panel.add(closeButton)
        panel.putClientProperty("label", titleLabel)
        panel.putClientProperty("close", closeButton)
        applyStyle(panel, titleLabel, closeButton)
        panel.border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
        panel.toolTipText = tab.title
        panel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                onSelect(tab.sessionId)
            }
        })
        return panel
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        val panel = component as? JPanel ?: return
        val titleLabel = panel.getClientProperty("label") as? JLabel ?: return
        val closeButton = panel.getClientProperty("close") as? JButton ?: return
        titleLabel.text = tab.title
        closeButton.isVisible = tab.closable
        applyStyle(panel, titleLabel, closeButton)
    }

    private fun applyStyle(panel: JPanel, titleLabel: JLabel, closeButton: JButton) {
        panel.background = if (tab.active) AssistantUiTheme.CHROME_RAISED else AssistantUiTheme.CHROME_BG
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, if (tab.active) 2 else 0, 0, AssistantUiTheme.ACCENT),
            BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )
        titleLabel.foreground = if (tab.active) AssistantUiTheme.TEXT_PRIMARY else AssistantUiTheme.TEXT_SECONDARY
        closeButton.foreground = if (tab.active) AssistantUiTheme.TEXT_PRIMARY else AssistantUiTheme.TEXT_SECONDARY
    }
}
