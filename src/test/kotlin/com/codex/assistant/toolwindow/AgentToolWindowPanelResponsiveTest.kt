package com.codex.assistant.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentToolWindowPanelResponsiveTest {
    @Test
    fun `density is regular for wide widths`() {
        assertEquals(
            AgentToolWindowPanel.ControlDensity.REGULAR,
            AgentToolWindowPanel.densityForWidth(AgentToolWindowPanel.COMPOSER_REGULAR_MIN_WIDTH),
        )
    }

    @Test
    fun `density is compact for mid widths`() {
        assertEquals(
            AgentToolWindowPanel.ControlDensity.COMPACT,
            AgentToolWindowPanel.densityForWidth(AgentToolWindowPanel.COMPOSER_COMPACT_MIN_WIDTH),
        )
        assertEquals(
            AgentToolWindowPanel.ControlDensity.COMPACT,
            AgentToolWindowPanel.densityForWidth(AgentToolWindowPanel.COMPOSER_REGULAR_MIN_WIDTH - 1),
        )
    }

    @Test
    fun `density is icon only for narrow widths`() {
        assertEquals(
            AgentToolWindowPanel.ControlDensity.ICON_ONLY,
            AgentToolWindowPanel.densityForWidth(AgentToolWindowPanel.COMPOSER_COMPACT_MIN_WIDTH - 1),
        )
    }
}

