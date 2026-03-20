package com.auracode.assistant.toolwindow.session

import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.shared.AssistantUiTheme
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantUiTokens
import com.auracode.assistant.toolwindow.shared.currentIdeDarkTheme
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class ToolWindowHeaderOverflowAction(
    private val overflowTabs: List<ToolWindowHeaderTab>,
    private val onSelect: (String) -> Unit,
) : DumbAwareAction(AuraCodeBundle.message("common.more")), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val tokens = assistantUiTokens()
        val panel = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = true
        }
        val label = JLabel(AuraCodeBundle.message("common.more"))
        val arrow = JButton("\u2304").apply {
            isFocusable = false
            isOpaque = false
            setContentAreaFilled(false)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            addActionListener { showOverflowPopup(panel) }
        }
        label.font = label.font.deriveFont(Font.BOLD, 12f)
        arrow.font = arrow.font.deriveFont(Font.PLAIN, 12f)
        panel.add(label, BorderLayout.CENTER)
        panel.add(arrow, BorderLayout.EAST)
        panel.putClientProperty("label", label)
        panel.putClientProperty("arrow", arrow)
        panel.border = BorderFactory.createEmptyBorder(0, tokens.spacing.xs.value.toInt(), 0, tokens.spacing.xs.value.toInt())
        panel.toolTipText = AuraCodeBundle.message("common.more")
        panel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                showOverflowPopup(panel)
            }
        })
        applyStyle(panel, label, arrow)
        return panel
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        val panel = component as? JPanel ?: return
        val label = panel.getClientProperty("label") as? JLabel ?: return
        val arrow = panel.getClientProperty("arrow") as? JButton ?: return
        label.text = AuraCodeBundle.message("common.more")
        applyStyle(panel, label, arrow)
    }

    private fun showOverflowPopup(anchor: JComponent) {
        if (overflowTabs.isEmpty()) {
            return
        }
        val actions = overflowTabs.map { tab ->
            object : DumbAwareAction(tab.fullTitle) {
                override fun actionPerformed(e: AnActionEvent) {
                    onSelect(tab.sessionId)
                }
            }
        }
        val group = DefaultActionGroup(actions)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            AuraCodeBundle.message("common.more"),
            group,
            DataManager.getInstance().getDataContext(anchor),
            ActionSelectionAid.SPEEDSEARCH,
            true,
        )
        popup.showUnderneathOf(anchor)
    }

    private fun applyStyle(panel: JPanel, label: JLabel, arrow: JButton) {
        val tokens = assistantUiTokens()
        val theme = if (currentIdeDarkTheme()) EffectiveTheme.DARK else EffectiveTheme.LIGHT
        val palette = AssistantUiTheme.palette(theme)
        panel.background = palette.chromeBg
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 0, 0),
            BorderFactory.createEmptyBorder(
                tokens.spacing.xs.value.toInt(),
                tokens.spacing.sm.value.toInt(),
                tokens.spacing.xs.value.toInt(),
                tokens.spacing.sm.value.toInt(),
            ),
        )
        label.foreground = palette.textSecondary
        arrow.foreground = palette.textSecondary
    }
}
