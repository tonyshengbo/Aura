package com.codex.assistant.toolwindow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.ComposePanel
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnUiState
import com.codex.assistant.service.AgentChatService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ex.ToolWindowEx
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

class ComposeToolWindowPanel(
    private val project: Project,
    toolWindowEx: ToolWindowEx? = null,
) : JPanel(BorderLayout()), Disposable {
    private val chatService = project.getService(AgentChatService::class.java)
    private val store = ToolWindowStore(chatService)
    private val composePanel = ComposePanel()
    private val sessionTabs = SessionTabCoordinator(
        chatService = chatService,
        toolWindowProvider = { toolWindowEx },
        isRunning = { store.state.value.conversation.isRunning },
        onStatus = { },
        onSessionActivated = {
            store.onSessionActivated()
        },
    )

    init {
        sessionTabs.initialize()
        add(composePanel, BorderLayout.CENTER)
        composePanel.setContent {
            MaterialTheme {
                val state by store.state.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    ToolWindowScreen(
                        state = state,
                        onIntent = ::onIntent,
                        onOpenFile = ::openFileInEditor,
                    )
                }
            }
        }
    }

    private fun onIntent(intent: ToolWindowIntent) {
        when (intent) {
            ToolWindowIntent.NewSession -> {
                sessionTabs.startNewSession()
                store.syncSessionsFromService()
                return
            }

            ToolWindowIntent.NewTab -> {
                sessionTabs.startNewWindowTab()
                store.syncSessionsFromService()
                return
            }

            is ToolWindowIntent.SwitchSession -> {
                sessionTabs.switchToSession(intent.sessionId)
                store.syncSessionsFromService()
                return
            }

            else -> Unit
        }

        when (val effect = store.dispatch(intent)) {
            is ToolWindowEffect.RunPrompt -> runPrompt(effect.prompt)
            null -> Unit
        }
    }

    private fun runPrompt(prompt: String) {
        chatService.runAgent(
            engineId = chatService.defaultEngineId(),
            model = "gpt-5.3-codex",
            prompt = prompt,
            contextFiles = emptyList(),
            onUnifiedEvent = { event ->
                ApplicationManager.getApplication().invokeLater {
                    store.onUnifiedEvent(event)
                }
            },
        ) { }
    }

    private fun openFileInEditor(path: String) {
        val basePath = project.basePath ?: return
        val absolute = if (File(path).isAbsolute) path else File(basePath, path).absolutePath
        val vf = LocalFileSystem.getInstance().findFileByPath(absolute) ?: return
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    override fun dispose() = Unit
}

private data class DesignPalette(
    val appBg: Color,
    val topBarBg: Color,
    val topStripBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val timelineCardBg: Color,
    val timelineCardBorder: Color,
    val timelineCardText: Color,
    val timelinePlainText: Color,
    val composerBg: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val accent: Color,
    val success: Color,
    val danger: Color,
)

@Composable
private fun palette(): DesignPalette {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        DesignPalette(
            appBg = Color(0xFF12151B),
            topBarBg = Color(0xFF1A1E26),
            topStripBg = Color(0xFF161A21),
            textPrimary = Color(0xFFE6EBF4),
            textSecondary = Color(0xFFAFB8C9),
            textMuted = Color(0xFF7D879A),
            timelineCardBg = Color(0xFF171B22),
            timelineCardBorder = Color(0xFF2C313C),
            timelineCardText = Color(0xFFD7DEEA),
            timelinePlainText = Color(0xFFE3E8F1),
            composerBg = Color(0xFF141820),
            inputBg = Color(0xFF0F131A),
            inputBorder = Color(0xFF2A3040),
            chipBg = Color(0xFF1B212D),
            chipBorder = Color(0xFF2F3850),
            accent = Color(0xFF4F8BFF),
            success = Color(0xFF6FCF73),
            danger = Color(0xFFF07F84),
        )
    } else {
        DesignPalette(
            appBg = Color(0xFFF5F7FB),
            topBarBg = Color(0xFFECEFF6),
            topStripBg = Color(0xFFE6EAF3),
            textPrimary = Color(0xFF182030),
            textSecondary = Color(0xFF4B5870),
            textMuted = Color(0xFF68758F),
            timelineCardBg = Color(0xFFFFFFFF),
            timelineCardBorder = Color(0xFFD6DDEA),
            timelineCardText = Color(0xFF22304A),
            timelinePlainText = Color(0xFF263248),
            composerBg = Color(0xFFF0F3FA),
            inputBg = Color(0xFFFFFFFF),
            inputBorder = Color(0xFFCCD6E8),
            chipBg = Color(0xFFE9EEF8),
            chipBorder = Color(0xFFCAD6EE),
            accent = Color(0xFF2E6BDE),
            success = Color(0xFF3DAA55),
            danger = Color(0xFFD74D58),
        )
    }
}

@Composable
private fun ToolWindowScreen(
    state: ToolWindowUiState,
    onIntent: (ToolWindowIntent) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val p = palette()
    Row(modifier = Modifier.fillMaxSize().background(p.appBg)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            HeaderRegion(p = p, onIntent = onIntent)
            StatusStrip(p = p, text = buildTopStatus(state))
            TimelineRegion(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                p = p,
                state = state,
                onIntent = onIntent,
            )
            ComposerRegion(p = p, state = state, onIntent = onIntent, onOpenFile = onOpenFile)
        }
        if (state.rightDrawer != RightDrawerKind.NONE) {
            RightDrawer(p = p, state = state, onIntent = onIntent)
        }
    }
}

private fun buildTopStatus(state: ToolWindowUiState): String {
    return when {
        state.conversation.latestError != null -> state.conversation.latestError
        state.conversation.isRunning -> "正在执行当前步骤，请等待实时结果..."
        else -> "当前会话就绪，可继续输入下一步任务。"
    }
}

@Composable
private fun HeaderRegion(
    p: DesignPalette,
    onIntent: (ToolWindowIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(p.topBarBg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("CCG", color = p.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        HeaderAction(p, "+") { onIntent(ToolWindowIntent.NewSession) }
        Spacer(modifier = Modifier.width(6.dp))
        HeaderAction(p, "[]") { onIntent(ToolWindowIntent.NewTab) }
        Spacer(modifier = Modifier.width(6.dp))
        HeaderAction(p, "↺") { onIntent(ToolWindowIntent.ToggleHistory) }
        Spacer(modifier = Modifier.width(6.dp))
        HeaderAction(p, "⚙") { onIntent(ToolWindowIntent.ToggleSettings) }
    }
}

@Composable
private fun StatusStrip(
    p: DesignPalette,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(p.topStripBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = p.textSecondary,
            style = MaterialTheme.typography.caption,
        )
    }
}

@Composable
private fun HeaderAction(
    p: DesignPalette,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(label, color = p.textSecondary)
    }
}

@Composable
private fun TimelineRegion(
    modifier: Modifier,
    p: DesignPalette,
    state: ToolWindowUiState,
    onIntent: (ToolWindowIntent) -> Unit,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.conversation.turns) { turn ->
            TurnView(turn = turn, p = p, state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun TurnView(
    turn: TurnUiState,
    p: DesignPalette,
    state: ToolWindowUiState,
    onIntent: (ToolWindowIntent) -> Unit,
) {
    turn.items.forEach { item ->
        if (item.kind == ItemKind.NARRATIVE) {
            val text = item.text ?: return@forEach
            Text(
                text = text,
                color = p.timelinePlainText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
            return@forEach
        }

        val expanded = state.expandedNodeIds.contains(item.id)
        val indicatorColor = if (item.status == ItemStatus.FAILED) p.danger else p.success
        val title = "Exec Command"
        val body = item.text ?: item.command ?: item.filePath ?: item.name ?: item.id

        Column(
            modifier = Modifier.fillMaxWidth()
                .background(p.timelineCardBg, RoundedCornerShape(10.dp))
                .clickable { onIntent(ToolWindowIntent.ToggleNodeExpanded(item.id)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "⌄" else "›", color = p.textMuted)
                Spacer(Modifier.width(8.dp))
                Text(title, color = p.timelineCardText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(8.dp).background(indicatorColor, CircleShape),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(body, color = p.textSecondary)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ComposerRegion(
    p: DesignPalette,
    state: ToolWindowUiState,
    onIntent: (ToolWindowIntent) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(p.composerBg).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(p, if (state.stripExpanded) "⌄" else "›") { onIntent(ToolWindowIntent.ToggleStrip) }
            Spacer(Modifier.width(6.dp))
            if (state.stripExpanded) {
                Box(
                    modifier = Modifier.background(p.chipBg, RoundedCornerShape(7.dp))
                        .clickable { onIntent(ToolWindowIntent.ToggleEditedDrawer) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("编辑", color = p.textSecondary)
                }
            }
        }

        if (state.editedDrawerOpen) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().height(150.dp)
                    .background(p.inputBg, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text("当前会话修改文件", color = p.textPrimary)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.editedFiles) { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(p.topStripBg, RoundedCornerShape(6.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(file.path, color = p.textSecondary, modifier = Modifier.weight(1f))
                            HeaderAction(p, "打开") { onOpenFile(file.path) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth()
                .onPreviewKeyEvent {
                    if (
                        !state.conversation.isRunning &&
                        it.type == KeyEventType.KeyDown &&
                        it.key == Key.Enter &&
                        !it.isShiftPressed
                    ) {
                        onIntent(ToolWindowIntent.SendPrompt)
                        true
                    } else {
                        false
                    }
                },
            value = state.inputText,
            onValueChange = { onIntent(ToolWindowIntent.InputChanged(it)) },
            label = { Text("引用文件，#唤起智能体，插入提示词，Enter 发送") },
            maxLines = 4,
            singleLine = false,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val canSend = !state.conversation.isRunning && state.inputText.isNotBlank()
            Button(
                onClick = { onIntent(ToolWindowIntent.SendPrompt) },
                enabled = canSend,
            ) {
                Text(if (state.conversation.isRunning) "执行中..." else "发送")
            }
            Spacer(Modifier.width(10.dp))
            Text("⚡ 全自动", color = p.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text("gpt-5.3-codex", color = p.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text("中等", color = p.textSecondary)
        }
    }
}

@Composable
private fun RightDrawer(
    p: DesignPalette,
    state: ToolWindowUiState,
    onIntent: (ToolWindowIntent) -> Unit,
) {
    Column(
        modifier = Modifier.width(260.dp).fillMaxHeight()
            .background(p.topStripBg)
            .padding(10.dp),
    ) {
        when (state.rightDrawer) {
            RightDrawerKind.HISTORY -> {
                Text("历史信息", color = p.textPrimary, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.sessions) { session ->
                        val active = session.id == state.activeSessionId
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (active) p.chipBg else p.inputBg,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onIntent(ToolWindowIntent.SwitchSession(session.id)) }
                                .padding(8.dp),
                        ) {
                            Column {
                                Text(session.title, color = p.textPrimary)
                                Text("messages: ${session.messageCount}", color = p.textMuted, style = MaterialTheme.typography.caption)
                            }
                        }
                    }
                }
            }

            RightDrawerKind.SETTINGS -> {
                Text("设置", color = p.textPrimary, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(10.dp))
                Text("执行策略: 保守默认", color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text("引擎: Codex", color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text("模式: 证据流时间线", color = p.textSecondary)
            }

            RightDrawerKind.NONE -> Unit
        }
    }
}
