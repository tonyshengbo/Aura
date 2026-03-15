package com.codex.assistant.toolwindow

import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.CodexModelCatalog
import com.codex.assistant.service.AgentChatService
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

internal data class ComposerSettingsState(
    val selectedEngineId: String,
    val selectedModel: String,
    val selectedReasoningDepth: AgentToolWindowPanel.ReasoningDepth,
)

internal class ComposerSettingsViewModel(initialState: ComposerSettingsState) {
    var state: ComposerSettingsState = initialState
        private set

    fun update(transform: (ComposerSettingsState) -> ComposerSettingsState) {
        state = transform(state)
    }
}

internal class ComposerSettingsAction(
    private val chatService: AgentChatService,
    private val viewModel: ComposerSettingsViewModel,
    private val inputBoxView: InputBoxView,
    private val actionIcon: (Icon) -> Icon,
    private val actionArrowIcon: (Icon) -> Icon,
    private val menuWidthProvider: () -> Int,
    private val onSettingsChanged: () -> Unit,
) {
    fun initializeState() {
        val models = availableModels(viewModel.state.selectedEngineId)
        if (viewModel.state.selectedModel !in models) {
            viewModel.update {
                it.copy(selectedModel = models.firstOrNull() ?: CodexModelCatalog.defaultModel)
            }
        }
    }

    fun selectedEngineId(): String = viewModel.state.selectedEngineId

    fun selectedModel(): String = viewModel.state.selectedModel

    fun selectedReasoningEffort(): String = viewModel.state.selectedReasoningDepth.effort

    fun currentCapabilities(): EngineCapabilities {
        return chatService.engineDescriptor(viewModel.state.selectedEngineId)?.capabilities ?: EngineCapabilities(
            supportsThinking = true,
            supportsToolEvents = true,
            supportsCommandProposal = false,
            supportsDiffProposal = false,
        )
    }

    fun syncChipLabels() {
        val state = viewModel.state
        val sdkName = chatService.engineDescriptor(state.selectedEngineId)?.displayName ?: "Codex"
        inputBoxView.sdkIconLabel.icon = actionIcon(engineIconFor(state.selectedEngineId, sdkName))
        inputBoxView.sdkTextLabel.text = ToolWindowUiText.selectionChipLabel(sdkName)
        setChipExpanded(inputBoxView.sdkButton, expanded = false)
        inputBoxView.sdkButton.toolTipText = "切换模型提供方"

        inputBoxView.modelIconLabel.icon = actionIcon(modelIconFor(state.selectedModel))
        inputBoxView.modelTextLabel.text = ToolWindowUiText.selectionChipLabel(state.selectedModel)
        setChipExpanded(inputBoxView.modelChip, expanded = false)
        inputBoxView.modelChip.toolTipText = "切换模型"

        inputBoxView.modeChip.isEnabled = true
        setChipExpanded(inputBoxView.modeChip, expanded = false)
        inputBoxView.modeChip.toolTipText = "当前为默认执行模式"
        inputBoxView.reasoningChip.isEnabled = true
        inputBoxView.reasoningIconLabel.icon = actionIcon(reasoningIconFor(state.selectedReasoningDepth))
        inputBoxView.reasoningTextLabel.text = state.selectedReasoningDepth.label
        setChipExpanded(inputBoxView.reasoningChip, expanded = false)
        inputBoxView.reasoningChip.toolTipText = "设置推理深度"
    }

    fun applyChipStyles(fontSize: Float) {
        styleSdkButton(fontSize)
        styleChip(inputBoxView.modeChip, fontSize)
        styleChip(inputBoxView.modelChip, fontSize)
        styleChip(inputBoxView.reasoningChip, fontSize)
    }

    fun showSdkMenu(anchor: JComponent) {
        val chip = anchor as? JButton ?: return
        val items = chatService.availableEngines().map { descriptor ->
            UnifiedMenuItem(
                icon = engineIconFor(descriptor.id, descriptor.displayName),
                title = descriptor.displayName,
                selected = descriptor.id == viewModel.state.selectedEngineId,
                onSelect = {
                    viewModel.update {
                        val models = availableModels(descriptor.id)
                        val model = if (it.selectedModel in models) {
                            it.selectedModel
                        } else {
                            models.firstOrNull() ?: CodexModelCatalog.defaultModel
                        }
                        it.copy(
                            selectedEngineId = descriptor.id,
                            selectedModel = model,
                        )
                    }
                    onSettingsChanged()
                },
            )
        }
        showUnifiedChipMenu(chip, items)
    }

    fun showModeMenu(anchor: JComponent) {
        val chip = anchor as? JButton ?: return
        val items = listOf(
            UnifiedMenuItem(
                icon = ToolWindowIcons.autoMode,
                title = "自动",
                selected = true,
                onSelect = { onSettingsChanged() },
            ),
        )
        showUnifiedChipMenu(chip, items)
    }

    fun showModelMenu(anchor: JComponent) {
        val chip = anchor as? JButton ?: return
        val state = viewModel.state
        val items = availableModels(state.selectedEngineId).map { model ->
            UnifiedMenuItem(
                icon = modelIconFor(model),
                title = model,
                selected = model == state.selectedModel,
                onSelect = {
                    viewModel.update { it.copy(selectedModel = model) }
                    onSettingsChanged()
                },
            )
        }
        showUnifiedChipMenu(chip, items)
    }

    fun showReasoningMenu(anchor: JComponent) {
        val chip = anchor as? JButton ?: return
        val state = viewModel.state
        val items = AgentToolWindowPanel.ReasoningDepth.entries.map { depth ->
            UnifiedMenuItem(
                icon = reasoningIconFor(depth),
                title = depth.label,
                selected = depth == state.selectedReasoningDepth,
                onSelect = {
                    viewModel.update { it.copy(selectedReasoningDepth = depth) }
                    onSettingsChanged()
                },
            )
        }
        showUnifiedChipMenu(chip, items)
    }

    private fun setChipExpanded(button: JButton, expanded: Boolean) {
        val arrow = actionArrowIcon(if (expanded) ToolWindowIcons.arrowUp else ToolWindowIcons.arrowDown)
        when (button) {
            inputBoxView.sdkButton -> inputBoxView.setChipExpanded(InputBoxView.ChipType.SDK, arrow)
            inputBoxView.modeChip -> inputBoxView.setChipExpanded(InputBoxView.ChipType.MODE, arrow)
            inputBoxView.modelChip -> inputBoxView.setChipExpanded(InputBoxView.ChipType.MODEL, arrow)
            inputBoxView.reasoningChip -> inputBoxView.setChipExpanded(InputBoxView.ChipType.REASONING, arrow)
        }
    }

    private fun styleChip(button: JButton, fontSize: Float) {
        styleSelectorButton(button)
        button.font = button.font.deriveFont(fontSize)
        val chipFont = button.font
        when (button) {
            inputBoxView.modeChip -> {
                inputBoxView.modeTextLabel.font = chipFont
                inputBoxView.modeArrowLabel.font = chipFont
            }
            inputBoxView.modelChip -> {
                inputBoxView.modelTextLabel.font = chipFont
                inputBoxView.modelArrowLabel.font = chipFont
            }
            inputBoxView.reasoningChip -> {
                inputBoxView.reasoningTextLabel.font = chipFont
                inputBoxView.reasoningArrowLabel.font = chipFont
            }
        }
    }

    private fun styleSdkButton(fontSize: Float) {
        val button = inputBoxView.sdkButton
        button.toolTipText = "切换模型提供方"
        styleSelectorButton(button)
        button.font = button.font.deriveFont(fontSize)
        inputBoxView.sdkTextLabel.font = button.font
        inputBoxView.sdkArrowLabel.font = button.font
    }

    private fun styleSelectorButton(button: JButton) {
        inputBoxView.styleSelectorButton(button)
    }

    private fun modelIconFor(model: String): Icon {
        val normalized = model.trim().lowercase()
        return if (normalized.contains("gpt")) ToolWindowIcons.gpt else ToolWindowIcons.codex
    }

    private fun reasoningIconFor(depth: AgentToolWindowPanel.ReasoningDepth): Icon = when (depth) {
        AgentToolWindowPanel.ReasoningDepth.LOW -> ToolWindowIcons.reasoningLow
        AgentToolWindowPanel.ReasoningDepth.MEDIUM -> ToolWindowIcons.reasoningMedium
        AgentToolWindowPanel.ReasoningDepth.HIGH -> ToolWindowIcons.reasoningHigh
        AgentToolWindowPanel.ReasoningDepth.MAX -> ToolWindowIcons.reasoningMax
    }

    private fun engineIconFor(engineId: String, displayName: String): Icon {
        val normalized = "$engineId $displayName".trim().lowercase()
        return if (normalized.contains("gpt") || normalized.contains("openai")) ToolWindowIcons.gpt else ToolWindowIcons.codex
    }

    private fun availableModels(engineId: String): List<String> {
        return chatService.engineDescriptor(engineId)?.models?.takeIf { it.isNotEmpty() } ?: listOf(CodexModelCatalog.defaultModel)
    }

    private fun createUnifiedMenuItem(
        item: UnifiedMenuItem,
        itemWidth: Int,
        onSelect: () -> Unit,
    ): JComponent {
        val normalBackground = JBColor(0x161C24, 0x161C24)
        val hoverBackground = JBColor(0x1D2631, 0x1D2631)
        val selectedBackground = JBColor(0x154E7D, 0x154E7D)
        val normalTitle = JBColor(0xE3ECF8, 0xE3ECF8)
        val selectedTitle = JBColor(0xF4F8FF, 0xF4F8FF)

        val iconLabel = JLabel().apply {
            icon = item.icon
            border = BorderFactory.createEmptyBorder(0, 0, 0, 8)
        }
        val titleLabel = JLabel(item.title).apply {
            foreground = if (item.selected) selectedTitle else normalTitle
            font = font.deriveFont(Font.BOLD, 12f)
        }

        lateinit var container: JPanel
        val mouseListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (!item.selected) {
                    container.background = hoverBackground
                }
            }

            override fun mouseExited(e: MouseEvent?) {
                if (!item.selected) {
                    container.background = normalBackground
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                onSelect()
            }
        }

        container = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = if (item.selected) selectedBackground else normalBackground
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    if (item.selected) JBColor(0x317DBA, 0x317DBA) else JBColor(0x202B39, 0x202B39),
                    1,
                    true,
                ),
                BorderFactory.createEmptyBorder(8, 10, 8, 10),
            )
            preferredSize = Dimension(itemWidth, 36)
            maximumSize = Dimension(itemWidth, 36)
            add(iconLabel, BorderLayout.WEST)
            add(titleLabel, BorderLayout.CENTER)
        }

        listOf(container, iconLabel, titleLabel).forEach {
            it.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            it.addMouseListener(mouseListener)
        }
        return container
    }

    private fun showUnifiedChipMenu(chip: JButton, items: List<UnifiedMenuItem>) {
        val itemWidth = menuWidthProvider()
        val menu = JPopupMenu().apply {
            isOpaque = true
            background = JBColor(0x161C24, 0x161C24)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0x26384F, 0x26384F), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4),
            )
            addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                    setChipExpanded(chip, expanded = true)
                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                    setChipExpanded(chip, expanded = false)
                }

                override fun popupMenuCanceled(e: PopupMenuEvent?) {
                    setChipExpanded(chip, expanded = false)
                }
            })
        }
        items.forEach { item ->
            menu.add(
                createUnifiedMenuItem(item, itemWidth) {
                    item.onSelect()
                    menu.isVisible = false
                },
            )
        }
        menu.show(chip, 0, chip.height)
    }

    private data class UnifiedMenuItem(
        val icon: Icon,
        val title: String,
        val selected: Boolean,
        val onSelect: () -> Unit,
    )
}
