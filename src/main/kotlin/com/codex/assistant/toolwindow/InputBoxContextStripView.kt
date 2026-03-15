package com.codex.assistant.toolwindow

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.UIManager

internal class InputBoxContextStripView {
    val attachedFilesPanel = JPanel()
    val editorContextPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
    val editorContextLabel = JLabel()
    val editorContextCloseButton = JButton("\u00D7")

    val contextStripPanel = JPanel(BorderLayout())
    val contextStripItems = JPanel()
    val contextStripScroll = JBScrollPane(
        contextStripItems,
        JScrollPane.VERTICAL_SCROLLBAR_NEVER,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    )

    fun styleAttachedFilesPanel() {
        attachedFilesPanel.isOpaque = false
        attachedFilesPanel.layout = BoxLayout(attachedFilesPanel, BoxLayout.X_AXIS)
        attachedFilesPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
    }

    fun styleEditorContextPanel() {
        editorContextPanel.isOpaque = true
        editorContextPanel.background = AssistantUiTheme.SURFACE
        editorContextPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        editorContextPanel.layout = FlowLayout(FlowLayout.LEFT, 6, 0)
        val chip = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = true
            background = AssistantUiTheme.SURFACE
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AssistantUiTheme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 6),
            )
        }
        val iconLabel = JLabel(UIManager.getIcon("Tree.leafIcon")).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
        }
        editorContextLabel.foreground = AssistantUiTheme.TEXT_PRIMARY
        editorContextLabel.font = editorContextLabel.font.deriveFont(Font.BOLD, 10.5f)
        editorContextCloseButton.isFocusable = false
        editorContextCloseButton.isOpaque = false
        editorContextCloseButton.isContentAreaFilled = false
        editorContextCloseButton.border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
        editorContextCloseButton.foreground = AssistantUiTheme.TEXT_SECONDARY
        editorContextCloseButton.toolTipText = "移除当前文件"
        chip.add(iconLabel)
        chip.add(editorContextLabel)
        chip.add(editorContextCloseButton)
        editorContextPanel.removeAll()
        editorContextPanel.add(chip)
    }

    fun configureStrip() {
        contextStripPanel.isOpaque = true
        contextStripPanel.background = AssistantUiTheme.CHROME_RAISED
        contextStripPanel.border = BorderFactory.createEmptyBorder()
        contextStripPanel.preferredSize = Dimension(10, 40)
        contextStripPanel.minimumSize = Dimension(10, 40)
        contextStripPanel.maximumSize = Dimension(Int.MAX_VALUE, 40)

        contextStripItems.isOpaque = false
        contextStripItems.layout = BoxLayout(contextStripItems, BoxLayout.X_AXIS)
        contextStripItems.removeAll()
        contextStripItems.add(attachedFilesPanel)
        contextStripItems.add(editorContextPanel)
        contextStripItems.add(javax.swing.Box.createHorizontalGlue())

        contextStripScroll.border = BorderFactory.createEmptyBorder()
        contextStripScroll.isOpaque = false
        contextStripScroll.viewport.isOpaque = false
        contextStripScroll.horizontalScrollBar.unitIncrement = 20

        contextStripPanel.removeAll()
        contextStripPanel.add(contextStripScroll, BorderLayout.CENTER)
    }

    fun renderAttachedFiles(paths: List<String>, onRemovePath: (String) -> Unit) {
        attachedFilesPanel.removeAll()
        attachedFilesPanel.isVisible = paths.isNotEmpty()
        paths.forEachIndexed { index, path ->
            val chip = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = true
                background = AssistantUiTheme.SURFACE
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AssistantUiTheme.BORDER, 1, true),
                    BorderFactory.createEmptyBorder(4, 8, 4, 6),
                )
                add(JLabel(UIManager.getIcon("Tree.leafIcon")).apply {
                    border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
                })
                add(JLabel(File(path).name).apply {
                    foreground = AssistantUiTheme.TEXT_PRIMARY
                    font = font.deriveFont(Font.BOLD, 10.5f)
                })
                add(JButton("\u00D7").apply {
                    isFocusable = false
                    isOpaque = false
                    isContentAreaFilled = false
                    foreground = AssistantUiTheme.TEXT_SECONDARY
                    border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
                    toolTipText = "移除文件"
                    addActionListener {
                        onRemovePath(path)
                    }
                })
            }
            attachedFilesPanel.add(chip)
            if (index != paths.lastIndex) {
                attachedFilesPanel.add(javax.swing.Box.createHorizontalStrut(6))
            }
        }
        attachedFilesPanel.revalidate()
        attachedFilesPanel.repaint()
    }
}
