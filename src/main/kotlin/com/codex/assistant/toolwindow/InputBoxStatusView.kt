package com.codex.assistant.toolwindow

import com.intellij.ui.AnimatedIcon
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

internal class InputBoxStatusView {
    val statusSpinnerLabel = JLabel()
    val statusTextLabel = JLabel()
    val statusSnackbar = JPanel(BorderLayout(10, 0))

    fun style(composerFontShrinkPt: Float) {
        statusSnackbar.isOpaque = true
        statusSnackbar.background = AssistantUiTheme.SURFACE_RAISED
        statusSnackbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AssistantUiTheme.BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10),
        )
        statusSpinnerLabel.isOpaque = false
        statusSpinnerLabel.preferredSize = Dimension(16, 16)
        statusSpinnerLabel.minimumSize = Dimension(16, 16)
        statusSpinnerLabel.maximumSize = Dimension(16, 16)

        statusTextLabel.isOpaque = false
        statusTextLabel.foreground = AssistantUiTheme.TEXT_SECONDARY
        statusTextLabel.font = statusTextLabel.font.deriveFont((11.5f - composerFontShrinkPt).coerceAtLeast(9.5f))
        statusTextLabel.border = BorderFactory.createEmptyBorder(0, 2, 0, 0)
        statusSnackbar.removeAll()
        statusSnackbar.add(statusSpinnerLabel, BorderLayout.WEST)
        statusSnackbar.add(statusTextLabel, BorderLayout.CENTER)
    }

    fun update(message: String, loading: Boolean) {
        statusTextLabel.text = message
        statusTextLabel.toolTipText = message
        statusSpinnerLabel.icon = if (loading) AnimatedIcon.Default() else null
        statusSnackbar.isVisible = true
    }

    fun hide() {
        statusSpinnerLabel.icon = null
        statusTextLabel.text = ""
        statusTextLabel.toolTipText = null
        statusSnackbar.isVisible = false
    }
}
