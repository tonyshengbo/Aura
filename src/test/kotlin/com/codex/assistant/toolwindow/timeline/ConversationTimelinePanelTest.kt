package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.MessageRole
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversationTimelinePanelTest {
    @Test
    fun `user message actions include retry and invoke callback`() {
        var retriedContent: String? = null
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onRetryMessage = { retriedContent = it },
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-retry-user-message",
                    userMessage = ChatMessage(
                        id = "user-retry",
                        role = MessageRole.USER,
                        content = "Retry this exact prompt",
                        timestamp = 1_000L,
                    ),
                    nodes = emptyList(),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )
        panel.setSize(560, 300)
        layoutHierarchy(panel)

        val userBlock = findNamedPanel(panel, "task-block-user-retry")
        assertNotNull(userBlock)
        val retryButton = collectButtons(userBlock).firstOrNull { it.text == "重试" }
        assertNotNull(retryButton)
        retryButton.doClick()

        assertEquals("Retry this exact prompt", retriedContent)
    }

    @Test
    fun `user prompts render as task request blocks instead of chat bubbles`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-task",
                    userMessage = ChatMessage(
                        id = "user-task",
                        role = MessageRole.USER,
                        content = "Refine the timeline layout to match the IDE plugin style.",
                        timestamp = 1_000L,
                    ),
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "result-task",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "Working on it.",
                            status = TimelineNodeStatus.RUNNING,
                            expanded = true,
                        ),
                    ),
                    isRunning = true,
                    footerStatus = TimelineNodeStatus.RUNNING,
                    statusText = "Loading",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val mainPanel = firstTurnMainPanel(panel)
        val taskBlock = mainPanel.components.filterIsInstance<JPanel>().first()

        assertFalse(containsRightAlignedBubble(taskBlock))
        assertTrue(containsLeftAlignedBubble(taskBlock))
        assertFalse(collectLabels(taskBlock).contains("Task"))
    }

    @Test
    fun `execution nodes start collapsed even when the view model is expanded`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-collapsed",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "command-collapsed",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "Command output line that should stay hidden until expansion.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                            command = "adb devices",
                            cwd = "/tmp/project",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val commandCard = findNodeCard(panel, "command-collapsed")

        assertFalse(collectLabels(commandCard).contains("Command"))
        assertFalse(collectEditorMarkup(commandCard).any { it.contains("Command output line that should stay hidden until expansion.") })
    }

    @Test
    fun `file mutation cards prioritize diff access over generic open action`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-diff",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-diff",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "edit_file",
                            body = "Applied diff to src/main/kotlin/com/example/LocalSplash.kt",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "edit_file",
                            toolInput = "src/main/kotlin/com/example/LocalSplash.kt",
                            toolOutput = "Applied diff",
                            filePath = "src/main/kotlin/com/example/LocalSplash.kt",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val mutationCard = findNodeCard(panel, "tool-diff")
        val buttonLabels = collectButtons(mutationCard).map { it.text }

        assertTrue(collectLabels(mutationCard).any { it.contains("LocalSplash.kt") })
        assertTrue(buttonLabels.contains("◧"))
        assertFalse(buttonLabels.contains("↗"))
    }

    @Test
    fun `turn rows do not reserve a dedicated left timeline gutter`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-1",
                    userMessage = ChatMessage(
                        id = "user-1",
                        role = MessageRole.USER,
                        content = "Inspect the current layout",
                        timestamp = 1_000L,
                    ),
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "result-1",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "Completed.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        val contentPanel = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .view as JPanel
        val turnPanel = contentPanel.components.filterIsInstance<JPanel>().first(::isTurnPanel)

        assertEquals(1, turnPanel.componentCount)
    }

    @Test
    fun `turn content renders nodes in direct sequence without summary grouping`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-2",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "note-1",
                            kind = TimelineNodeKind.ASSISTANT_NOTE,
                            title = "Assistant",
                            body = "I inspected the file.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "tool-1",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "read_file",
                            body = "src/App.kt",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                        ),
                        TimelineNodeViewModel(
                            id = "result-1",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "Done.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        val contentPanel = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .view as JPanel
        val turnPanel = contentPanel.components.filterIsInstance<JPanel>().first(::isTurnPanel)
        val mainPanel = turnPanel.components.single() as JPanel

        assertTrue(mainPanel.componentCount >= 5)
        assertFalse(collectLabels(mainPanel).contains("Assistant Summary"))
        assertFalse(collectLabels(mainPanel).contains("Execution Trace"))
    }

    @Test
    fun `markdown views shrink with narrower viewport after resize`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-3",
                    userMessage = ChatMessage(
                        id = "user-3",
                        role = MessageRole.USER,
                        content = "This is a long user prompt that should wrap when the tool window becomes narrower.",
                        timestamp = 3_000L,
                    ),
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "result-3",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "This is a long result body that should also wrap instead of preserving an older preferred width.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(900, 700)
        layoutHierarchy(panel)
        panel.setSize(320, 700)
        layoutHierarchy(panel)

        val viewportWidth = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .extentSize
            .width
        val widestMarkdown = collectEditors(panel).maxOf { it.preferredSize.width }

        assertTrue(widestMarkdown <= viewportWidth, "markdown preferred width should shrink to viewport width")
    }

    @Test
    fun `expanding command details does not widen timeline beyond viewport`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        fun commandTurn(expanded: Boolean) = listOf(
            TimelineTurnViewModel(
                id = "turn-4",
                userMessage = null,
                nodes = listOf(
                    TimelineNodeViewModel(
                        id = "command-4",
                        kind = TimelineNodeKind.COMMAND_STEP,
                        title = "Command",
                        body = "Command output line that should wrap inside the viewport.",
                        status = TimelineNodeStatus.RUNNING,
                        expanded = expanded,
                        command = "/bin/zsh -lc 'cd /Users/tonysheng/StudioProject/Codex-Assistant && ./gradlew runIde --stacktrace'",
                        cwd = "/Users/tonysheng/StudioProject/Codex-Assistant/.worktrees/ui-structured-console",
                    ),
                ),
                isRunning = true,
                footerStatus = TimelineNodeStatus.RUNNING,
                statusText = "Loading",
            ),
        )

        panel.updateTurns(
            turns = commandTurn(expanded = false),
            forceAutoScroll = false,
        )

        panel.setSize(360, 700)
        layoutHierarchy(panel)
        panel.updateTurns(
            turns = commandTurn(expanded = true),
            forceAutoScroll = false,
        )
        layoutHierarchy(panel)
        click(findNodeCard(panel, "command-4"))
        layoutHierarchy(panel)

        val scrollPane = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
        val viewportWidth = scrollPane.viewport.extentSize.width
        val contentWidth = (scrollPane.viewport.view as JPanel).preferredSize.width
        val widestTextArea = collectTextAreas(panel).maxOf { it.preferredSize.width }

        assertTrue(contentWidth <= viewportWidth, "expanded command card should stay within viewport width")
        assertTrue(widestTextArea <= viewportWidth, "expanded command text should wrap within viewport width")
    }

    @Test
    fun `command cards toggle by clicking the card without dedicated expand buttons`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-4b",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "command-4b",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "Command output line that should only appear after expanding the card.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            command = "/bin/zsh -lc 'cd /Users/tonysheng/StudioProject/Codex-Assistant && ./gradlew test'",
                            cwd = "/Users/tonysheng/StudioProject/Codex-Assistant/.worktrees/ui-structured-console",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(480, 700)
        layoutHierarchy(panel)

        assertFalse(collectButtons(panel).any { it.text == "Expand" || it.text == "Collapse" })
        assertFalse(collectLabels(firstNodeCard(panel)).contains("命令"))

        click(firstNodeCard(panel))
        layoutHierarchy(panel)

        assertTrue(collectLabels(firstNodeCard(panel)).contains("命令"))
    }

    @Test
    fun `text explanation cards stay expanded and do not expose collapse affordance`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-4c",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "result-4c",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "First line of explanation.\nSecond line should stay visible.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(480, 700)
        layoutHierarchy(panel)

        assertFalse(collectButtons(panel).any { it.text == "Expand" || it.text == "Collapse" })
        assertTrue(collectEditorMarkup(panel).any { it.contains("Second line should stay visible.") })

        click(firstNodeCard(panel))
        layoutHierarchy(panel)

        assertTrue(collectEditorMarkup(panel).any { it.contains("Second line should stay visible.") })
    }

    @Test
    fun `lightweight nodes render as inline log rows instead of opaque cards`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-inline",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "assistant-inline",
                            kind = TimelineNodeKind.ASSISTANT_NOTE,
                            title = "Assistant",
                            body = "Inspecting the current tool window layout.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "result-inline",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "Updated the render path.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "failure-inline",
                            kind = TimelineNodeKind.FAILURE,
                            title = "Failure",
                            body = "The command exited with code 1.",
                            status = TimelineNodeStatus.FAILED,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "system-inline",
                            kind = TimelineNodeKind.SYSTEM_AUX,
                            title = "System",
                            body = "Applied diff to src/App.kt",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        assertFalse(findNodeCard(panel, "assistant-inline").isOpaque)
        assertFalse(findNodeCard(panel, "result-inline").isOpaque)
        assertFalse(findNodeCard(panel, "failure-inline").isOpaque)
        assertFalse(findNodeCard(panel, "system-inline").isOpaque)
    }

    @Test
    fun `timeline chrome does not expose raw english labels`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-localized",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "assistant-localized",
                            kind = TimelineNodeKind.ASSISTANT_NOTE,
                            title = "Assistant",
                            body = "Inspecting the timeline styling.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "result-localized",
                            kind = TimelineNodeKind.RESULT,
                            title = "Result",
                            body = "Updated the composer styling.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        ),
                        TimelineNodeViewModel(
                            id = "failure-localized",
                            kind = TimelineNodeKind.FAILURE,
                            title = "Failure",
                            body = "A command failed.",
                            status = TimelineNodeStatus.FAILED,
                            expanded = true,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val labels = collectLabels(panel)
        assertFalse(labels.contains("ASSISTANT"))
        assertFalse(labels.contains("RESULT"))
        assertFalse(labels.contains("FAILED"))
    }

    @Test
    fun `execution details use localized meta copy instead of english command labels`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-command-localized",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "command-localized",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "Command output line that should appear after expanding.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                            command = "git status --short",
                            cwd = "/tmp/project",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)
        click(findNodeCard(panel, "command-localized"))
        layoutHierarchy(panel)

        val labels = collectLabels(findNodeCard(panel, "command-localized"))
        assertFalse(labels.contains("COMMAND"))
        assertFalse(labels.contains("OUTPUT"))
        assertFalse(labels.any { it.contains("done") })
    }

    @Test
    fun `collapsed execution rows stay as single line evidence bars`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-flat-bars",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-flat",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "read_file",
                            body = "src/main/kotlin/com/example/LocalSplash.kt",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "read_file",
                            toolInput = "src/main/kotlin/com/example/LocalSplash.kt",
                            toolOutput = "scanned file structure",
                            filePath = "src/main/kotlin/com/example/LocalSplash.kt",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val toolCard = findNodeCard(panel, "tool-flat")
        assertFalse(collectEditorMarkup(toolCard).any { it.contains("scanned file structure") })
        assertFalse(collectEditorMarkup(toolCard).any { it.contains("src/main/kotlin/com/example/LocalSplash.kt") })
    }

    @Test
    fun `failed commands keep execution chrome instead of falling back to narrative alert`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-failed-command",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "failed-command",
                            kind = TimelineNodeKind.FAILURE,
                            title = "Failure",
                            body = "ADB server didn't ACK",
                            status = TimelineNodeStatus.FAILED,
                            expanded = true,
                            command = "adb devices",
                            cwd = "/tmp/project",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.FAILED,
                    statusText = "Failed",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val failedCard = findNodeCard(panel, "failed-command")
        assertTrue(failedCard.isOpaque)
        assertTrue(collectLabels(failedCard).any { it.contains("运行命令") })
    }

    @Test
    fun `command title uses executable name for clearer collapsed summary`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-command-title",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "cmd-title",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "BUILD SUCCESSFUL",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            command = "/bin/zsh -lc 'cd /tmp/project && ./gradlew test --no-daemon'",
                            cwd = "/tmp/project",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(560, 760)
        layoutHierarchy(panel)

        val labels = collectLabels(findNodeCard(panel, "cmd-title"))
        assertTrue(labels.any { it.contains("运行命令") })
        assertTrue(labels.any { it.contains("gradlew") })
    }

    @Test
    fun `shell tool calls are categorized as run command entries`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-shell-tool",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-shell",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "shell",
                            body = "BUILD SUCCESSFUL",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "shell",
                            toolInput = "/bin/zsh -lc 'cd /tmp/project && ./gradlew test --no-daemon'",
                            toolOutput = "BUILD SUCCESSFUL",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(560, 760)
        layoutHierarchy(panel)

        val labels = collectLabels(findNodeCard(panel, "tool-shell"))
        assertTrue(labels.any { it.contains("运行命令") })
        assertTrue(labels.any { it.contains("gradlew") })
    }

    @Test
    fun `expanded command details do not render running subtitle row`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-command-subtitle-clean",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "cmd-subtitle-clean",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "using-superpowers skill content",
                            status = TimelineNodeStatus.RUNNING,
                            expanded = true,
                            command = "/bin/zsh -lc \"cat /tmp/SKILL.md\"",
                            cwd = "/tmp",
                            exitCode = 0,
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(560, 760)
        layoutHierarchy(panel)

        val labels = collectLabels(findNodeCard(panel, "cmd-subtitle-clean"))
        assertFalse(labels.any { it.contains("进行中") })
        assertFalse(labels.any { it.contains("已完成") })
    }

    @Test
    fun `web search without call suffix still uses dedicated retrieval category`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-web-search",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-web-search",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "web_search",
                            body = "fetched 4 urls",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "web_search",
                            toolInput = "query: Codex tools guide",
                            toolOutput = "fetched 4 urls",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val labels = collectLabels(findNodeCard(panel, "tool-web-search"))
        assertTrue(labels.any { it.contains("网页检索") })
    }

    @Test
    fun `remaining official tool names map to unified collapsed execution categories`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-official-types",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-tool-search",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "tool_search",
                            body = "matched files",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "tool_search",
                            toolInput = "query: timeline",
                        ),
                        TimelineNodeViewModel(
                            id = "tool-mcp",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "mcp",
                            body = "ok",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "mcp",
                            toolInput = "server: docs-index",
                        ),
                        TimelineNodeViewModel(
                            id = "tool-computer",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "computer",
                            body = "click button",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "computer",
                            toolInput = "click x=10 y=20",
                        ),
                        TimelineNodeViewModel(
                            id = "tool-code-interpreter",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "code_interpreter",
                            body = "1",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "code_interpreter",
                            toolInput = "print(1)",
                        ),
                        TimelineNodeViewModel(
                            id = "tool-image-generation",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "image_generation",
                            body = "image_id",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "image_generation",
                            toolInput = "draw a cat",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(620, 820)
        layoutHierarchy(panel)

        assertTrue(collectLabels(findNodeCard(panel, "tool-tool-search")).any { it.contains("搜索文件") })
        assertTrue(collectLabels(findNodeCard(panel, "tool-mcp")).any { it.contains("MCP 工具") })
        assertTrue(collectLabels(findNodeCard(panel, "tool-computer")).any { it.contains("计算机操作") })
        assertTrue(collectLabels(findNodeCard(panel, "tool-code-interpreter")).any { it.contains("代码解释器") })
        assertTrue(collectLabels(findNodeCard(panel, "tool-image-generation")).any { it.contains("图像生成") })
    }

    @Test
    fun `file search tool calls render with dedicated file retrieval category`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-file-search",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-file-search",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "file_search_call",
                            body = "matched 3 files",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "file_search_call",
                            toolInput = "query: adb devices",
                            toolOutput = "matched 3 files",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val card = findNodeCard(panel, "tool-file-search")
        assertTrue(collectLabels(card).any { it.contains("文件检索") })
        assertFalse(collectLabels(card).any { it.contains("file_search_call") })
    }

    @Test
    fun `function call tool calls render with dedicated function category`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-function-call",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "tool-function-call",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "function_call",
                            body = "invoked update_session_metadata",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "function_call",
                            toolInput = "{\"session_id\":\"abc\"}",
                            toolOutput = "{\"ok\":true}",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val card = findNodeCard(panel, "tool-function-call")
        assertTrue(collectLabels(card).any { it.contains("调用函数") })
        assertFalse(collectLabels(card).any { it.contains("function_call") })
    }

    @Test
    fun `adjacent edit tool calls remain independent entries`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-grouped-edit",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "edit-a",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "edit_file",
                            body = "Applied diff +14",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "edit_file",
                            toolInput = "src/A.kt",
                            toolOutput = "Applied diff +14",
                            filePath = "src/A.kt",
                        ),
                        TimelineNodeViewModel(
                            id = "edit-b",
                            kind = TimelineNodeKind.TOOL_STEP,
                            title = "edit_file",
                            body = "Applied diff -6",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            toolName = "edit_file",
                            toolInput = "src/B.kt",
                            toolOutput = "Applied diff -6",
                            filePath = "src/B.kt",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(520, 700)
        layoutHierarchy(panel)

        val firstCard = findNodeCard(panel, "edit-a")
        val secondCard = findNodeCard(panel, "edit-b")
        assertTrue(collectLabels(firstCard).any { it.contains("编辑文件") })
        assertTrue(collectLabels(secondCard).any { it.contains("编辑文件") })
    }

    @Test
    fun `multiple command entries can stay expanded independently`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-grouped-command",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "cmd-a",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "first output",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            command = "echo first",
                            cwd = "/tmp",
                        ),
                        TimelineNodeViewModel(
                            id = "cmd-b",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "second output",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = false,
                            command = "echo second",
                            cwd = "/tmp",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(560, 760)
        layoutHierarchy(panel)

        val firstCard = findNodeCard(panel, "cmd-a")
        val secondCard = findNodeCard(panel, "cmd-b")
        click(firstCard)
        layoutHierarchy(panel)
        click(secondCard)
        layoutHierarchy(panel)
        val firstCardAfter = findNodeCard(panel, "cmd-a")
        val secondCardAfter = findNodeCard(panel, "cmd-b")
        assertTrue(collectEditorMarkup(firstCardAfter).any { it.contains("first output") })
        assertTrue(collectEditorMarkup(secondCardAfter).any { it.contains("second output") })
    }

    @Test
    fun `command details shrink with narrower viewport after resize`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-5",
                    userMessage = null,
                    nodes = listOf(
                        TimelineNodeViewModel(
                            id = "command-5",
                            kind = TimelineNodeKind.COMMAND_STEP,
                            title = "Command",
                            body = "Command output line that should wrap inside the viewport after a resize.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                            command = "/bin/zsh -lc 'cd /Users/tonysheng/StudioProject/Codex-Assistant && ./gradlew runIde --stacktrace'",
                            cwd = "/Users/tonysheng/StudioProject/Codex-Assistant/.worktrees/ui-structured-console",
                        ),
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(900, 700)
        layoutHierarchy(panel)
        panel.setSize(320, 700)
        layoutHierarchy(panel)
        click(findNodeCard(panel, "command-5"))
        layoutHierarchy(panel)

        val viewportWidth = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .extentSize
            .width
        val horizontalScrollBar = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .horizontalScrollBar
        val widestTextArea = collectTextAreas(panel).maxOf { it.preferredSize.width }
        val contentPanel = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .view as JPanel
        val maxRightEdge = maxRightEdge(contentPanel)

        assertTrue(widestTextArea <= viewportWidth, "expanded command text should shrink to viewport width")
        assertTrue(maxRightEdge <= viewportWidth, "expanded command layout should stay within viewport bounds")
        assertTrue(
            horizontalScrollBar.maximum <= horizontalScrollBar.visibleAmount,
            "expanded command layout should not require horizontal scrolling",
        )
    }

    @Test
    fun `expanding command card keeps clicked row anchored in viewport`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        val nodes = (1..16).map { index ->
            TimelineNodeViewModel(
                id = "cmd-anchor-$index",
                kind = TimelineNodeKind.COMMAND_STEP,
                title = "Command",
                body = "output-$index",
                status = TimelineNodeStatus.SUCCESS,
                expanded = false,
                command = "echo command-$index",
                cwd = "/tmp",
            )
        }
        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-anchor",
                    userMessage = null,
                    nodes = nodes,
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(560, 420)
        layoutHierarchy(panel)

        val scrollPane = scrollPane(panel)
        scrollPane.verticalScrollBar.value = 140
        layoutHierarchy(panel)
        val beforeViewportY = scrollPane.viewport.viewPosition.y

        val target = findNodeCard(panel, "cmd-anchor-8")
        val beforeRelativeTop = target.y - scrollPane.viewport.viewPosition.y

        click(target)
        layoutHierarchy(panel)

        val targetAfter = findNodeCard(panel, "cmd-anchor-8")
        val afterRelativeTop = targetAfter.y - scrollPane.viewport.viewPosition.y
        val afterViewportY = scrollPane.viewport.viewPosition.y

        assertTrue(kotlin.math.abs(beforeRelativeTop - afterRelativeTop) <= 3)
        assertTrue(kotlin.math.abs(beforeViewportY - afterViewportY) <= 3)
        assertTrue(collectLabels(targetAfter).contains("命令"))
    }

    @Test
    fun `rerender after resize uses current narrow width for markdown layouts`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        fun turn(expanded: Boolean) = listOf(
            TimelineTurnViewModel(
                id = "turn-6",
                userMessage = ChatMessage(
                    id = "user-6",
                    role = MessageRole.USER,
                    content = "This is a long user prompt that should still wrap after the tool window is narrowed and a command row is expanded.",
                    timestamp = 6_000L,
                ),
                nodes = listOf(
                    TimelineNodeViewModel(
                        id = "assistant-6",
                        kind = TimelineNodeKind.ASSISTANT_NOTE,
                        title = "Assistant",
                        body = "I am preparing to inspect the file and then run a command.",
                        status = TimelineNodeStatus.SUCCESS,
                        expanded = true,
                    ),
                    TimelineNodeViewModel(
                        id = "command-6",
                        kind = TimelineNodeKind.COMMAND_STEP,
                        title = "Command",
                        body = "Command output that should remain wrapped.",
                        status = TimelineNodeStatus.SUCCESS,
                        expanded = expanded,
                        command = "/bin/zsh -lc 'cd /Users/tonysheng/StudioProject/Codex-Assistant && ./gradlew runIde --stacktrace'",
                        cwd = "/Users/tonysheng/StudioProject/Codex-Assistant/.worktrees/ui-structured-console",
                    ),
                ),
                isRunning = false,
                footerStatus = TimelineNodeStatus.SUCCESS,
                statusText = "Done",
            ),
        )

        panel.updateTurns(
            turns = turn(expanded = false),
            forceAutoScroll = false,
        )
        panel.setSize(900, 700)
        layoutHierarchy(panel)

        panel.setSize(320, 700)
        panel.updateTurns(
            turns = turn(expanded = true),
            forceAutoScroll = false,
        )
        layoutHierarchy(panel)
        click(findNodeCard(panel, "command-6"))
        layoutHierarchy(panel)

        val scrollPane = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
        val viewportWidth = scrollPane.viewport.extentSize.width
        val horizontalScrollBar = scrollPane.horizontalScrollBar
        val contentPanel = scrollPane.viewport.view as JPanel
        val maxRightEdge = maxRightEdge(contentPanel)
        val overflowing = overflowingComponents(contentPanel, viewportWidth).joinToString(" | ")

        assertTrue(
            maxRightEdge <= viewportWidth,
            "rerendered markdown should honor the current narrow viewport (right edge=$maxRightEdge, viewport=$viewportWidth, overflow=$overflowing)",
        )
        assertTrue(
            horizontalScrollBar.maximum <= horizontalScrollBar.visibleAmount,
            "rerender after resize should not introduce horizontal scrolling (max=${horizontalScrollBar.maximum}, visible=${horizontalScrollBar.visibleAmount})",
        )
    }

    @Test
    fun `toggling an execution card keeps its top anchored in the viewport`() {
        val panel = ConversationTimelinePanel(
            onCopyMessage = {},
            onOpenFile = {},
            onRetryTool = { _, _ -> },
            onRetryCommand = { _, _ -> },
            onCopyCommand = {},
        )

        panel.updateTurns(
            turns = listOf(
                TimelineTurnViewModel(
                    id = "turn-anchor",
                    userMessage = null,
                    nodes = List(8) { index ->
                        TimelineNodeViewModel(
                            id = "note-$index",
                            kind = TimelineNodeKind.ASSISTANT_NOTE,
                            title = "Assistant",
                            body = "Narrative block $index that adds vertical height to the timeline.",
                            status = TimelineNodeStatus.SUCCESS,
                            expanded = true,
                        )
                    } + TimelineNodeViewModel(
                        id = "command-anchor",
                        kind = TimelineNodeKind.COMMAND_STEP,
                        title = "Command",
                        body = "Output line 1\nOutput line 2\nOutput line 3\nOutput line 4",
                        status = TimelineNodeStatus.SUCCESS,
                        expanded = false,
                        command = "/bin/zsh -lc 'echo anchored'",
                        cwd = "/tmp",
                    ),
                    isRunning = false,
                    footerStatus = TimelineNodeStatus.SUCCESS,
                    statusText = "Done",
                ),
            ),
            forceAutoScroll = false,
        )

        panel.setSize(420, 260)
        layoutHierarchy(panel)

        val scrollPane = panel.components.filterIsInstance<javax.swing.JScrollPane>().single()
        val targetBefore = findNodeCard(panel, "command-anchor")
        scrollPane.verticalScrollBar.value = (targetBefore.y - 24).coerceAtLeast(0)
        layoutHierarchy(panel)

        val anchoredBefore = findNodeCard(panel, "command-anchor").y - scrollPane.viewport.viewPosition.y

        click(findNodeCard(panel, "command-anchor"))
        layoutHierarchy(panel)

        val anchoredAfter = findNodeCard(panel, "command-anchor").y - scrollPane.viewport.viewPosition.y

        assertEquals(anchoredBefore, anchoredAfter, "expanded execution card should keep the same top anchor")
    }

    private fun isTurnPanel(component: JPanel): Boolean {
        return component.components.any { child: Component ->
            child is JPanel && child.layout is javax.swing.BoxLayout
        }
    }

    private fun collectLabels(component: Component): List<String> {
        return when (component) {
            is JLabel -> listOf(component.text)
            is JPanel -> component.components.flatMap(::collectLabels)
            else -> emptyList()
        }
    }

    private fun collectEditors(component: Component): List<JEditorPane> {
        return when (component) {
            is JEditorPane -> listOf(component)
            is Container -> component.components.flatMap(::collectEditors)
            else -> emptyList()
        }
    }

    private fun collectTextAreas(component: Component): List<JTextArea> {
        return when (component) {
            is JTextArea -> listOf(component)
            is Container -> component.components.flatMap(::collectTextAreas)
            else -> emptyList()
        }
    }

    private fun collectButtons(component: Component): List<JButton> {
        return when (component) {
            is JButton -> listOf(component)
            is Container -> component.components.flatMap(::collectButtons)
            else -> emptyList()
        }
    }

    private fun collectEditorMarkup(component: Component): List<String> {
        return when (component) {
            is JEditorPane -> listOf(component.text)
            is Container -> component.components.flatMap(::collectEditorMarkup)
            else -> emptyList()
        }
    }

    private fun firstNodeCard(panel: ConversationTimelinePanel): JPanel {
        val mainPanel = firstTurnMainPanel(panel)
        return mainPanel.components.filterIsInstance<JPanel>().first()
    }

    private fun firstTurnMainPanel(panel: ConversationTimelinePanel): JPanel {
        val contentPanel = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .view as JPanel
        val turnPanel = contentPanel.components.filterIsInstance<JPanel>().first(::isTurnPanel)
        return turnPanel.components.single() as JPanel
    }

    private fun findNodeCard(panel: ConversationTimelinePanel, nodeId: String): JPanel {
        val contentPanel = panel.components
            .filterIsInstance<javax.swing.JScrollPane>()
            .single()
            .viewport
            .view as JPanel
        return findNamedPanel(contentPanel, "node-card-$nodeId")
            ?: error("Unable to find node card for $nodeId")
    }

    private fun findNamedPanel(component: Component, name: String): JPanel? {
        return when {
            component is JPanel && component.name == name -> component
            component is Container -> component.components.firstNotNullOfOrNull { child ->
                findNamedPanel(child, name)
            }
            else -> null
        }
    }

    private fun click(component: Component) {
        val event = MouseEvent(
            component,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            8,
            8,
            1,
            false,
        )
        component.mouseListeners.forEach { it.mouseClicked(event) }
    }

    private fun layoutHierarchy(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.validate()
            component.components.forEach(::layoutHierarchy)
        }
    }

    private fun containsRightAlignedBubble(component: Component): Boolean {
        return when (component) {
            is JPanel -> {
                val layout = component.layout
                val selfMatches = layout is FlowLayout && layout.alignment == FlowLayout.RIGHT
                selfMatches || component.components.any(::containsRightAlignedBubble)
            }
            is Container -> component.components.any(::containsRightAlignedBubble)
            else -> false
        }
    }

    private fun containsLeftAlignedBubble(component: Component): Boolean {
        return when (component) {
            is JPanel -> {
                val layout = component.layout
                val selfMatches = layout is FlowLayout && layout.alignment == FlowLayout.LEFT
                selfMatches || component.components.any(::containsLeftAlignedBubble)
            }
            is Container -> component.components.any(::containsLeftAlignedBubble)
            else -> false
        }
    }

    private fun scrollPane(panel: ConversationTimelinePanel): javax.swing.JScrollPane {
        return panel.components.filterIsInstance<javax.swing.JScrollPane>().single()
    }

    private fun maxRightEdge(component: Component, offsetX: Int = 0): Int {
        val absoluteRightEdge = offsetX + component.x + component.width
        return if (component is Container && component.componentCount > 0) {
            maxOf(absoluteRightEdge, component.components.maxOf { child ->
                maxRightEdge(child, offsetX + component.x)
            })
        } else {
            absoluteRightEdge
        }
    }

    private fun overflowingComponents(
        component: Component,
        viewportWidth: Int,
        offsetX: Int = 0,
    ): List<String> {
        val absoluteX = offsetX + component.x
        val rightEdge = absoluteX + component.width
        val current = if (rightEdge > viewportWidth) {
            listOf("${component.javaClass.simpleName}(x=$absoluteX,w=${component.width},right=$rightEdge)")
        } else {
            emptyList()
        }
        return if (component is Container && component.componentCount > 0) {
            current + component.components.flatMap { child ->
                overflowingComponents(child, viewportWidth, absoluteX)
            }
        } else {
            current
        }
    }
}
