package com.codex.assistant.toolwindow

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

internal class InputBoxControlBarView {
    val sdkButton = JButton()
    val sdkIconLabel = JLabel()
    val sdkTextLabel = JLabel()
    val sdkArrowLabel = JLabel()

    val modeChip = JButton()
    val modeIconLabel = JLabel()
    val modeTextLabel = JLabel()
    val modeArrowLabel = JLabel()

    val modelChip = JButton()
    val modelIconLabel = JLabel()
    val modelTextLabel = JLabel()
    val modelArrowLabel = JLabel()

    val reasoningChip = JButton()
    val reasoningIconLabel = JLabel()
    val reasoningTextLabel = JLabel()
    val reasoningArrowLabel = JLabel()

    val controlsLayout = FlowLayout(FlowLayout.LEFT, 4, 0)
    val controlsPanel = JPanel(controlsLayout)
    val controlsScroll = JBScrollPane(
        controlsPanel,
        JScrollPane.VERTICAL_SCROLLBAR_NEVER,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    )
    val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))

    val bottomRowLayout = BorderLayout(8, 0)
    val bottomRowPanel = JPanel(bottomRowLayout)
    val actionButton = JButton("发送")

    enum class ChipType {
        SDK,
        MODE,
        MODEL,
        REASONING,
    }

    fun configureChipButton(button: JButton, iconLabel: JLabel, textLabel: JLabel, arrowLabel: JLabel) {
        button.layout = BorderLayout(6, 0)
        button.text = ""
        button.icon = null
        button.removeAll()

        iconLabel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        iconLabel.horizontalAlignment = JLabel.CENTER
        iconLabel.preferredSize = Dimension(14, 14)
        iconLabel.minimumSize = Dimension(14, 14)
        iconLabel.maximumSize = Dimension(14, 14)
        textLabel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        arrowLabel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        arrowLabel.horizontalAlignment = JLabel.CENTER
        arrowLabel.preferredSize = Dimension(20, 20)
        arrowLabel.minimumSize = Dimension(20, 20)
        arrowLabel.maximumSize = Dimension(20, 20)
        textLabel.foreground = AssistantUiTheme.TEXT_PRIMARY
        arrowLabel.foreground = AssistantUiTheme.TEXT_SECONDARY
        textLabel.horizontalAlignment = JLabel.LEFT
        arrowLabel.horizontalAlignment = JLabel.RIGHT

        button.add(iconLabel, BorderLayout.WEST)
        button.add(textLabel, BorderLayout.CENTER)
        button.add(arrowLabel, BorderLayout.EAST)
    }

    fun setChipExpanded(chipType: ChipType, arrowIcon: Icon) {
        when (chipType) {
            ChipType.SDK -> sdkArrowLabel.icon = arrowIcon
            ChipType.MODE -> modeArrowLabel.icon = arrowIcon
            ChipType.MODEL -> modelArrowLabel.icon = arrowIcon
            ChipType.REASONING -> reasoningArrowLabel.icon = arrowIcon
        }
    }

    fun updateChipContentVisibility(iconOnly: Boolean) {
        sdkTextLabel.isVisible = !iconOnly
        sdkArrowLabel.isVisible = !iconOnly
        modeTextLabel.isVisible = !iconOnly
        modeArrowLabel.isVisible = !iconOnly
        modelTextLabel.isVisible = !iconOnly
        modelArrowLabel.isVisible = !iconOnly
        reasoningTextLabel.isVisible = !iconOnly
        reasoningArrowLabel.isVisible = !iconOnly
    }

    fun styleSelectorButton(button: JButton) {
        button.isFocusable = false
        button.isOpaque = false
        button.isContentAreaFilled = false
        button.margin = Insets(0, 0, 0, 0)
        button.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
    }

    fun stylePrimaryActionButton() {
        AssistantUiTheme.primaryButton(actionButton)
        actionButton.text = ""
        actionButton.icon = ToolWindowIcons.send
        actionButton.preferredSize = Dimension(34, 34)
        actionButton.minimumSize = Dimension(34, 34)
        actionButton.maximumSize = Dimension(34, 34)
        actionButton.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AssistantUiTheme.ACCENT, 1, true),
            BorderFactory.createEmptyBorder(6, 6, 6, 6),
        )
    }

    fun setRunningState(running: Boolean) {
        actionButton.text = ""
        actionButton.icon = if (running) ToolWindowIcons.stop else ToolWindowIcons.send
        actionButton.toolTipText = if (running) "停止当前执行" else "发送当前输入"
    }

    fun configureBottomRow() {
        controlsPanel.isOpaque = false
        controlsPanel.removeAll()
        controlsPanel.add(sdkButton)
        controlsPanel.add(modeChip)
        controlsPanel.add(modelChip)
        controlsPanel.add(reasoningChip)
        controlsScroll.border = BorderFactory.createEmptyBorder()
        controlsScroll.isOpaque = false
        controlsScroll.viewport.isOpaque = false
        controlsScroll.horizontalScrollBar.unitIncrement = 20

        actionsPanel.isOpaque = false
        actionsPanel.removeAll()
        actionsPanel.add(actionButton)

        bottomRowPanel.isOpaque = true
        bottomRowPanel.background = AssistantUiTheme.CHROME_BG
        bottomRowPanel.border = BorderFactory.createEmptyBorder()
        bottomRowPanel.removeAll()
        bottomRowPanel.add(controlsScroll, BorderLayout.CENTER)
        bottomRowPanel.add(actionsPanel, BorderLayout.EAST)
        bottomRowPanel.preferredSize = Dimension(10, 48)
        bottomRowPanel.minimumSize = Dimension(0, 40)
    }
}
