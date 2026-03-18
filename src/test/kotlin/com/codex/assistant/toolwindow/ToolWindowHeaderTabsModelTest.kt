package com.codex.assistant.toolwindow

import com.codex.assistant.service.AgentChatService
import com.codex.assistant.toolwindow.session.ToolWindowHeaderTabsModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolWindowHeaderTabsModelTest {
    @Test
    fun `builds header tabs in open order with active marker`() {
        val tabs = ToolWindowHeaderTabsModel.buildTabs(
            openSessionIds = listOf("s2", "s1"),
            activeSessionId = "s1",
            sessions = listOf(
                AgentChatService.SessionSummary("s1", "First Session", 2L, 3),
                AgentChatService.SessionSummary("s2", "Second Session", 1L, 1),
            ),
        )

        assertEquals(listOf("Second Session", "First Session"), tabs.map { it.title })
        assertEquals(listOf(false, true), tabs.map { it.active })
        assertTrue(tabs.all { it.closable })
    }

    @Test
    fun `falls back to numbered titles when session title is blank`() {
        val tabs = ToolWindowHeaderTabsModel.buildTabs(
            openSessionIds = listOf("s1"),
            activeSessionId = "s1",
            sessions = listOf(
                AgentChatService.SessionSummary("s1", "", 1L, 0),
            ),
        )

        assertEquals("T1", tabs.single().title)
        assertFalse(tabs.single().closable)
    }
}
