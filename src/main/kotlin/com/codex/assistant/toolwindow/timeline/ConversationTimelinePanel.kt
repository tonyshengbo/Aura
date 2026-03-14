package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.toolwindow.AssistantUiTheme
import com.codex.assistant.toolwindow.ToolWindowUiText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.JViewport

class ConversationTimelinePanel(
    private val onCopyMessage: (String) -> Unit,
    private val onRetryMessage: (String) -> Unit = {},
    private val onOpenFile: (String) -> Unit,
    private val onRetryTool: (String, String) -> Unit,
    private val onRetryCommand: (String, String) -> Unit,
    private val onCopyCommand: (String) -> Unit,
) : JPanel(BorderLayout()) {
    private val contentPanel = TimelineContentPanel()
    private val scrollPane = JBScrollPane(contentPanel)
    private val expansionOverrides = mutableMapOf<String, Boolean>()

    init {
        background = Colors.APP_BG
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.isOpaque = true
        contentPanel.background = Colors.APP_BG
        contentPanel.border = BorderFactory.createEmptyBorder(14, 14, 18, 14)

        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.viewport.background = Colors.APP_BG
        add(scrollPane, BorderLayout.CENTER)
    }

    fun renderTurns(
        turns: List<TimelineTurnViewModel>,
        forceAutoScroll: Boolean,
        keepNearBottomAutoScroll: Boolean = true,
    ) {
        val autoScroll = forceAutoScroll || (keepNearBottomAutoScroll && isNearBottom())
        contentPanel.removeAll()
        if (turns.isEmpty()) {
            contentPanel.add(Box.createVerticalGlue())
        } else {
            turns.forEachIndexed { index, turn ->
                contentPanel.add(buildTurnPanel(turn))
                if (index != turns.lastIndex) {
                    contentPanel.add(Box.createVerticalStrut(14))
                }
            }
        }
        contentPanel.revalidate()
        contentPanel.repaint()
        if (autoScroll) {
            val application = ApplicationManager.getApplication()
            if (application != null) {
                application.invokeLater { scrollToBottom() }
            } else {
                scrollToBottom()
            }
        }
    }

    fun isNearBottom(thresholdPx: Int = 32): Boolean {
        val bar = scrollPane.verticalScrollBar ?: return true
        return bar.value + bar.visibleAmount >= bar.maximum - thresholdPx
    }

    fun scrollToBottom() {
        val bar = scrollPane.verticalScrollBar ?: return
        bar.value = bar.maximum
    }

    private fun buildTurnPanel(turn: TimelineTurnViewModel): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false

        val main = JPanel()
        main.layout = BoxLayout(main, BoxLayout.Y_AXIS)
        main.isOpaque = false

        turn.userMessage?.let {
            main.add(buildUserBubble(it))
            main.add(Box.createVerticalStrut(10))
        }
        val visibleNodes = turn.nodes.filter(::shouldRenderNode)
        visibleNodes.forEachIndexed { index, node ->
            main.add(buildNodeCard(node))
            if (index != visibleNodes.lastIndex) {
                main.add(Box.createVerticalStrut(8))
            }
        }
        if (turn.userMessage != null || visibleNodes.isNotEmpty()) {
            main.add(Box.createVerticalStrut(8))
        }
        if (turn.isRunning) {
            main.add(buildTurnFooter(turn))
        }

        panel.add(main, BorderLayout.CENTER)
        return panel
    }

    private fun buildUserBubble(message: ChatMessage): JComponent {
        val bubble = JPanel(BorderLayout(0, 10))
        bubble.name = "task-block-${message.id}"
        bubble.isOpaque = true
        bubble.background = Colors.TASK_BG
        bubble.alignmentX = LEFT_ALIGNMENT
        bubble.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Colors.TASK_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12),
        )
        bubble.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
        }
        val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }
        titleRow.add(JLabel(formatClockTime(message.timestamp)).apply {
            foreground = Colors.TASK_META
            font = font.deriveFont(10f)
        })

        val actions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
        }
        actions.add(buildActionButton("重试", compact = true) { onRetryMessage(message.content) })
        actions.add(Box.createHorizontalStrut(6))
        actions.add(buildActionButton("复制", compact = true) { onCopyMessage(message.id) })

        header.add(titleRow, BorderLayout.WEST)
        header.add(actions, BorderLayout.EAST)
        bubble.add(header, BorderLayout.NORTH)
        bubble.add(createBodyView(message.content, Colors.TASK_TEXT, fontSize = 13f), BorderLayout.CENTER)
        return bubble
    }

    private fun buildNodeCard(node: TimelineNodeViewModel): JComponent {
        val presentation = TimelineNodePresentation.forKind(node.kind)
        val expandable = isExpandable(node)
        val expanded = if (expandable) effectiveExpanded(node) else true

        return when {
            usesExecutionChrome(node) -> buildExecutionNodeCard(node, expanded, expandable)
            else -> when (presentation.chrome) {
            TimelineNodeChrome.NARRATIVE,
            TimelineNodeChrome.RESULT,
            TimelineNodeChrome.ALERT,
            TimelineNodeChrome.SUPPORTING,
            -> buildNarrativeNodeCard(node, presentation)
            TimelineNodeChrome.EXECUTION -> buildExecutionNodeCard(node, expanded, expandable)
        }
        }
    }

    private fun buildTurnFooter(turn: TimelineTurnViewModel): JComponent {
        return JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            add(buildDot(statusColor(turn.footerStatus), 7))
            add(JLabel(turn.statusText).apply {
                foreground = Colors.META
                font = font.deriveFont(11f)
            })
            turn.durationMs?.let { durationMs ->
                add(JLabel("· ${formatDuration(durationMs)}").apply {
                    foreground = Colors.META
                    font = font.deriveFont(11f)
                })
            }
        }
    }

    private fun buildNodeButtons(node: TimelineNodeViewModel): List<JComponent> {
        val buttons = mutableListOf<JComponent>()
        if (node.filePath != null) {
            val label = if (isFileMutationNode(node)) "Diff" else "打开"
            buttons += buildActionButton(label, compact = true) { onOpenFile(node.filePath) }
        }
        if (!node.command.isNullOrBlank()) {
            buttons += buildActionButton("复制", compact = true) { onCopyCommand(node.command) }
            if (node.status == TimelineNodeStatus.FAILED) {
                buttons += buildActionButton("重试", compact = true) { onRetryCommand(node.command, node.cwd.orEmpty()) }
            }
        }
        if (!node.toolName.isNullOrBlank() && node.status == TimelineNodeStatus.FAILED) {
            buttons += buildActionButton("重试", compact = true) { onRetryTool(node.toolName, node.toolInput.orEmpty()) }
        }
        return buttons
    }

    private fun buildNodeIconButtons(node: TimelineNodeViewModel): List<JComponent> {
        val buttons = mutableListOf<JComponent>()
        if (node.filePath != null) {
            val icon = if (isFileMutationNode(node)) "◧" else "↗"
            val tip = if (isFileMutationNode(node)) "查看 Diff" else "打开文件"
            buttons += buildIconActionButton(icon, tip) { onOpenFile(node.filePath) }
        }
        if (!node.command.isNullOrBlank()) {
            buttons += buildIconActionButton("⧉", "复制命令") { onCopyCommand(node.command) }
            if (node.status == TimelineNodeStatus.FAILED) {
                buttons += buildIconActionButton("↻", "重试命令") { onRetryCommand(node.command, node.cwd.orEmpty()) }
            }
        }
        if (!node.toolName.isNullOrBlank() && node.status == TimelineNodeStatus.FAILED) {
            buttons += buildIconActionButton("↻", "重试工具调用") { onRetryTool(node.toolName, node.toolInput.orEmpty()) }
        }
        return buttons
    }

    private var lastTurns: List<TimelineTurnViewModel> = emptyList()
    private var lastForceAutoScroll: Boolean = false

    private fun renderTurnsSnapshot(anchorNodeId: String? = null) {
        val anchor = anchorNodeId?.let(::captureAnchor)
        val previousViewPosition = Point(scrollPane.viewport.viewPosition)
        renderTurns(lastTurns, forceAutoScroll = false, keepNearBottomAutoScroll = false)
        val restored = anchor?.let(::restoreAnchorAfterLayout) == true
        if (!restored) {
            setViewportY(previousViewPosition.y)
        }
    }

    override fun addNotify() {
        super.addNotify()
        background = Colors.APP_BG
    }

    fun updateTurns(
        turns: List<TimelineTurnViewModel>,
        forceAutoScroll: Boolean,
    ) {
        lastTurns = turns
        lastForceAutoScroll = forceAutoScroll
        renderTurns(turns, forceAutoScroll, keepNearBottomAutoScroll = true)
    }

    private fun effectiveExpanded(node: TimelineNodeViewModel): Boolean {
        expansionOverrides[node.id]?.let { return it }
        return if (usesExecutionChrome(node)) {
            false
        } else {
            true
        }
    }

    private fun isExpandable(node: TimelineNodeViewModel): Boolean {
        if (!usesExecutionChrome(node) && !TimelineNodePresentation.forKind(node.kind).isToggleable) return false
        return !node.command.isNullOrBlank() ||
            !node.cwd.isNullOrBlank() ||
            !node.toolInput.isNullOrBlank() ||
            !node.toolOutput.isNullOrBlank() ||
            node.body.isNotBlank()
    }

    private fun usesExecutionChrome(node: TimelineNodeViewModel): Boolean {
        if (TimelineNodePresentation.forKind(node.kind).chrome == TimelineNodeChrome.EXECUTION) return true
        return node.kind == TimelineNodeKind.FAILURE && (!node.command.isNullOrBlank() || !node.toolName.isNullOrBlank())
    }

    private fun collapsedSummary(node: TimelineNodeViewModel): String {
        val source = when {
            isFileMutationNode(node) -> node.body
            !node.command.isNullOrBlank() -> node.command
            !node.body.isBlank() -> node.body
            !node.toolInput.isNullOrBlank() -> node.toolInput
            else -> node.title
        }
        return source.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(140).orEmpty()
    }

    private fun nodeTitle(node: TimelineNodeViewModel): String {
        return when (node.kind) {
            TimelineNodeKind.ASSISTANT_NOTE -> "助手"
            TimelineNodeKind.THINKING -> "思考"
            TimelineNodeKind.TOOL_STEP -> node.toolName ?: node.title
            TimelineNodeKind.COMMAND_STEP -> "命令"
            TimelineNodeKind.RESULT -> "结果"
            TimelineNodeKind.FAILURE -> when {
                !node.command.isNullOrBlank() -> "命令失败"
                !node.toolName.isNullOrBlank() -> "${node.toolName} 失败"
                else -> "执行失败"
            }
            TimelineNodeKind.SYSTEM_AUX -> "系统"
        }
    }

    private fun nodeSubtitle(node: TimelineNodeViewModel): String {
        val parts = mutableListOf<String>()
        if (node.exitCode != null) {
            parts += "退出码 ${node.exitCode}"
        }
        return parts.joinToString(" · ")
    }

    private fun statusColor(status: TimelineNodeStatus): Color {
        return when (status) {
            TimelineNodeStatus.RUNNING -> Colors.WARNING
            TimelineNodeStatus.SUCCESS -> Colors.SUCCESS
            TimelineNodeStatus.FAILED -> Colors.FAILURE
            TimelineNodeStatus.SKIPPED -> Colors.MUTED
        }
    }

    private fun buildDot(color: Color, size: Int): JComponent {
        return JPanel().apply {
            preferredSize = Dimension(size, size)
            minimumSize = Dimension(size, size)
            maximumSize = Dimension(size, size)
            background = color
            border = BorderFactory.createLineBorder(color, 1, true)
        }
    }

    private fun centerWrap(component: JComponent): JComponent {
        return JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
            isOpaque = false
            add(component)
        }
    }

    private fun buildExecutionNodeCard(
        node: TimelineNodeViewModel,
        expanded: Boolean,
        expandable: Boolean,
    ): JComponent {
        val card = JPanel(BorderLayout(0, 0))
        card.name = "node-card-${node.id}"
        card.isOpaque = true
        card.background = Colors.EXECUTION_BG
        card.alignmentX = LEFT_ALIGNMENT
        card.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Colors.EXECUTION_BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10),
        )

        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
        }
        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
        }
        if (expandable) {
            left.add(JLabel(if (expanded) "\u25BE" else "\u25B8").apply {
                foreground = Colors.TEXT_TERTIARY
                font = font.deriveFont(11f)
            })
            left.add(Box.createHorizontalStrut(6))
        }
        executionTitleSegments(node).forEachIndexed { index, segment ->
            if (index > 0) {
                left.add(Box.createHorizontalStrut(6))
            }
            left.add(JLabel(segment.text).apply {
                foreground = segment.color
                font = font.deriveFont(if (segment.strong) Font.BOLD else Font.PLAIN, segment.size)
            })
        }

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
        }
        buildNodeIconButtons(node).forEach(actions::add)
        actions.add(buildDot(statusColor(node.status), 8))

        header.add(left, BorderLayout.CENTER)
        header.add(actions, BorderLayout.EAST)

        card.add(header, BorderLayout.NORTH)
        if (expanded) {
            card.add(buildExecutionDetails(node), BorderLayout.CENTER)
        }
        if (expandable) {
            installCardToggle(card) {
                expansionOverrides[node.id] = !expanded
                renderTurnsSnapshot(anchorNodeId = node.id)
            }
        }
        return card
    }

    private fun buildNarrativeNodeCard(
        node: TimelineNodeViewModel,
        presentation: TimelineNodePresentation,
    ): JComponent {
        val row = JPanel(BorderLayout(0, 6))
        row.name = "node-card-${node.id}"
        row.alignmentX = LEFT_ALIGNMENT
        row.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        row.isOpaque = false
        row.border = BorderFactory.createEmptyBorder(2, 0, 6, 0)

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        if (node.body.isNotBlank()) {
            content.add(
                createBodyView(
                    text = node.body,
                    foreground = narrativeBodyColor(presentation),
                    fontSize = if (presentation.chrome == TimelineNodeChrome.NARRATIVE) 12.5f else 12f,
                ),
            )
        }
        row.add(content, BorderLayout.CENTER)
        return row
    }

    private fun shouldRenderNode(node: TimelineNodeViewModel): Boolean {
        if (node.body.isNotBlank()) return true
        return usesExecutionChrome(node) || isExpandable(node)
    }

    private fun buildExecutionDetails(node: TimelineNodeViewModel): JComponent {
        val bodyPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
        }
        val subtitle = nodeSubtitle(node)
        if (subtitle.isNotBlank()) {
            bodyPanel.add(JLabel(subtitle).apply {
                foreground = Colors.TEXT_MUTED
                font = font.deriveFont(10.5f)
                alignmentX = LEFT_ALIGNMENT
            })
            bodyPanel.add(Box.createVerticalStrut(6))
        }

        when (node.kind) {
            TimelineNodeKind.TOOL_STEP -> {
                if (isFileMutationNode(node) && node.filePath != null) {
                    bodyPanel.add(createMetaLabel("位置"))
                    bodyPanel.add(createBodyView("`${node.filePath}`", Colors.TEXT_PRIMARY, 11.5f))
                }
                if (!node.toolInput.isNullOrBlank() && !isFileMutationNode(node)) {
                    bodyPanel.add(createMetaLabel("输入"))
                    bodyPanel.add(createBodyView(node.toolInput, Colors.TEXT_SECONDARY, 11.5f))
                }
                val detail = node.toolOutput?.takeIf { it.isNotBlank() } ?: node.body.takeIf { it.isNotBlank() }
                if (!detail.isNullOrBlank()) {
                    if (bodyPanel.componentCount > 0) {
                        bodyPanel.add(Box.createVerticalStrut(6))
                    }
                    bodyPanel.add(createMetaLabel("输出"))
                    bodyPanel.add(createBodyView(detail, Colors.TEXT_PRIMARY, 11.5f))
                }
            }

            TimelineNodeKind.COMMAND_STEP,
            TimelineNodeKind.FAILURE,
            -> {
                if (!node.command.isNullOrBlank()) {
                    bodyPanel.add(createMetaLabel("命令"))
                    bodyPanel.add(createCodeView("$ ${node.command}"))
                } else if (!node.toolName.isNullOrBlank()) {
                    if (!node.toolInput.isNullOrBlank()) {
                        bodyPanel.add(createMetaLabel("输入"))
                        bodyPanel.add(createBodyView(node.toolInput, Colors.TEXT_SECONDARY, 11.5f))
                    }
                }
                if (!node.cwd.isNullOrBlank()) {
                    bodyPanel.add(Box.createVerticalStrut(6))
                    bodyPanel.add(createMetaLabel("目录"))
                    bodyPanel.add(createBodyView("`${node.cwd}`", Colors.TEXT_PRIMARY, 11.5f))
                }
                if (!node.body.isBlank()) {
                    bodyPanel.add(Box.createVerticalStrut(6))
                    bodyPanel.add(createMetaLabel("输出"))
                    bodyPanel.add(createBodyView(node.body, Colors.TEXT_PRIMARY, 11.5f))
                }
            }

            else -> Unit
        }
        return bodyPanel
    }

    private fun createMetaLabel(text: String): JComponent {
        return JLabel(text.uppercase()).apply {
            foreground = Colors.META
            font = font.deriveFont(Font.BOLD, 10f)
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun createBodyView(
        text: String,
        foreground: Color,
        fontSize: Float,
    ): JComponent {
        val html = MarkdownRenderer.renderToHtml(text)
        return MarkdownPane(
            html = wrapHtmlBody(html, foreground, fontSize),
        ).apply {
            border = BorderFactory.createEmptyBorder()
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun createCodeView(text: String): JComponent {
        return object : JBTextArea(text) {
            override fun getPreferredSize(): Dimension {
                val availableWidth = resolveAvailableWidth()
                setSize(availableWidth, Int.MAX_VALUE / 8)
                val preferred = super.getPreferredSize()
                return Dimension(availableWidth, preferred.height)
            }

            override fun getMaximumSize(): Dimension = preferredSize

            private fun resolveAvailableWidth(): Int {
                val widths = mutableListOf<Int>()
                var current: Container? = parent
                while (current != null) {
                    val width = when (current) {
                        is JViewport -> current.extentSize.width
                        else -> current.width
                    }
                    if (width > 0) {
                        widths += width
                    }
                    current = current.parent
                }
                val baseWidth = widths.minOrNull() ?: 320
                return (baseWidth - 64).coerceAtLeast(120)
            }
        }.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Colors.CODE_BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 9, 7, 9),
            )
            background = Colors.CODE_BG
            foreground = Colors.TEXT_PRIMARY
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun buildActionButton(
        text: String,
        compact: Boolean = false,
        action: () -> Unit,
    ): JButton {
        return JButton(text).apply {
            isFocusable = false
            horizontalAlignment = SwingConstants.CENTER
            foreground = Colors.ACTION_TEXT
            background = Colors.ACTION_BG
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Colors.ACTION_BORDER, 1, true),
                BorderFactory.createEmptyBorder(if (compact) 2 else 3, if (compact) 6 else 8, if (compact) 2 else 3, if (compact) 6 else 8),
            )
            font = font.deriveFont(if (compact) 10.2f else 10.8f)
            addActionListener { action() }
        }
    }

    private fun buildIconActionButton(
        icon: String,
        tooltip: String,
        action: () -> Unit,
    ): JButton {
        return JButton(icon).apply {
            isFocusable = false
            horizontalAlignment = SwingConstants.CENTER
            foreground = Colors.ACTION_TEXT
            background = Colors.ACTION_BG
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Colors.ACTION_BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 5, 1, 5),
            )
            font = font.deriveFont(10.5f)
            toolTipText = tooltip
            addActionListener { action() }
        }
    }

    private fun installCardToggle(
        component: JComponent,
        onToggle: () -> Unit,
    ) {
        if (component is JButton) {
            return
        }
        component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                onToggle()
            }
        })
        component.components
            .filterIsInstance<JComponent>()
            .forEach { child -> installCardToggle(child, onToggle) }
    }

    private data class ExecutionTitleSegment(
        val text: String,
        val color: Color,
        val strong: Boolean = false,
        val size: Float = 12f,
    )

    private enum class ExecutionCategory(val label: String) {
        READ_FILE("读取文件"),
        EDIT_FILE("编辑文件"),
        SEARCH_FILE("搜索文件"),
        RUN_COMMAND("运行命令"),
        FILE_SEARCH("文件检索"),
        WEB_SEARCH("网页检索"),
        FUNCTION_CALL("调用函数"),
        MCP_CALL("MCP 工具"),
        CODE_INTERPRETER("代码解释器"),
        COMPUTER_USE("计算机操作"),
        IMAGE_GENERATION("图像生成"),
        GENERIC_TOOL("执行工具"),
    }

    private fun executionCategory(node: TimelineNodeViewModel): ExecutionCategory {
        if (node.kind == TimelineNodeKind.COMMAND_STEP) return ExecutionCategory.RUN_COMMAND
        if (node.kind == TimelineNodeKind.FAILURE && !node.command.isNullOrBlank()) return ExecutionCategory.RUN_COMMAND
        val normalized = node.toolName
            ?.lowercase()
            .orEmpty()
            .removeSuffix("_call_output")
            .removeSuffix("_call")
            .removeSuffix("_output")
        return when {
            normalized == "shell" || normalized.contains("shell_call") ||
                normalized == "local_shell" || normalized.contains("terminal") || normalized.contains("run_command") ||
                normalized == "bash" || normalized == "zsh" || normalized == "sh" ->
                ExecutionCategory.RUN_COMMAND
            normalized == "file_search" -> ExecutionCategory.FILE_SEARCH
            normalized == "web_search" -> ExecutionCategory.WEB_SEARCH
            normalized == "tool_search" -> ExecutionCategory.SEARCH_FILE
            normalized == "function" || normalized == "function_call" -> ExecutionCategory.FUNCTION_CALL
            normalized == "mcp" -> ExecutionCategory.MCP_CALL
            normalized.contains("code_interpreter") -> ExecutionCategory.CODE_INTERPRETER
            normalized.contains("computer") || normalized.contains("computer_use") -> ExecutionCategory.COMPUTER_USE
            normalized.contains("image_generation") -> ExecutionCategory.IMAGE_GENERATION
            normalized.contains("read") || normalized.contains("view") || normalized.contains("open_file") -> ExecutionCategory.READ_FILE
            normalized.contains("grep") || normalized.contains("search") || normalized.contains("find") || normalized.contains("glob") -> ExecutionCategory.SEARCH_FILE
            normalized.contains("file_change") || normalized.contains("edit") || normalized.contains("patch") || normalized.contains("apply") || normalized.contains("write") || normalized.contains("create") -> ExecutionCategory.EDIT_FILE
            else -> ExecutionCategory.GENERIC_TOOL
        }
    }

    private fun executionTitleSegments(node: TimelineNodeViewModel): List<ExecutionTitleSegment> {
        val category = executionCategory(node)
        val fileName = node.filePath?.let { java.io.File(it).name }
        val delta = extractDiffDelta(node)
        val collapsedSummary = collapsedSummaryForNode(node, category)
        val rawCommand = node.command ?: node.toolInput ?: node.body
        return when {
            category == ExecutionCategory.EDIT_FILE && fileName != null -> buildList {
                add(ExecutionTitleSegment(category.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f))
                add(ExecutionTitleSegment(fileName, Colors.ACCENT_TEXT, strong = true, size = 12.8f))
                if (delta != null) {
                    add(ExecutionTitleSegment(delta, Colors.SUCCESS, strong = true, size = 12f))
                } else if (collapsedSummary.isNotBlank()) {
                    add(ExecutionTitleSegment(collapsedSummary, Colors.TEXT_MUTED, size = 11.4f))
                }
            }

            category == ExecutionCategory.READ_FILE || category == ExecutionCategory.SEARCH_FILE -> buildList {
                add(ExecutionTitleSegment(category.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f))
                add(ExecutionTitleSegment(fileName ?: collapsedSummary, Colors.ACCENT_TEXT, strong = true, size = 12.8f))
            }

            category == ExecutionCategory.RUN_COMMAND -> buildList {
                val executable = extractExecutableName(rawCommand)
                add(ExecutionTitleSegment(category.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f))
                add(ExecutionTitleSegment(executable, Colors.ACCENT_TEXT, strong = true, size = 12.8f))
                val details = compactCommand(rawCommand)
                if (details.isNotBlank() && !details.equals(executable, ignoreCase = true)) {
                    add(ExecutionTitleSegment(details, Colors.TEXT_MUTED, size = 11.3f))
                }
            }

            category != ExecutionCategory.GENERIC_TOOL -> buildList {
                add(ExecutionTitleSegment(category.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f))
                add(ExecutionTitleSegment(collapsedSummary, Colors.TEXT_PRIMARY, strong = true, size = 12.4f))
            }

            node.kind == TimelineNodeKind.TOOL_STEP && fileName != null -> buildList {
                add(ExecutionTitleSegment(ExecutionCategory.GENERIC_TOOL.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f))
                add(ExecutionTitleSegment(fileName, Colors.ACCENT_TEXT, strong = true, size = 12.8f))
            }

            else -> listOf(
                ExecutionTitleSegment(ExecutionCategory.GENERIC_TOOL.label, Colors.TEXT_SECONDARY, strong = true, size = 11.8f),
                ExecutionTitleSegment(collapsedSummary, Colors.TEXT_PRIMARY, strong = true, size = 12.3f),
            )
        }
    }

    private fun collapsedSummaryForNode(node: TimelineNodeViewModel, category: ExecutionCategory): String {
        return when (category) {
            ExecutionCategory.RUN_COMMAND -> compactCommand(node.command ?: node.toolInput ?: node.body)
            ExecutionCategory.READ_FILE,
            ExecutionCategory.EDIT_FILE,
            -> node.filePath?.let { java.io.File(it).name }
                ?: extractPathLikeName(node.toolInput)
                ?: compactText(node.toolInput ?: node.body)

            ExecutionCategory.SEARCH_FILE,
            ExecutionCategory.FILE_SEARCH,
            ExecutionCategory.WEB_SEARCH,
            -> extractQueryText(node.toolInput)
                ?: compactText(node.toolInput ?: node.body)

            ExecutionCategory.FUNCTION_CALL,
            ExecutionCategory.MCP_CALL,
            ExecutionCategory.CODE_INTERPRETER,
            ExecutionCategory.COMPUTER_USE,
            ExecutionCategory.IMAGE_GENERATION,
            -> normalizeToolDisplayName(node.toolName)
                ?: compactText(node.toolInput ?: node.body)

            ExecutionCategory.GENERIC_TOOL -> normalizeToolDisplayName(node.toolName)
                ?: compactText(node.toolInput ?: node.body.ifBlank { node.title })
        }
    }

    private fun extractQueryText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val line = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isBlank()) return null
        val query = Regex("""(?i)query\s*[:=]\s*(.+)$""").find(line)?.groupValues?.getOrNull(1)?.trim()
        return compactText(query ?: line)
    }

    private fun extractPathLikeName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""([A-Za-z0-9_./-]+\.(kt|kts|java|xml|md|json|yaml|yml|js|ts|tsx|jsx|py|go|rs))""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return java.io.File(match).name
    }

    private fun normalizeToolDisplayName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .trim()
            .substringAfterLast(':')
            .substringAfterLast('/')
            .removeSuffix("_call_output")
            .removeSuffix("_call")
            .removeSuffix("_output")
            .replace('_', ' ')
            .ifBlank { null }
            ?.let(::compactText)
    }

    private fun extractDiffDelta(node: TimelineNodeViewModel): String? {
        val text = listOfNotNull(node.toolInput, node.toolOutput, node.body).joinToString("\n")
        val added = Regex("""\+(\d+)""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val removed = Regex("""-(\d+)""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return when {
            added != null && removed != null -> "+$added -$removed"
            added != null -> "+$added"
            removed != null -> "-$removed"
            else -> null
        }
    }

    private fun compactCommand(command: String?): String {
        val normalized = command.orEmpty().trim()
        if (normalized.isBlank()) return "命令"
        val firstLine = normalized.lineSequence().first().trim()
        return firstLine.take(24).let { line ->
            if (line.length == firstLine.length) line else "$line…"
        }
    }

    private fun extractExecutableName(command: String?): String {
        val normalized = command.orEmpty().trim()
        if (normalized.isBlank()) return "命令"
        val shellScript = extractShellScript(normalized)
        val source = shellScript ?: normalized
        val segments = source.split(Regex("""\s*(?:&&|\|\||;|\|)\s*"""))
        val candidate = segments
            .asSequence()
            .map { it.trim() }
            .firstOrNull { segment ->
                segment.isNotBlank() &&
                    !segment.startsWith("cd ") &&
                    !segment.startsWith("export ") &&
                    !segment.startsWith("set ")
            }
            ?: source
        val tokens = candidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "命令"
        val firstToken = if (tokens.first() == "env") {
            tokens.drop(1).firstOrNull { !it.contains('=') } ?: tokens.first()
        } else {
            tokens.first()
        }
        return firstToken
            .removePrefix("./")
            .substringAfterLast('/')
            .ifBlank { "命令" }
    }

    private fun extractShellScript(command: String): String? {
        val patterns = listOf(
            Regex("""^(?:/bin/)?(?:zsh|bash|sh)\s+-lc\s+(.+)$"""),
            Regex("""^cmd\s+/c\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^powershell(?:\.exe)?\s+-Command\s+(.+)$""", RegexOption.IGNORE_CASE),
        )
        val rawScript = patterns.firstNotNullOfOrNull { regex ->
            regex.find(command)?.groupValues?.getOrNull(1)
        } ?: return null
        return rawScript
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
            .ifBlank { null }
    }

    private fun compactText(text: String?): String {
        val normalized = text.orEmpty().trim()
        if (normalized.isBlank()) return "详情"
        val firstLine = normalized.lineSequence().first().trim()
        return firstLine.take(24).let { line ->
            if (line.length == firstLine.length) line else "$line…"
        }
    }

    private fun isFileMutationTool(toolName: String?): Boolean {
        val normalized = toolName?.lowercase().orEmpty()
        return normalized.contains("edit") ||
            normalized.contains("file_change") ||
            normalized.contains("write") ||
            normalized.contains("patch") ||
            normalized.contains("apply")
    }

    private fun isFileMutationNode(node: TimelineNodeViewModel): Boolean {
        return node.filePath != null && isFileMutationTool(node.toolName)
    }

    private fun inlineLabel(node: TimelineNodeViewModel): String {
        return when (node.kind) {
            TimelineNodeKind.ASSISTANT_NOTE -> "助手"
            TimelineNodeKind.THINKING -> "思考"
            TimelineNodeKind.RESULT -> "结果"
            TimelineNodeKind.FAILURE -> "失败"
            TimelineNodeKind.SYSTEM_AUX -> "系统"
            TimelineNodeKind.TOOL_STEP -> "执行"
            TimelineNodeKind.COMMAND_STEP -> "执行"
        }
    }

    private fun narrativeTitleColor(presentation: TimelineNodePresentation): Color {
        return when (presentation.chrome) {
            TimelineNodeChrome.ALERT -> Colors.FAILURE_TEXT
            TimelineNodeChrome.RESULT -> Colors.RESULT_TEXT
            TimelineNodeChrome.SUPPORTING -> Colors.TEXT_SECONDARY
            else -> Colors.TEXT_SECONDARY
        }
    }

    private fun narrativeBodyColor(presentation: TimelineNodePresentation): Color {
        return when (presentation.chrome) {
            TimelineNodeChrome.ALERT -> Colors.FAILURE_TEXT
            TimelineNodeChrome.SUPPORTING -> Colors.TEXT_SECONDARY
            else -> Colors.TEXT_PRIMARY
        }
    }

    private data class ScrollAnchor(
        val nodeId: String,
        val relativeTop: Int,
    )

    private fun captureAnchor(nodeId: String): ScrollAnchor? {
        val card = findNodeCard(nodeId) ?: return null
        val relativeTop = card.y - scrollPane.viewport.viewPosition.y
        return ScrollAnchor(nodeId = nodeId, relativeTop = relativeTop)
    }

    private fun restoreAnchorAfterLayout(anchor: ScrollAnchor): Boolean {
        layoutHierarchy(this)
        val card = findNodeCard(anchor.nodeId) ?: return false
        val targetY = (card.y - anchor.relativeTop).coerceAtLeast(0)
        setViewportY(targetY)
        return true
    }

    private fun setViewportY(targetY: Int) {
        val viewport = scrollPane.viewport ?: return
        val view = viewport.view ?: return
        val maxY = (view.preferredSize.height - viewport.extentSize.height).coerceAtLeast(0)
        viewport.viewPosition = Point(0, targetY.coerceIn(0, maxY))
    }

    private fun findNodeCard(nodeId: String): JPanel? {
        return findNamedPanel(contentPanel, "node-card-$nodeId")
    }

    private fun findNamedPanel(component: Container, name: String): JPanel? {
        component.components.forEach { child ->
            if (child is JPanel && child.name == name) {
                return child
            }
            if (child is Container) {
                val nested = findNamedPanel(child, name)
                if (nested != null) {
                    return nested
                }
            }
        }
        return null
    }

    private fun layoutHierarchy(component: java.awt.Component) {
        if (component is Container) {
            component.doLayout()
            component.validate()
            component.components.forEach(::layoutHierarchy)
        }
    }

    private fun formatClockTime(epochMs: Long): String {
        val instant = java.time.Instant.ofEpochMilli(epochMs)
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }

    private fun formatDuration(durationMs: Long): String {
        return ToolWindowUiText.formatDuration(durationMs)
    }

    private fun wrapHtmlBody(
        bodyHtml: String,
        foreground: Color,
        fontSize: Float,
    ): String {
        val fontFamily = UIManager.getFont("Label.font")?.family ?: "SansSerif"
        return """
            <html>
              <head>
                <style>
                  body {
                    margin: 0;
                    color: ${toCssColor(foreground)};
                    font-family: '$fontFamily';
                    font-size: ${fontSize.toInt()}px;
                    line-height: 1.55;
                  }
                  p, ul, ol, pre, blockquote, h1, h2, h3, h4, h5, h6 { margin: 0 0 8px 0; }
                  ul, ol { padding-left: 20px; }
                  li + li { margin-top: 4px; }
                  pre {
                    white-space: pre-wrap;
                    border: 1px solid ${toCssColor(Colors.CODE_BORDER)};
                    background: ${toCssColor(Colors.CODE_BG)};
                    color: ${toCssColor(Colors.TEXT_PRIMARY)};
                    padding: 8px 10px;
                    border-radius: 8px;
                    overflow-wrap: anywhere;
                  }
                  code {
                    font-family: 'SF Mono', Menlo, Consolas, monospace;
                    background: ${toCssColor(Colors.CODE_BG)};
                    color: ${toCssColor(Colors.TEXT_PRIMARY)};
                    padding: 1px 4px;
                    border-radius: 4px;
                  }
                  pre code {
                    background: transparent;
                    padding: 0;
                  }
                  blockquote {
                    border-left: 3px solid ${toCssColor(Colors.EXECUTION_BORDER)};
                    padding-left: 10px;
                    color: ${toCssColor(Colors.TEXT_SECONDARY)};
                  }
                  a { color: ${toCssColor(Colors.LINK)}; }
                </style>
              </head>
              <body>$bodyHtml</body>
            </html>
        """.trimIndent()
    }

    private fun toCssColor(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }

    private class MarkdownPane(
        html: String,
    ) : JEditorPane("text/html", html) {
        init {
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            isFocusable = false
        }

        override fun getPreferredSize(): Dimension {
            val availableWidth = resolveAvailableWidth()
            setSize(availableWidth, Int.MAX_VALUE / 4)
            val preferred = super.getPreferredSize()
            return Dimension(availableWidth, preferred.height)
        }

        override fun getMaximumSize(): Dimension = preferredSize

        private fun resolveAvailableWidth(): Int {
            val baseWidth = ancestorWidths().minOrNull() ?: 320
            return (baseWidth - 56).coerceAtLeast(120)
        }

        private fun ancestorWidths(): List<Int> {
            val widths = mutableListOf<Int>()
            var current: Container? = parent
            while (current != null) {
                val width = when (current) {
                    is JViewport -> current.extentSize.width
                    else -> current.width
                }
                if (width > 0) {
                    widths += width
                }
                current = current.parent
            }
            return widths
        }
    }

    private class TimelineContentPanel : JPanel(), Scrollable {
        override fun getPreferredSize(): Dimension {
            val preferred = super.getPreferredSize()
            val viewportWidth = (parent as? JViewport)?.extentSize?.width ?: 0
            return if (viewportWidth > 0) {
                Dimension(minOf(preferred.width, viewportWidth), preferred.height)
            } else {
                preferred
            }
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle?,
            orientation: Int,
            direction: Int,
        ): Int = 24

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle?,
            orientation: Int,
            direction: Int,
        ): Int = visibleRect?.height ?: 120

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private object Colors {
        val APP_BG = AssistantUiTheme.APP_BG
        val TASK_BG = JBColor(0x1554A6, 0x1554A6)
        val TASK_BORDER = JBColor(0x3678D4, 0x3678D4)
        val TASK_TEXT = JBColor(0xF6FAFF, 0xF6FAFF)
        val TASK_META = JBColor(0xC4DCF8, 0xC4DCF8)
        val EXECUTION_BG = JBColor(0x1B2027, 0x1B2027)
        val EXECUTION_BORDER = JBColor(0x2A313C, 0x2A313C)
        val RESULT_TEXT = JBColor(0xD8F3E4, 0xD8F3E4)
        val CODE_BG = AssistantUiTheme.SURFACE_SUBTLE
        val CODE_BORDER = AssistantUiTheme.BORDER_STRONG
        val ACTION_BG = JBColor(0x222831, 0x222831)
        val ACTION_BORDER = JBColor(0x313948, 0x313948)
        val ACTION_TEXT = JBColor(0xC9D3E2, 0xC9D3E2)
        val ACCENT_TEXT = JBColor(0x67A9FF, 0x67A9FF)
        val TEXT_PRIMARY = AssistantUiTheme.TEXT_PRIMARY
        val TEXT_SECONDARY = AssistantUiTheme.TEXT_SECONDARY
        val TEXT_TERTIARY = JBColor(0x6D7686, 0x6D7686)
        val TEXT_MUTED = AssistantUiTheme.TEXT_MUTED
        val LINK = AssistantUiTheme.ACCENT
        val META = AssistantUiTheme.TEXT_MUTED
        val SUCCESS = AssistantUiTheme.SUCCESS
        val WARNING = AssistantUiTheme.WARNING
        val FAILURE = AssistantUiTheme.DANGER
        val FAILURE_TEXT = JBColor(0xF3C3C7, 0xF3C3C7)
        val MUTED = AssistantUiTheme.TEXT_MUTED
    }
}
