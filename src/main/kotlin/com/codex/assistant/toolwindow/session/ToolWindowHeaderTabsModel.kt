package com.codex.assistant.toolwindow.session

import com.codex.assistant.service.AgentChatService

internal data class ToolWindowHeaderTab(
    val sessionId: String,
    val title: String,
    val active: Boolean,
    val closable: Boolean,
)

internal object ToolWindowHeaderTabsModel {
    fun buildTabs(
        openSessionIds: List<String>,
        activeSessionId: String,
        sessions: List<AgentChatService.SessionSummary>,
    ): List<ToolWindowHeaderTab> {
        val sessionsById = sessions.associateBy { it.id }
        val closable = openSessionIds.size > 1
        return openSessionIds.mapIndexedNotNull { index, sessionId ->
            val summary = sessionsById[sessionId] ?: return@mapIndexedNotNull null
            ToolWindowHeaderTab(
                sessionId = sessionId,
                title = summary.title.trim().ifBlank { "T${index + 1}" },
                active = sessionId == activeSessionId,
                closable = closable,
            )
        }
    }
}
