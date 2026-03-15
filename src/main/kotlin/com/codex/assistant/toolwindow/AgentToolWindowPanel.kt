package com.codex.assistant.toolwindow

import com.codex.assistant.model.AgentEvent
import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.ContextFile
import com.codex.assistant.model.MessageRole
import com.codex.assistant.model.TimelineAction
import com.codex.assistant.model.TimelineActionCodec
import com.codex.assistant.model.label
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.CodexModelCatalog
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.toolwindow.timeline.ConversationTimelineBuilder
import com.codex.assistant.toolwindow.timeline.ConversationTimelinePanel
import com.codex.assistant.toolwindow.timeline.LiveCommandTrace
import com.codex.assistant.toolwindow.timeline.LiveNarrativeTrace
import com.codex.assistant.toolwindow.timeline.LiveToolTrace
import com.codex.assistant.toolwindow.timeline.LiveTurnSnapshot
import com.codex.assistant.toolwindow.timeline.TimelineNodeKind
import com.codex.assistant.toolwindow.timeline.TimelineNodeOrigin
import com.codex.assistant.toolwindow.timeline.TimelineNodeStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.Icon

class AgentToolWindowPanel(
    private val project: Project,
    private val toolWindowEx: ToolWindowEx? = null,
) : JPanel(BorderLayout()), Disposable {
    private val chatService = project.getService(AgentChatService::class.java)
    private val approvalUiPolicy = ApprovalUiPolicy()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val titleToolView = TitleToolView()
    private val inputBoxView = InputBoxView(project)
    private val titleToolViewModel = TitleToolViewModel(object : TitleToolPort {
        override fun startNewSession() {
            this@AgentToolWindowPanel.startNewSession()
        }

        override fun startNewWindow() {
            this@AgentToolWindowPanel.startNewWindowTab()
        }
    })
    private val inputBoxViewModel = InputBoxViewModel(object : InputBoxPort {
        override fun primaryAction() {
            this@AgentToolWindowPanel.onPrimaryAction()
        }

        override fun showSdkMenu(anchor: JButton) {
            composerSettingsAction.showSdkMenu(anchor)
        }

        override fun showModeMenu(anchor: JButton) {
            composerSettingsAction.showModeMenu(anchor)
        }

        override fun showModelMenu(anchor: JButton) {
            composerSettingsAction.showModelMenu(anchor)
        }

        override fun showReasoningMenu(anchor: JButton) {
            composerSettingsAction.showReasoningMenu(anchor)
        }

        override fun dismissEditorContext() {
            this@AgentToolWindowPanel.dismissCurrentEditorContext()
        }
        override fun fileMentioned(path: String) {
            attachedFiles.add(path)
            refreshChipLabels()
        }
    })
    private val messageBoxViewModel = MessageBoxViewModel(object : MessageBoxPort {
        override fun retryTool(name: String, input: String) {
            this@AgentToolWindowPanel.retryTool(name, input)
        }

        override fun retryMessage(content: String) {
            this@AgentToolWindowPanel.retryMessage(content)
        }

        override fun copyMessage(messageId: String) {
            this@AgentToolWindowPanel.copyMessageToClipboard(messageId)
        }

        override fun openFile(path: String) {
            this@AgentToolWindowPanel.openFileInEditor(path)
        }

        override fun retryCommand(command: String, cwd: String) {
            this@AgentToolWindowPanel.retryCommand(command, cwd)
        }

        override fun copyCommand(command: String) {
            this@AgentToolWindowPanel.copyCommandToClipboard(command)
        }
    })
    private val titleToolAction = TitleToolAction(titleToolView, titleToolViewModel)
    private val inputBoxAction = InputBoxAction(inputBoxView, inputBoxViewModel)
    private val messageBoxAction = MessageBoxAction(messageBoxViewModel)
    private val composerSettingsViewModel = ComposerSettingsViewModel(
        ComposerSettingsState(
            selectedEngineId = chatService.defaultEngineId(),
            selectedModel = CodexModelCatalog.defaultModel,
            selectedReasoningDepth = ReasoningDepth.MEDIUM,
        ),
    )
    private val composerSettingsAction = ComposerSettingsAction(
        chatService = chatService,
        viewModel = composerSettingsViewModel,
        inputBoxView = inputBoxView,
        actionIcon = { icon -> actionIcon(icon) },
        actionArrowIcon = { icon -> actionArrowIcon(icon) },
        menuWidthProvider = { computeMenuItemWidth() },
        onSettingsChanged = { refreshChipLabels() },
    )
    private val sessionTabs = SessionTabCoordinator(
        chatService = chatService,
        toolWindowProvider = { toolWindowEx },
        isRunning = { isRunning },
        onStatus = { message -> setStatusMessage(message) },
        onSessionActivated = {
            resetConversationUi()
            refreshMessages()
        },
    )
    private val timelinePresenter = AgentTimelinePresenter()
    private val diffProposalAction = DiffProposalAction(
        supportsDiffProposal = { currentCapabilities().supportsDiffProposal },
        resolveVirtualFile = { path -> resolveVirtualFile(path) },
        shouldApplyDiffProposal = { approvalUiPolicy.shouldApplyDiffProposal() },
        emitSystemMessage = { content ->
            chatService.addMessage(ChatMessage(role = MessageRole.SYSTEM, content = content))
        },
        refreshMessages = { refreshMessages() },
    )
    private val newChatButton: JButton get() = titleToolView.newChatButton
    private val newWindowButton: JButton get() = titleToolView.newWindowButton
    private val sdkButton: JButton get() = inputBoxView.sdkButton
    private val sdkIconLabel: JLabel get() = inputBoxView.sdkIconLabel
    private val sdkTextLabel: JLabel get() = inputBoxView.sdkTextLabel
    private val sdkArrowLabel: JLabel get() = inputBoxView.sdkArrowLabel
    private val modeChip: JButton get() = inputBoxView.modeChip
    private val modeIconLabel: JLabel get() = inputBoxView.modeIconLabel
    private val modeTextLabel: JLabel get() = inputBoxView.modeTextLabel
    private val modeArrowLabel: JLabel get() = inputBoxView.modeArrowLabel
    private val modelChip: JButton get() = inputBoxView.modelChip
    private val modelIconLabel: JLabel get() = inputBoxView.modelIconLabel
    private val modelTextLabel: JLabel get() = inputBoxView.modelTextLabel
    private val modelArrowLabel: JLabel get() = inputBoxView.modelArrowLabel
    private val reasoningChip: JButton get() = inputBoxView.reasoningChip
    private val reasoningIconLabel: JLabel get() = inputBoxView.reasoningIconLabel
    private val reasoningTextLabel: JLabel get() = inputBoxView.reasoningTextLabel
    private val reasoningArrowLabel: JLabel get() = inputBoxView.reasoningArrowLabel
    private val editorContextPanel: JPanel get() = inputBoxView.editorContextPanel
    private val editorContextLabel: JLabel get() = inputBoxView.editorContextLabel
    private val editorContextCloseButton: JButton get() = inputBoxView.editorContextCloseButton
    private val composerContainer: JPanel get() = inputBoxView.composerContainer
    private val composePanel: JPanel get() = inputBoxView.composePanel
    private val controlsLayout: FlowLayout get() = inputBoxView.controlsLayout
    private val bottomRowLayout: BorderLayout get() = inputBoxView.bottomRowLayout
    private val bottomRowPanel: JPanel get() = inputBoxView.bottomRowPanel
    private val inputAndControlsLayout: BorderLayout get() = inputBoxView.inputAndControlsLayout
    private val inputAndControlsPanel: JPanel get() = inputBoxView.inputAndControlsPanel
    private val inputArea: FileMentionInputArea get() = inputBoxView.inputArea
    private val inputScroll: JBScrollPane get() = inputBoxView.inputScroll
    private val actionButton: JButton get() = inputBoxView.actionButton
    private val attachStatusLabel = JLabel()
    private val timelineBuilder = ConversationTimelineBuilder()
    private val timelinePanel = ConversationTimelinePanel(
        onCopyMessage = { messageBoxAction.onCopyMessage(it) },
        onRetryMessage = { content -> messageBoxAction.onRetryMessage(content) },
        onOpenFile = { messageBoxAction.onOpenFile(it) },
        onRetryTool = { name, input -> messageBoxAction.onRetryTool(name, input) },
        onRetryCommand = { command, cwd -> messageBoxAction.onRetryCommand(command, cwd) },
        onCopyCommand = { messageBoxAction.onCopyCommand(it) },
    )
    private val messageBoxView = MessageBoxView(timelinePanel)
    private val toolWindowAction = AgentToolWindowAction(
        titleToolAction = titleToolAction,
        inputBoxAction = inputBoxAction,
        titleToolViewModel = titleToolViewModel,
        messageBoxViewModel = messageBoxViewModel,
        inputBoxViewModel = inputBoxViewModel,
        titleToolView = titleToolView,
        messageBoxView = messageBoxView,
        inputBoxView = inputBoxView,
    )

    private val attachedFiles = linkedSetOf<String>()
    private var currentAssistantContentBuffer = StringBuilder()
    private var currentThinkingBuffer = StringBuilder()
    private val turnState = AgentTurnState()
    private val currentToolEventsBuffer = mutableListOf<ToolTraceItem>()
    private val currentCommandEventsBuffer = mutableListOf<CommandTraceItem>()
    private val currentNarrativeEventsBuffer = mutableListOf<NarrativeTraceItem>()
    private var activeContentNarrativeId: String? = null
    private var currentFlowSequence: Int = 0
    private var isRunning = false
    private var dismissedEditorContextPath: String? = null
    private var expandAllTools: Boolean = true
    private var cachedMessagesHtml: String = ""
    private var cachedMessageCount: Int = -1
    private var cachedLastMessageId: String = ""
    private var cachedToolDetailMode: Boolean = expandAllTools
    private val expandedToolMessageIds = linkedSetOf<String>()
    private val expandedThinkingMessageIds = linkedSetOf<String>()
    private val expandedCommandMessageIds = linkedSetOf<String>()
    private val expandedTurnIds = linkedSetOf<String>()
    private val previewRenderTimer = Timer(80) {
        renderPreviewBufferNow()
    }.apply {
        isRepeats = false
    }
    private val loadingTimer = Timer(1000) {
        if (isRunning) {
            refreshRunningStatusLabel()
        }
    }
    private var controlDensity: ControlDensity = ControlDensity.REGULAR

    init {
        composerSettingsAction.initializeState()
        border = BorderFactory.createEmptyBorder()
        background = AssistantUiTheme.APP_BG

        AssistantUiTheme.toolbarButton(newChatButton)
        AssistantUiTheme.toolbarButton(newWindowButton)
        inputBoxView.styleAttachedFilesPanel()
        inputBoxView.styleEditorContextPanel()
        inputBoxView.configureChipButton(sdkButton, sdkIconLabel, sdkTextLabel, sdkArrowLabel)
        inputBoxView.configureChipButton(modeChip, modeIconLabel, modeTextLabel, modeArrowLabel)
        inputBoxView.configureChipButton(modelChip, modelIconLabel, modelTextLabel, modelArrowLabel)
        inputBoxView.configureChipButton(reasoningChip, reasoningIconLabel, reasoningTextLabel, reasoningArrowLabel)
        composerSettingsAction.applyChipStyles(fontSize = 10.5f)
        inputBoxView.stylePrimaryActionButton()
        inputBoxView.styleStatusSnackbar(COMPOSER_FONT_SHRINK_PT)

        add(buildHeader(), BorderLayout.NORTH)
        add(buildCenter(), BorderLayout.CENTER)
        add(buildBottom(), BorderLayout.SOUTH)

        toolWindowAction.bind(uiScope)
        refreshChipLabels()
        sessionTabs.initialize()

        refreshMessages()
        setRunningState(false)
        installEditorContextListener()
        updateResponsiveLayout(force = true)
        revalidate()
        repaint()
        ApplicationManager.getApplication().invokeLater {
            updateResponsiveLayout(force = true)
            revalidate()
            repaint()
        }
    }

    private fun buildHeader(): JComponent {
        return titleToolView.root
    }

    private fun buildCenter(): JComponent {
        return messageBoxView.root
    }

    private fun buildBottom(): JComponent {
        return inputBoxView.buildLayout(COMPOSER_FONT_SHRINK_PT) {
            updateResponsiveLayout()
        }
    }

    private fun submitPrompt() {
        if (isRunning) {
            return
        }
        refreshChipLabels()
        val prompt = inputArea.text.trim()
        if (prompt.isBlank()) {
            return
        }
        startAgentRun(prompt)
        inputArea.text = ""
    }

    private fun startAgentRun(userPrompt: String) {
        val userMessage = ChatMessage(role = MessageRole.USER, content = userPrompt)
        chatService.addMessage(userMessage)
        refreshMessages()

        currentAssistantContentBuffer = StringBuilder()
        currentThinkingBuffer = StringBuilder()
        currentToolEventsBuffer.clear()
        currentCommandEventsBuffer.clear()
        currentNarrativeEventsBuffer.clear()
        activeContentNarrativeId = null
        currentFlowSequence = 0
        turnState.begin(System.currentTimeMillis())
        loadingTimer.start()
        setRunningState(true)
        refreshRunningStatusLabel()

        chatService.runAgent(
            engineId = composerSettingsAction.selectedEngineId(),
            model = composerSettingsAction.selectedModel(),
            reasoningEffort = composerSettingsAction.selectedReasoningEffort(),
            prompt = userPrompt,
            contextFiles = collectContextFiles(),
        ) { action ->
            ApplicationManager.getApplication().invokeLater {
                handleTimelineAction(action)
            }
        }
    }

    private fun retryTool(name: String, input: String) {
        if (isRunning) return
        val retryPrompt = buildString {
            append("请重试上一步失败的工具调用。")
            if (name.isNotBlank()) append("\n工具: ").append(name)
            if (input.isNotBlank()) append("\n输入: ").append(input)
            append("\n如果失败原因是权限或环境限制，请先说明并给出替代方案。")
        }
        startAgentRun(retryPrompt)
    }

    private fun retryMessage(content: String) {
        if (isRunning) return
        val prompt = content.trim()
        if (prompt.isBlank()) return
        startAgentRun(prompt)
    }

    private fun copyMessageToClipboard(messageId: String) {
        if (messageId.isBlank()) return
        val message = chatService.messages.firstOrNull { it.id == messageId } ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(message.content))
        setStatusMessage("已复制")
    }

    private fun openFileInEditor(path: String) {
        if (path.isBlank()) return
        val file = resolveVirtualFile(path) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun retryCommand(command: String, cwd: String) {
        if (command.isBlank() || isRunning) return
        val retryPrompt = buildString {
            append("请重试这条命令，并先说明风险再执行：\n")
            append("命令: ").append(command)
            if (cwd.isNotBlank()) append("\n工作目录: ").append(cwd)
        }
        startAgentRun(retryPrompt)
    }

    private fun copyCommandToClipboard(command: String) {
        if (command.isBlank()) return
        CopyPasteManager.getInstance().setContents(StringSelection(command))
        setStatusMessage("命令已复制")
    }

    private fun cancelRequest() {
        chatService.cancelCurrent()
        finishTurn("已停止", persistIfNeeded = true)
    }

    private fun handleTimelineAction(action: TimelineAction) {
        if (!turnState.canHandle(action)) {
            return
        }
        when (action) {
            is TimelineAction.AppendNarrative,
            is TimelineAction.AppendThinking,
            is TimelineAction.UpsertTool,
            is TimelineAction.UpsertCommand,
            is TimelineAction.MarkTurnFailed,
            is TimelineAction.CommandProposalReceived,
            -> {
                turnState.record(action)
                renderPreviewBuffer()
            }

            is TimelineAction.DiffProposalReceived -> diffProposalAction.apply(action)
            TimelineAction.FinishTurn -> {
                turnState.markFinalized()
                persistTimelineMessageIfNeeded(turnState.snapshotActions())
                finishTurn("完成", persistIfNeeded = true)
            }
        }
    }

    private fun persistTimelineMessageIfNeeded(actions: List<TimelineAction>) {
        if (actions.isEmpty()) {
            return
        }
        val assistantContent = timelinePresenter.buildAssistantContentFromTimelineActions(actions)
        val hasRenderableContent = assistantContent.isNotBlank() ||
            actions.any {
                it is TimelineAction.UpsertTool ||
                    it is TimelineAction.UpsertCommand ||
                    it is TimelineAction.AppendThinking ||
                    it is TimelineAction.MarkTurnFailed
            }
        if (!hasRenderableContent) {
            return
        }
        chatService.addMessage(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = assistantContent,
                timelineActionsPayload = TimelineActionCodec.encode(actions),
            ),
        )
        refreshMessages()
    }

    private fun refreshMessages() {
        refreshHeaderState()
        refreshUsageSnapshotLabel()
        renderMessages(streamingText = null, forceAutoScroll = false)
        showMessagesCardIfNeeded()
        refreshNewButtonState()
    }

    private fun refreshHeaderState() {
        titleToolViewModel.updateState {
            it.copy(
                title = chatService.currentSessionTitle(),
                subtitle = if (chatService.isCurrentSessionEmpty()) "Codex Chat" else "当前对话",
            )
        }
        sessionTabs.refresh()
    }

    private fun resetConversationUi() {
        currentAssistantContentBuffer = StringBuilder()
        currentThinkingBuffer = StringBuilder()
        currentToolEventsBuffer.clear()
        currentCommandEventsBuffer.clear()
        currentNarrativeEventsBuffer.clear()
        activeContentNarrativeId = null
        currentFlowSequence = 0
        expandedToolMessageIds.clear()
        expandedThinkingMessageIds.clear()
        expandedCommandMessageIds.clear()
        turnState.clear()
        loadingTimer.stop()
        previewRenderTimer.stop()
        hideStatusSnackbar()
        setRunningState(false)
    }

    private fun refreshUsageSnapshotLabel() {
        val snapshot = chatService.currentUsageSnapshot()
        titleToolViewModel.updateState {
            it.copy(
                usageLabel = snapshot?.headerLabel() ?: "--",
                usageTooltip = snapshot?.tooltipText(),
            )
        }
    }

    private fun renderPreviewBuffer() {
        if (!previewRenderTimer.isRunning) {
            previewRenderTimer.start()
        } else {
            previewRenderTimer.restart()
        }
    }

    private fun renderPreviewBufferNow() {
        renderMessages(streamingText = null, forceAutoScroll = false)
        showMessagesCard(forceMessages = true)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun renderMessages(streamingText: String?, forceAutoScroll: Boolean) {
        val liveTurn = buildLiveTurnSnapshot()
        val shouldAutoScroll = forceAutoScroll || isNearBottom()
        val turns = timelineBuilder.build(chatService.messages, liveTurn)
        timelinePanel.updateTurns(turns, shouldAutoScroll)
    }

    private fun isNearBottom(thresholdPx: Int = 24): Boolean {
        return timelinePanel.isNearBottom(thresholdPx)
    }

    private fun scrollToBottom() {
        timelinePanel.scrollToBottom()
    }

    private fun showMessagesCardIfNeeded() {
        if (chatService.messages.isEmpty() &&
            !turnState.hasActions() &&
            currentAssistantContentBuffer.isBlank() &&
            currentThinkingBuffer.isBlank() &&
            currentToolEventsBuffer.isEmpty() &&
            currentCommandEventsBuffer.isEmpty()
        ) {
            showMessagesCard(forceMessages = false)
        } else {
            showMessagesCard(forceMessages = true)
        }
    }

    private fun showMessagesCard(forceMessages: Boolean) {
        messageBoxViewModel.setForceMessages(forceMessages)
    }

    private fun refreshChipLabels() {
        composerSettingsAction.syncChipLabels()
        syncEditorContextIndicator()
        rebuildAttachedFileChips()
    }

    private fun rebuildAttachedFileChips() {
        inputBoxView.renderAttachedFiles(attachedFiles.toList()) { path ->
            attachedFiles.remove(path)
            refreshChipLabels()
        }
    }

    private fun updateChipContentVisibility() {
        val iconOnly = controlDensity == ControlDensity.ICON_ONLY
        inputBoxView.updateChipContentVisibility(iconOnly)
    }

    private fun setRunningState(running: Boolean) {
        isRunning = running
        inputBoxViewModel.setRunning(running)
        if (running) {
            refreshRunningStatusLabel()
        } else {
            hideStatusSnackbar()
        }
    }

    private fun actionIcon(icon: Icon): Icon = ResizedIcon(icon, 14, 14)

    private fun actionArrowIcon(icon: Icon): Icon = ResizedIcon(icon, 13, 13)

    private fun refreshRunningStatusLabel() {
        if (!isRunning) return
        val elapsedMs = turnState.startedAtMs.takeIf { it > 0L }?.let { System.currentTimeMillis() - it } ?: 0L
        showStatusSnackbar(ToolWindowUiText.runningStatus(elapsedMs), loading = true)
    }

    private fun setStatusMessage(message: String) {
        turnState.setStatus(message)
        updateStatusSnackbar(message, loading = false)
    }

    private fun showStatusSnackbar(message: String, loading: Boolean) {
        turnState.setStatus(message)
        inputBoxViewModel.setStatus(message, loading)
    }

    private fun updateStatusSnackbar(message: String, loading: Boolean) {
        turnState.setStatus(message)
        inputBoxViewModel.setStatus(message, loading)
    }

    private fun hideStatusSnackbar() {
        turnState.setStatus("")
        inputBoxViewModel.clearStatus()
    }

    private fun updateResponsiveLayout(force: Boolean = false) {
        val width = composerContainer.width.takeIf { it > 0 } ?: COMPOSER_REGULAR_MIN_WIDTH
        val nextDensity = resolveControlDensity(width)
        if (!force && nextDensity == controlDensity && composePanel.isDisplayable) {
            return
        }
        controlDensity = nextDensity

        val (outerPad, _, rowGap, chipGap, chipHeight) = when (controlDensity) {
            ControlDensity.REGULAR -> LayoutTuning(8, 8, 8, 8, 28)
            ControlDensity.COMPACT -> LayoutTuning(6, 6, 6, 6, 26)
            ControlDensity.ICON_ONLY -> LayoutTuning(6, 6, 6, 5, 26)
        }
        val composerHeight = when (controlDensity) {
            ControlDensity.REGULAR -> 198
            ControlDensity.COMPACT -> 188
            ControlDensity.ICON_ONLY -> 182
        }

        composerContainer.preferredSize = Dimension(10, composerHeight)
        composerContainer.minimumSize = Dimension(10, (composerHeight - 20).coerceAtLeast(164))
        composePanel.preferredSize = Dimension(10, composerHeight - outerPad * 2)
        composePanel.minimumSize = Dimension(10, (composerHeight - outerPad * 2 - 18).coerceAtLeast(146))
        controlsLayout.hgap = chipGap
        inputAndControlsLayout.vgap = rowGap
        bottomRowLayout.hgap = rowGap
        bottomRowPanel.preferredSize = Dimension(10, chipHeight + 4)
        bottomRowPanel.minimumSize = Dimension(10, chipHeight + 2)

        inputArea.rows = 5
        inputArea.font = inputArea.font.deriveFont(10.5f)
        inputScroll.preferredSize = Dimension(300, 116)
        inputScroll.minimumSize = Dimension(10, 92)
        inputAndControlsPanel.preferredSize = Dimension(10, 150)
        inputAndControlsPanel.minimumSize = Dimension(10, 128)

        val chipFontSize = when (controlDensity) {
            ControlDensity.REGULAR -> 10.5f
            ControlDensity.COMPACT -> 9.5f
            ControlDensity.ICON_ONLY -> 9.5f
        }
        composerSettingsAction.applyChipStyles(chipFontSize)
        listOf(sdkButton, modeChip, modelChip, reasoningChip).forEach { button ->
            updateChipContentVisibility()
            button.setPreferredSize(null)
            val naturalWidth = button.preferredSize.width
            val preferredWidth = if (controlDensity == ControlDensity.ICON_ONLY) {
                30
            } else {
                naturalWidth
            }
            button.preferredSize = Dimension(preferredWidth, chipHeight)
            button.minimumSize = Dimension(preferredWidth, chipHeight)
            button.maximumSize = Dimension(preferredWidth, chipHeight)
            button.revalidate()
        }
        revalidate()
        repaint()
    }

    private fun resolveControlDensity(width: Int): ControlDensity = densityForWidth(width)

    private fun onPrimaryAction() {
        if (isRunning) {
            cancelRequest()
        } else {
            submitPrompt()
        }
    }

    private fun computeMenuItemWidth(): Int {
        val available = composerContainer.width.takeIf { it > 0 } ?: DEFAULT_MENU_ITEM_WIDTH
        return (available - 24).coerceIn(MIN_MENU_ITEM_WIDTH, DEFAULT_MENU_ITEM_WIDTH)
    }

    private fun currentCapabilities(): EngineCapabilities {
        return composerSettingsAction.currentCapabilities()
    }

    private fun buildLiveTurnSnapshot(): LiveTurnSnapshot? {
        return timelinePresenter.buildLiveTurnSnapshot(
            turnState = turnState,
            isRunning = isRunning,
            assistantContent = currentAssistantContentBuffer.toString(),
            thinkingContent = currentThinkingBuffer.toString(),
            narratives = currentNarrativeEventsBuffer.map { trace ->
                AgentTimelinePresenter.NarrativeTraceView(
                    id = trace.id,
                    sequence = trace.sequence,
                    body = trace.body,
                    origin = trace.origin,
                    startedAtMs = trace.startedAtMs,
                    source = trace.source.name,
                )
            },
            tools = currentToolEventsBuffer.map { trace ->
                AgentTimelinePresenter.ToolTraceView(
                    id = trace.id,
                    name = trace.name,
                    sequence = trace.sequence,
                    input = trace.input,
                    output = trace.output,
                    done = trace.done,
                    failed = trace.failed,
                    startedAtMs = trace.startedAtMs,
                    finishedAtMs = trace.finishedAtMs,
                )
            },
            commands = currentCommandEventsBuffer.map { trace ->
                AgentTimelinePresenter.CommandTraceView(
                    id = trace.id,
                    sequence = trace.sequence,
                    status = trace.status.name,
                    command = trace.command,
                    cwd = trace.cwd,
                    startedAtMs = trace.startedAtMs,
                    finishedAtMs = trace.finishedAtMs,
                    exitCode = trace.exitCode,
                    output = trace.output,
                )
            },
        )
    }

    private fun buildAssistantStructuredMessage(
        content: String,
        thinking: String,
        tools: List<ToolTraceItem>,
        commands: List<CommandTraceItem>,
        narratives: List<NarrativeTraceItem>,
        includeThinking: Boolean,
    ): String {
        return timelinePresenter.buildAssistantStructuredMessage(
            content = content,
            thinking = thinking,
            includeThinking = includeThinking,
            narratives = narratives.map { trace ->
                AgentTimelinePresenter.NarrativeTraceView(
                    id = trace.id,
                    sequence = trace.sequence,
                    body = trace.body,
                    origin = trace.origin,
                    startedAtMs = trace.startedAtMs,
                    source = trace.source.name,
                )
            },
            tools = tools.map { trace ->
                AgentTimelinePresenter.ToolTraceView(
                    id = trace.id,
                    name = trace.name,
                    sequence = trace.sequence,
                    input = trace.input,
                    output = trace.output,
                    done = trace.done,
                    failed = trace.failed,
                    startedAtMs = trace.startedAtMs,
                    finishedAtMs = trace.finishedAtMs,
                )
            },
            commands = commands.map { trace ->
                AgentTimelinePresenter.CommandTraceView(
                    id = trace.id,
                    sequence = trace.sequence,
                    status = trace.status.name,
                    command = trace.command,
                    cwd = trace.cwd,
                    startedAtMs = trace.startedAtMs,
                    finishedAtMs = trace.finishedAtMs,
                    exitCode = trace.exitCode,
                    output = trace.output,
                )
            },
        )
    }

    private fun registerNarrativeContent(text: String) {
        if (text.isBlank()) return
        val trace = activeContentNarrativeId
            ?.let { id -> currentNarrativeEventsBuffer.firstOrNull { it.id == id } }
            ?: NarrativeTraceItem(
                id = "note-${currentNarrativeEventsBuffer.size + 1}",
                sequence = nextFlowSequence(),
                origin = TimelineNodeOrigin.EVENT,
                source = NarrativeSource.CONTENT,
                startedAtMs = System.currentTimeMillis(),
            ).also {
                currentNarrativeEventsBuffer.add(it)
                activeContentNarrativeId = it.id
            }
        trace.body += text
    }

    private fun registerNarrativeStatus(status: String) {
        if (!shouldRecordStatusAsNarrative(status)) {
            return
        }
        closeActiveContentNarrative()
        val previous = currentNarrativeEventsBuffer.lastOrNull()
        if (previous?.source == NarrativeSource.STATUS && previous.body == status) {
            return
        }
        currentNarrativeEventsBuffer.add(
            NarrativeTraceItem(
                id = "note-${currentNarrativeEventsBuffer.size + 1}",
                sequence = nextFlowSequence(),
                body = status,
                origin = TimelineNodeOrigin.EVENT,
                source = NarrativeSource.STATUS,
                startedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun closeActiveContentNarrative() {
        activeContentNarrativeId = null
    }

    private fun shouldRecordStatusAsNarrative(status: String): Boolean {
        return status !in setOf(
            "正在生成响应...",
            "正在准备会话...",
            "正在执行步骤...",
            "连接中断，正在重试...",
        )
    }

    private fun nextFlowSequence(): Int {
        currentFlowSequence += 1
        return currentFlowSequence
    }

    private fun compactToolText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(220)
    }

    private fun finishTurn(doneText: String, persistIfNeeded: Boolean) {
        val shouldRefreshFiles = hasLikelyFileMutationInTurn()
        val totalDurationMs = turnState.startedAtMs.takeIf { it > 0L }?.let { System.currentTimeMillis() - it }
        if (persistIfNeeded && !turnState.finalized) {
            closeActiveContentNarrative()
            val content = currentAssistantContentBuffer.toString().trim()
            val thinking = currentThinkingBuffer.toString().trim()
            val assistantMessage = buildAssistantStructuredMessage(
                content = content,
                thinking = thinking,
                tools = currentToolEventsBuffer,
                commands = currentCommandEventsBuffer,
                narratives = currentNarrativeEventsBuffer,
                includeThinking = true,
            )
            if (assistantMessage.isNotBlank()) {
                chatService.addMessage(ChatMessage(role = MessageRole.ASSISTANT, content = assistantMessage))
                refreshMessages()
            }
        }

        loadingTimer.stop()
        previewRenderTimer.stop()
        currentAssistantContentBuffer = StringBuilder()
        currentThinkingBuffer = StringBuilder()
        currentToolEventsBuffer.clear()
        currentCommandEventsBuffer.clear()
        currentNarrativeEventsBuffer.clear()
        activeContentNarrativeId = null
        currentFlowSequence = 0
        turnState.clear()
        setStatusMessage(ToolWindowUiText.finishedStatus(doneText, totalDurationMs))
        setRunningState(false)
        renderMessages(streamingText = null, forceAutoScroll = false)
        maybeRefreshProjectFiles(force = shouldRefreshFiles)
        showMessagesCardIfNeeded()
    }

    private fun hasLikelyFileMutationInTurn(): Boolean {
        val merged = if (turnState.hasActions()) {
            turnState.snapshotActions().joinToString(" ") { action ->
                when (action) {
                    is TimelineAction.UpsertTool -> "${action.name} ${action.input} ${action.output}"
                    is TimelineAction.UpsertCommand -> "${action.command} ${action.output}"
                    is TimelineAction.DiffProposalReceived -> "${action.filePath} ${action.newContent}"
                    else -> ""
                }
            }
        } else {
            if (currentToolEventsBuffer.isEmpty() && currentCommandEventsBuffer.isEmpty()) return false
            currentToolEventsBuffer.joinToString(" ") {
                "${it.name} ${it.input} ${it.output}"
            } + " " + currentCommandEventsBuffer.joinToString(" ") {
                "${it.command} ${it.output}"
            }
        }
        val lowered = merged.lowercase()
        if (lowered.isBlank()) return false
        if (mutationKeywords.any { lowered.contains(it) }) return true
        return Regex("""\.(kt|kts|java|xml|md|json|yaml|yml|js|ts|tsx|jsx|py|go|rs)\b""").containsMatchIn(lowered)
    }

    private fun maybeRefreshProjectFiles(force: Boolean = false) {
        if (!force || project.isDisposed) return
        val baseDirPath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(baseDirPath) ?: return
        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)

        val fileManager = FileDocumentManager.getInstance()
        FileEditorManager.getInstance(project).selectedFiles.forEach { file ->
            val document = fileManager.getDocument(file) ?: return@forEach
            if (!fileManager.isFileModified(file)) {
                fileManager.reloadFromDisk(document)
            }
        }
    }

    private fun collectContextFiles(): List<ContextFile> {
        val files = mutableListOf<ContextFile>()

        val editorContext = currentEditorContextFile()
        if (editorContext != null) {
            val pathOnly = editorContext.path.substringBefore("#L")
            if (dismissedEditorContextPath != pathOnly) {
                files.add(editorContext)
            }
        }

        attachedFiles.forEach { path ->
            val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return@forEach
            if (vf.length > maxContextFileBytes) return@forEach
            val content = readVirtualFile(vf) ?: return@forEach
            if (files.none { it.path == path }) {
                files.add(ContextFile(path = path, content = content))
            }
        }

        return files
    }

    private fun currentEditorContextFile(): ContextFile? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return null
        val selectedText = editor.selectionModel.selectedText
        val hasSelection = !selectedText.isNullOrBlank()
        val content = if (hasSelection) selectedText!! else truncateContextContent(document.text)

        val path = if (hasSelection) {
            val startLine = document.getLineNumber(editor.selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(editor.selectionModel.selectionEnd) + 1
            "${vFile.path}#L$startLine-$endLine"
        } else {
            vFile.path
        }
        return ContextFile(path = path, content = content)
    }

    private fun syncEditorContextIndicator() {
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selectedFile == null) {
            editorContextPanel.isVisible = false
            dismissedEditorContextPath = null
            return
        }
        val path = selectedFile.path
        if (dismissedEditorContextPath != null && dismissedEditorContextPath != path) {
            dismissedEditorContextPath = null
        }
        if (dismissedEditorContextPath == path) {
            editorContextPanel.isVisible = false
            return
        }
        editorContextLabel.text = selectedFile.name
        editorContextPanel.toolTipText = "Current file context: ${selectedFile.path}"
        editorContextPanel.isVisible = true
    }

    private fun dismissCurrentEditorContext() {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return
        dismissedEditorContextPath = file.path
        syncEditorContextIndicator()
    }

    private fun installEditorContextListener() {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    ApplicationManager.getApplication().invokeLater { refreshChipLabels() }
                }
            },
        )
    }

    private fun readVirtualFile(file: VirtualFile): String? {
        if (file.length > maxContextFileBytes) return null
        return runCatching {
            truncateContextContent(String(file.contentsToByteArray(), file.charset))
        }.getOrNull()
    }

    private fun truncateContextContent(raw: String): String {
        if (raw.length <= maxContextChars) return raw
        return raw.take(maxContextChars) + "\n\n...[truncated for context]"
    }

    private fun invalidateMessageHtmlCache() {
        cachedMessagesHtml = ""
        cachedMessageCount = -1
        cachedLastMessageId = ""
        cachedToolDetailMode = expandAllTools
    }

    private fun resolveVirtualFile(path: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val absolute = if (FileUtil.isAbsolute(path)) path else FileUtil.join(basePath, path)
        return LocalFileSystem.getInstance().findFileByPath(absolute)
    }

    private data class LayoutTuning(
        val outerPadding: Int,
        val innerPadding: Int,
        val rowGap: Int,
        val chipGap: Int,
        val chipHeight: Int,
    )

    private class ResizedIcon(
        private val delegate: Icon,
        private val targetWidth: Int,
        private val targetHeight: Int,
    ) : Icon {
        override fun getIconWidth(): Int = targetWidth

        override fun getIconHeight(): Int = targetHeight

        override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics?, x: Int, y: Int) {
            if (g !is java.awt.Graphics2D) return
            val sourceWidth = delegate.iconWidth.coerceAtLeast(1)
            val sourceHeight = delegate.iconHeight.coerceAtLeast(1)
            val tx = g.create() as java.awt.Graphics2D
            tx.translate(x.toDouble(), y.toDouble())
            tx.transform(
                AffineTransform.getScaleInstance(
                    targetWidth.toDouble() / sourceWidth.toDouble(),
                    targetHeight.toDouble() / sourceHeight.toDouble(),
                ),
            )
            delegate.paintIcon(c, tx, 0, 0)
            tx.dispose()
        }
    }

    internal enum class ControlDensity {
        REGULAR,
        COMPACT,
        ICON_ONLY,
    }

    internal enum class ReasoningDepth(
        val label: String,
        val effort: String,
    ) {
        LOW(label = "较少", effort = "low"),
        MEDIUM(label = "中等", effort = "medium"),
        HIGH(label = "较多", effort = "high"),
        MAX(label = "最多", effort = "xhigh"),
    }

    companion object {
        private const val COMPOSER_FONT_SHRINK_PT = 2f
        internal const val COMPOSER_REGULAR_MIN_WIDTH = 760
        internal const val COMPOSER_COMPACT_MIN_WIDTH = 600
        private const val DEFAULT_MENU_ITEM_WIDTH = 248
        private const val MIN_MENU_ITEM_WIDTH = 180
        private const val maxContextChars = 120_000
        private const val maxContextFileBytes = 200_000L
        private val mutationKeywords: Set<String> = setOf(
            "write",
            "edit",
            "patch",
            "diff",
            "apply",
            "rename",
            "delete",
            "create",
            "modify",
            "save",
            "update",
        )
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        internal fun densityForWidth(width: Int): ControlDensity = when {
            width >= COMPOSER_REGULAR_MIN_WIDTH -> ControlDensity.REGULAR
            width >= COMPOSER_COMPACT_MIN_WIDTH -> ControlDensity.COMPACT
            else -> ControlDensity.ICON_ONLY
        }
    }

    private data class ToolTraceItem(
        val id: String,
        val name: String,
        val sequence: Int,
        var input: String = "",
        var output: String = "",
        var done: Boolean = false,
        var failed: Boolean = false,
        var startedAtMs: Long = 0L,
        var finishedAtMs: Long = 0L,
    )

    private data class ChangedFileRef(
        val path: String,
        val name: String,
        val delta: String,
    )

    private data class TurnViewModel(
        val id: String,
        val userMessage: ChatMessage?,
        val assistantMessage: ChatMessage?,
        val systemMessages: List<ChatMessage>,
    )

    private data class ToolMeta(
        val status: String,
        val name: String,
        val sequence: Int?,
        val startedAtMs: Long?,
        val durationMs: Long?,
    )

    private data class CommandTraceItem(
        val id: String,
        val command: String,
        val cwd: String,
        val sequence: Int,
        var status: CommandStatus = CommandStatus.PENDING,
        var startedAtMs: Long = 0L,
        var finishedAtMs: Long = 0L,
        var exitCode: Int? = null,
        var output: String = "",
    )

    private data class NarrativeTraceItem(
        val id: String,
        val sequence: Int,
        var body: String = "",
        val origin: TimelineNodeOrigin,
        val source: NarrativeSource,
        val startedAtMs: Long = 0L,
    )

    private enum class CommandStatus {
        PENDING,
        RUNNING,
        DONE,
        FAILED,
        SKIPPED,
    }

    private enum class NarrativeSource {
        STATUS,
        CONTENT,
    }

    private data class CommandMeta(
        val id: String,
        val status: String,
        val sequence: Int?,
        val command: String,
        val cwd: String,
        val startedAtMs: Long?,
        val durationMs: Long?,
        val exitCode: Int?,
        val output: String,
    )

    private data class FlowStepRow(
        val sequence: Int?,
        val startedAtMs: Long?,
        val html: String,
        val order: Int,
    )

    private fun clearConversation() {
        chatService.clearMessages()
        resetConversationUi()
        refreshMessages()
    }

    private fun startNewSession() {
        sessionTabs.startNewSession()
    }

    private fun startNewWindowTab() {
        sessionTabs.startNewWindowTab()
    }

    private fun refreshNewButtonState() {
        newChatButton.isEnabled = true
        newWindowButton.isEnabled = true
    }

    override fun dispose() {
        previewRenderTimer.stop()
        loadingTimer.stop()
        uiScope.cancel()
        chatService.cancelCurrent()
        toolWindowEx?.setTabActions()
    }
}
