package com.codex.assistant.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentSettingsStateTest {
    @Test
    fun `state defaults theme mode to follow ide`() {
        val state = AgentSettingsService.State()

        assertEquals(UiThemeMode.FOLLOW_IDE.name, state.uiTheme)
    }

    @Test
    fun `state reads codex path from engine map first`() {
        val state = AgentSettingsService.State(
            codexCliPath = "legacy-codex",
            engineExecutablePaths = mutableMapOf("codex" to "/usr/local/bin/codex"),
        )

        assertEquals("/usr/local/bin/codex", state.executablePathFor("codex"))
    }

    @Test
    fun `state falls back to codexCliPath when map key is absent`() {
        val state = AgentSettingsService.State(
            codexCliPath = "codex-from-legacy",
            engineExecutablePaths = mutableMapOf(),
        )

        assertEquals("codex-from-legacy", state.executablePathFor("codex"))
    }

    @Test
    fun `state stores local agent definitions`() {
        val state = AgentSettingsService.State(
            savedAgents = mutableListOf(
                SavedAgentDefinition(
                    id = "agent-1",
                    name = "Reviewer",
                    prompt = "Review the current implementation.",
                ),
            ),
        )

        assertEquals(1, state.savedAgents.size)
        assertEquals("Reviewer", state.savedAgents.single().name)
        assertEquals("Review the current implementation.", state.savedAgents.single().prompt)
    }
}
