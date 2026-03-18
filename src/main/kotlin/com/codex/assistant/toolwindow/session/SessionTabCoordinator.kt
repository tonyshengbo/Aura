package com.codex.assistant.toolwindow.session

import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.toolwindow.shared.UiText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ex.ToolWindowEx

internal class SessionTabCoordinator(
    private val chatService: AgentChatService,
    private val toolWindowProvider: () -> ToolWindowEx?,
    private val isRunning: () -> Boolean,
    private val onStatus: (UiText) -> Unit,
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
        val app = ApplicationManager.getApplication()
        if (!app.isDispatchThread) {
            app.invokeLater { refresh() }
            return
        }
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
            onStatus(UiText.bundle("session.warn.stopBeforeNewSession"))
            return
        }
        val id = chatService.createSession()
        replaceActiveSessionTab(id)
    }

    fun startNewWindowTab() {
        if (isRunning()) {
            onStatus(UiText.bundle("session.warn.stopBeforeNewWindow"))
            return
        }
        val id = chatService.createSession()
        openSessionTab(id)
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == activeSessionTabId) return
        if (isRunning()) {
            onStatus(UiText.bundle("session.warn.runningNoSwitch"))
            return
        }
        if (chatService.switchSession(sessionId)) {
            activeSessionTabId = sessionId
            onSessionActivated()
        }
    }

    fun closeSessionTab(sessionId: String) {
        if (openSessionTabs.size <= 1) {
            onStatus(UiText.bundle("session.warn.keepOneTab"))
            return
        }
        if (sessionId == activeSessionTabId && isRunning()) {
            onStatus(UiText.bundle("session.warn.stopBeforeClose"))
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
            onStatus(UiText.bundle("session.warn.maxTabs", MAX_OPEN_TABS))
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
        val app = ApplicationManager.getApplication()
        if (!app.isDispatchThread) {
            app.invokeLater { syncToolWindowHeaderTabs(sessions) }
            return
        }
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
