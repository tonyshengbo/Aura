package com.codex.assistant.toolwindow

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

object AssistantUiTheme {
    val APP_BG = JBColor(0x15171B, 0x15171B)
    val CHROME_BG = JBColor(0x1A1D22, 0x1A1D22)
    val CHROME_RAISED = JBColor(0x20242A, 0x20242A)
    val SURFACE = JBColor(0x1B1F25, 0x1B1F25)
    val SURFACE_RAISED = JBColor(0x222730, 0x222730)
    val SURFACE_SUBTLE = JBColor(0x13161A, 0x13161A)
    val BORDER = JBColor(0x313743, 0x313743)
    val BORDER_STRONG = JBColor(0x404857, 0x404857)
    val BORDER_SUBTLE = JBColor(0x252A32, 0x252A32)
    val TEXT_PRIMARY = JBColor(0xE3E7ED, 0xE3E7ED)
    val TEXT_SECONDARY = JBColor(0xA7B0BE, 0xA7B0BE)
    val TEXT_MUTED = JBColor(0x757F8E, 0x757F8E)
    val ACCENT = JBColor(0x53A6FF, 0x53A6FF)
    val ACCENT_BG = JBColor(0x234B78, 0x234B78)
    val ACCENT_BG_SOFT = JBColor(0x1C3553, 0x1C3553)
    val SUCCESS = JBColor(0x52C7A5, 0x52C7A5)
    val WARNING = JBColor(0xD7B866, 0xD7B866)
    val DANGER = JBColor(0xF07F84, 0xF07F84)

    fun panel(component: JComponent) {
        component.background = SURFACE
        component.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(14, 14, 14, 14),
        )
    }

    fun subtlePanel(component: JComponent) {
        component.background = CHROME_RAISED
        component.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12),
        )
    }

    fun title(label: JLabel) {
        label.foreground = TEXT_PRIMARY
        label.font = label.font.deriveFont(Font.BOLD, 15f)
    }

    fun sectionTitle(label: JLabel) {
        label.foreground = TEXT_PRIMARY
        label.font = label.font.deriveFont(Font.BOLD, 12.5f)
    }

    fun subtitle(label: JLabel) {
        label.foreground = TEXT_SECONDARY
        label.font = label.font.deriveFont(11.5f)
    }

    fun meta(label: JLabel, color: Color = TEXT_MUTED) {
        label.foreground = color
        label.font = label.font.deriveFont(10.5f)
    }

    fun primaryButton(button: JButton) {
        button.isFocusable = false
        button.background = ACCENT_BG
        button.foreground = TEXT_PRIMARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT, 1, true),
            BorderFactory.createEmptyBorder(8, 16, 8, 16),
        )
    }

    fun secondaryButton(button: JButton) {
        button.isFocusable = false
        button.background = SURFACE_RAISED
        button.foreground = TEXT_PRIMARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STRONG, 1, true),
            BorderFactory.createEmptyBorder(6, 12, 6, 12),
        )
    }

    fun ghostButton(button: JButton) {
        button.isFocusable = false
        button.background = CHROME_RAISED
        button.foreground = TEXT_SECONDARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(5, 10, 5, 10),
        )
    }

    fun toolbarButton(button: JButton) {
        button.isFocusable = false
        button.background = CHROME_BG
        button.foreground = TEXT_SECONDARY
        button.border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
    }

    fun toolbarChip(button: JButton) {
        button.isFocusable = false
        button.background = CHROME_RAISED
        button.foreground = TEXT_PRIMARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(3, 8, 3, 8),
        )
    }

    fun compactPrimaryButton(button: JButton) {
        button.isFocusable = false
        button.background = ACCENT_BG_SOFT
        button.foreground = TEXT_PRIMARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10),
        )
    }

    fun chipButton(button: JButton) {
        button.isFocusable = false
        button.background = SURFACE_SUBTLE
        button.foreground = TEXT_SECONDARY
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(3, 8, 3, 8),
        )
    }

    fun badge(label: JLabel) {
        label.isOpaque = true
        label.background = CHROME_RAISED
        label.foreground = TEXT_SECONDARY
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(3, 7, 3, 7),
        )
    }
}
