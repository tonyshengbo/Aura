package com.codex.assistant.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class InputBoxView(project: Project) {
    private val statusView = InputBoxStatusView()
    private val contextStripView = InputBoxContextStripView()
    private val controlBarView = InputBoxControlBarView()

    val statusSpinnerLabel: JLabel get() = statusView.statusSpinnerLabel
    val statusTextLabel: JLabel get() = statusView.statusTextLabel
    val statusSnackbar: JPanel get() = statusView.statusSnackbar

    val sdkButton: JButton get() = controlBarView.sdkButton
    val sdkIconLabel: JLabel get() = controlBarView.sdkIconLabel
    val sdkTextLabel: JLabel get() = controlBarView.sdkTextLabel
    val sdkArrowLabel: JLabel get() = controlBarView.sdkArrowLabel
    val modeChip: JButton get() = controlBarView.modeChip
    val modeIconLabel: JLabel get() = controlBarView.modeIconLabel
    val modeTextLabel: JLabel get() = controlBarView.modeTextLabel
    val modeArrowLabel: JLabel get() = controlBarView.modeArrowLabel
    val modelChip: JButton get() = controlBarView.modelChip
    val modelIconLabel: JLabel get() = controlBarView.modelIconLabel
    val modelTextLabel: JLabel get() = controlBarView.modelTextLabel
    val modelArrowLabel: JLabel get() = controlBarView.modelArrowLabel
    val reasoningChip: JButton get() = controlBarView.reasoningChip
    val reasoningIconLabel: JLabel get() = controlBarView.reasoningIconLabel
    val reasoningTextLabel: JLabel get() = controlBarView.reasoningTextLabel
    val reasoningArrowLabel: JLabel get() = controlBarView.reasoningArrowLabel

    val attachedFilesPanel: JPanel get() = contextStripView.attachedFilesPanel
    val editorContextPanel: JPanel get() = contextStripView.editorContextPanel
    val editorContextLabel: JLabel get() = contextStripView.editorContextLabel
    val editorContextCloseButton: JButton get() = contextStripView.editorContextCloseButton

    val composerContainer = JPanel(BorderLayout())
    val composePanel = JPanel(BorderLayout(0, 0))
    val contextStripPanel: JPanel get() = contextStripView.contextStripPanel
    val contextStripItems: JPanel get() = contextStripView.contextStripItems
    val contextStripScroll: JBScrollPane get() = contextStripView.contextStripScroll
    val controlsLayout get() = controlBarView.controlsLayout
    val controlsPanel: JPanel get() = controlBarView.controlsPanel
    val controlsScroll: JBScrollPane get() = controlBarView.controlsScroll
    val actionsPanel: JPanel get() = controlBarView.actionsPanel
    val bottomRowLayout get() = controlBarView.bottomRowLayout
    val bottomRowPanel: JPanel get() = controlBarView.bottomRowPanel
    val inputAndControlsLayout = BorderLayout(0, 8)
    val inputAndControlsPanel = JPanel(inputAndControlsLayout)

    val inputArea = FileMentionInputArea(project)
    val inputScroll = JBScrollPane(inputArea)
    val actionButton: JButton get() = controlBarView.actionButton

    enum class ChipType {
        SDK,
        MODE,
        MODEL,
        REASONING,
    }

    fun styleAttachedFilesPanel() {
        contextStripView.styleAttachedFilesPanel()
    }

    fun styleEditorContextPanel() {
        contextStripView.styleEditorContextPanel()
    }

    fun styleStatusSnackbar(composerFontShrinkPt: Float) {
        statusView.style(composerFontShrinkPt)
    }

    fun configureChipButton(button: JButton, iconLabel: JLabel, textLabel: JLabel, arrowLabel: JLabel) {
        controlBarView.configureChipButton(button, iconLabel, textLabel, arrowLabel)
    }

    fun setChipExpanded(chipType: ChipType, arrowIcon: Icon) {
        val mapped = when (chipType) {
            ChipType.SDK -> InputBoxControlBarView.ChipType.SDK
            ChipType.MODE -> InputBoxControlBarView.ChipType.MODE
            ChipType.MODEL -> InputBoxControlBarView.ChipType.MODEL
            ChipType.REASONING -> InputBoxControlBarView.ChipType.REASONING
        }
        controlBarView.setChipExpanded(mapped, arrowIcon)
    }

    fun updateChipContentVisibility(iconOnly: Boolean) {
        controlBarView.updateChipContentVisibility(iconOnly)
    }

    fun styleSelectorButton(button: JButton) {
        controlBarView.styleSelectorButton(button)
    }

    fun stylePrimaryActionButton() {
        controlBarView.stylePrimaryActionButton()
    }

    fun setRunningState(running: Boolean) {
        controlBarView.setRunningState(running)
    }

    fun updateStatus(message: String, loading: Boolean) {
        statusView.update(message, loading)
    }

    fun hideStatus() {
        statusView.hide()
    }

    fun renderAttachedFiles(paths: List<String>, onRemovePath: (String) -> Unit) {
        contextStripView.renderAttachedFiles(paths, onRemovePath)
    }

    fun buildLayout(composerFontShrinkPt: Float, onResized: () -> Unit): JComponent {
        composerContainer.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AssistantUiTheme.BORDER_SUBTLE),
            BorderFactory.createEmptyBorder(8, 8, 10, 8),
        )
        composerContainer.isOpaque = true
        composerContainer.background = AssistantUiTheme.CHROME_BG

        composePanel.background = AssistantUiTheme.SURFACE
        composePanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AssistantUiTheme.BORDER, 1, true),
            BorderFactory.createEmptyBorder(),
        )

        contextStripView.configureStrip()

        val inputRow = JPanel(BorderLayout())
        inputRow.isOpaque = true
        inputRow.background = AssistantUiTheme.SURFACE_SUBTLE
        inputArea.toolTipText = "输入需求。Enter 发送，Shift+Enter 换行，@ 引入文件。"
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.rows = 5
        inputArea.background = AssistantUiTheme.SURFACE_SUBTLE
        inputArea.foreground = AssistantUiTheme.TEXT_PRIMARY
        inputArea.caretColor = AssistantUiTheme.TEXT_PRIMARY
        inputArea.border = BorderFactory.createEmptyBorder()
        inputArea.font = inputArea.font.deriveFont((inputArea.font.size2D - composerFontShrinkPt - 1f).coerceAtLeast(10f))

        inputScroll.border = BorderFactory.createEmptyBorder()
        inputScroll.background = inputArea.background
        inputScroll.preferredSize = Dimension(300, 116)
        inputScroll.minimumSize = Dimension(0, 96)

        inputRow.add(inputScroll, BorderLayout.CENTER)
        inputRow.minimumSize = Dimension(0, 96)
        inputRow.preferredSize = Dimension(1, 116)

        controlBarView.configureBottomRow()

        inputAndControlsPanel.isOpaque = true
        inputAndControlsPanel.background = AssistantUiTheme.SURFACE_SUBTLE
        inputAndControlsPanel.removeAll()
        inputAndControlsPanel.add(inputRow, BorderLayout.CENTER)
        inputAndControlsPanel.add(bottomRowPanel, BorderLayout.SOUTH)
        inputAndControlsPanel.minimumSize = Dimension(0, 132)
        inputAndControlsPanel.preferredSize = Dimension(10, 152)

        val centerStack = JPanel(BorderLayout()).apply {
            isOpaque = false
        }
        centerStack.add(statusSnackbar, BorderLayout.NORTH)
        centerStack.add(inputAndControlsPanel, BorderLayout.CENTER)

        composePanel.removeAll()
        composePanel.add(contextStripPanel, BorderLayout.NORTH)
        composePanel.add(centerStack, BorderLayout.CENTER)
        composePanel.minimumSize = Dimension(0, 170)
        composePanel.preferredSize = Dimension(10, 192)

        composerContainer.removeAll()
        composerContainer.add(composePanel, BorderLayout.CENTER)
        composerContainer.preferredSize = Dimension(10, 206)
        composerContainer.minimumSize = Dimension(10, 182)
        composePanel.minimumSize = Dimension(0, 166)
        composerContainer.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                onResized()
            }
        })
        return composerContainer
    }
}
