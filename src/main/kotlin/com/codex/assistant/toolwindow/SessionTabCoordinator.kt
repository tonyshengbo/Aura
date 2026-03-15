package com.codex.assistant.toolwindow

import com.codex.assistant.service.AgentChatService
import com.intellij.openapi.wm.ex.ToolWindowEx

internal class SessionTabCoordinator(
    private val chatService: AgentChatService,
    private val toolWindowProvider: () -> ToolWindowEx?,
    private val isRunning: () -> Boolean,
    private val onStatus: (String) -> Unit,
    private val onSessionActivated: () -> Unit,
) {
    private val openSessionTabs = linkedSetOf<String>()
    private var activeSessionTabId: String = ""

    fun initialize() {
        activeSessionTabId = chatService.getCurrentSessionId()
        if (activeSessionTabId.isNotBlank()) {
            openSessionTabs += activeSessionTabId
        }
        refresh()
    }

    fun refresh() {
        val sessionsById = chatService.listSessions().associateBy { it.id }
        openSessionTabs.removeIf { !sessionsById.containsKey(it) }
        if (openSessionTabs.isEmpty()) {
            val currentId = chatService.getCurrentSessionId()
            if (currentId.isNotBlank()) {
                openSessionTabs += currentId
            }
        }
        activeSessionTabId = chatService.getCurrentSessionId()
        syncToolWindowHeaderTabs(sessionsById.values.toList())
    }

    fun startNewSession() {
        if (isRunning()) {
            onStatus("请先停止当前任务再新建会话")
            return
        }
        val id = chatService.createSession()
        replaceActiveSessionTab(id)
    }

    fun startNewWindowTab() {
        if (isRunning()) {
            onStatus("请先停止当前任务再新建窗口")
            return
        }
        val id = chatService.createSession()
        openSessionTab(id)
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == activeSessionTabId) return
        if (isRunning()) {
            onStatus("任务执行中，暂不支持切换标签")
            return
        }
        if (chatService.switchSession(sessionId)) {
            activeSessionTabId = sessionId
            onSessionActivated()
        }
    }

    fun closeSessionTab(sessionId: String) {
        if (openSessionTabs.size <= 1) {
            onStatus("至少保留一个标签")
            return
        }
        if (sessionId == activeSessionTabId && isRunning()) {
            onStatus("请先停止当前任务再关闭标签")
            return
        }
        val ordered = openSessionTabs.toList()
        val index = ordered.indexOf(sessionId)
        if (index < 0) return
        openSessionTabs.remove(sessionId)
        if (sessionId == activeSessionTabId) {
            val nextSessionId = openSessionTabs.elementAtOrNull(index.coerceAtMost(openSessionTabs.size - 1))
                ?: openSessionTabs.lastOrNull()
            if (nextSessionId != null && chatService.switchSession(nextSessionId)) {
                activeSessionTabId = nextSessionId
                onSessionActivated()
                return
            }
        }
        refresh()
    }

    private fun openSessionTab(sessionId: String) {
        if (openSessionTabs.size >= MAX_OPEN_TABS && !openSessionTabs.contains(sessionId)) {
            onStatus("最多可打开 $MAX_OPEN_TABS 个标签")
            return
        }
        openSessionTabs += sessionId
        switchToSession(sessionId)
    }

    private fun replaceActiveSessionTab(sessionId: String) {
        val currentActive = activeSessionTabId
        val ordered = openSessionTabs.toList().toMutableList()
        val index = ordered.indexOf(currentActive)
        if (index >= 0) {
            ordered[index] = sessionId
        } else if (ordered.isEmpty()) {
            ordered += sessionId
        } else {
            ordered[ordered.lastIndex] = sessionId
        }
        openSessionTabs.clear()
        openSessionTabs.addAll(ordered)
        activeSessionTabId = sessionId
        onSessionActivated()
    }

    private fun syncToolWindowHeaderTabs(sessions: List<AgentChatService.SessionSummary>) {
        val toolWindowEx = toolWindowProvider() ?: return
        val tabs = ToolWindowHeaderTabsModel.buildTabs(
            openSessionIds = openSessionTabs.toList(),
            activeSessionId = activeSessionTabId,
            sessions = sessions,
        )
        val actions = tabs.map { tab ->
            ToolWindowHeaderTabAction(
                tab = tab,
                onSelect = { sessionId -> switchToSession(sessionId) },
                onClose = { sessionId -> closeSessionTab(sessionId) },
            )
        }
        toolWindowEx.setTabActions(*actions.toTypedArray())
    }

    companion object {
        private const val MAX_OPEN_TABS = 10
    }
}
