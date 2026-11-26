package com.auracode.assistant.toolwindow.shell

import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.settings.SavedAgentDefinition
import com.auracode.assistant.settings.UiLanguageMode
import com.auracode.assistant.settings.UiThemeMode
import com.auracode.assistant.toolwindow.settings.RuntimeSettingsTab
import com.auracode.assistant.toolwindow.settings.SettingsSection
import com.auracode.assistant.toolwindow.settings.SkillsSettingsTab
import com.auracode.assistant.toolwindow.settings.TokenUsageRange
import com.auracode.assistant.toolwindow.settings.TokenUsageSettingsTab
import com.auracode.assistant.toolwindow.settings.TokenUsageStatsSnapshot
import com.auracode.assistant.toolwindow.settings.tokenUsageScopeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidePanelAreaStoreTest {
    @Test
    fun `settings refresh preserves dirty agent fields and save acknowledgement advances clean baseline`() {
        val store = SidePanelAreaStore()
        val saved = SavedAgentDefinition("agent-a", "原名称", "原提示词")
        store.onEvent(settingsSnapshot(saved))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.SelectSettingsSection(SettingsSection.AGENTS)))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.SelectSavedAgentForEdit(saved.id)))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.EditAgentDraftName("新名称")))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.EditAgentDraftPrompt("新提示词")))

        store.onEvent(settingsSnapshot(saved))
        assertEquals("新名称", store.state.value.agentDraftName)
        assertEquals("新提示词", store.state.value.agentDraftPrompt)
        assertEquals(saved.id, store.state.value.editingAgentId)

        val acknowledged = saved.copy(name = "新名称", prompt = "新提示词")
        store.onEvent(settingsSnapshot(acknowledged))
        assertEquals(acknowledged, store.state.value.savedAgents.single())
        store.onEvent(settingsSnapshot(acknowledged.copy(name = "外部名称", prompt = "外部提示词")))
        assertEquals("外部名称", store.state.value.agentDraftName)
        assertEquals("外部提示词", store.state.value.agentDraftPrompt)
    }

    @Test
    fun `agent refresh accepts clean field while retaining dirty sibling`() {
        val store = SidePanelAreaStore()
        val saved = SavedAgentDefinition("agent-a", "原名称", "原提示词")
        store.onEvent(settingsSnapshot(saved))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.SelectSavedAgentForEdit(saved.id)))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.EditAgentDraftName("未保存名称")))

        store.onEvent(settingsSnapshot(saved.copy(prompt = "外部提示词")))

        assertEquals("未保存名称", store.state.value.agentDraftName)
        assertEquals("外部提示词", store.state.value.agentDraftPrompt)
    }

    @Test
    fun `new agent draft keeps null identity and text across settings refresh`() {
        val store = SidePanelAreaStore()
        val saved = SavedAgentDefinition("agent-a", "已存在", "已有提示词")
        store.onEvent(settingsSnapshot(saved))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.SelectSettingsSection(SettingsSection.AGENTS)))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.CreateNewAgentDraft))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.EditAgentDraftName("新智能体")))
        store.onEvent(AppEvent.UiIntentPublished(UiIntent.EditAgentDraftPrompt("新草稿")))

        store.onEvent(settingsSnapshot(saved, SavedAgentDefinition("agent-b", "另一个", "另一提示词")))

        assertNull(store.state.value.editingAgentId)
        assertEquals(AgentSettingsPage.EDITOR, store.state.value.agentSettingsPage)
        assertEquals("新智能体", store.state.value.agentDraftName)
        assertEquals("新草稿", store.state.value.agentDraftPrompt)
    }

    private fun settingsSnapshot(vararg agents: SavedAgentDefinition) = AppEvent.SettingsSnapshotUpdated(
        codexCliPath = "codex",
        languageMode = UiLanguageMode.FOLLOW_IDE,
        themeMode = UiThemeMode.FOLLOW_IDE,
        autoContextEnabled = true,
        savedAgents = agents.toList(),
    )

    @Test
    fun `select skills settings tab updates only skills tab state`() {
        val store = SidePanelAreaStore()

        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectRuntimeSettingsTab(RuntimeSettingsTab.CLAUDE),
            ),
        )
        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectSkillsSettingsTab(SkillsSettingsTab.CLAUDE),
            ),
        )

        assertEquals(RuntimeSettingsTab.CLAUDE, store.state.value.runtimeSettingsTab)
        assertEquals(SkillsSettingsTab.CLAUDE, store.state.value.skillsSettingsTab)
    }

    @Test
    fun `token usage intents and events update page state coherently`() {
        val store = SidePanelAreaStore()

        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectSettingsSection(SettingsSection.TOKEN_USAGE),
            ),
        )
        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectTokenUsageSettingsTab(TokenUsageSettingsTab.CLAUDE),
            ),
        )
        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectTokenUsageRange(TokenUsageRange.ALL),
            ),
        )
        store.onEvent(
            AppEvent.TokenUsageStatsLoadingChanged(
                loading = true,
                requestScopeKey = tokenUsageScopeKey(
                    engineId = TokenUsageSettingsTab.CLAUDE.engineId,
                    range = TokenUsageRange.ALL,
                ),
            ),
        )
        store.onEvent(
            AppEvent.TokenUsageStatsUpdated(
                snapshot = TokenUsageStatsSnapshot(
                    engineId = TokenUsageSettingsTab.CLAUDE.engineId,
                    range = TokenUsageRange.ALL,
                    hasHistoricalData = true,
                ),
                requestScopeKey = tokenUsageScopeKey(
                    engineId = TokenUsageSettingsTab.CLAUDE.engineId,
                    range = TokenUsageRange.ALL,
                ),
            ),
        )

        assertEquals(SettingsSection.TOKEN_USAGE, store.state.value.settingsSection)
        assertEquals(TokenUsageSettingsTab.CLAUDE, store.state.value.tokenUsageSettingsTab)
        assertEquals(TokenUsageRange.ALL, store.state.value.tokenUsageRange)
        assertEquals(false, store.state.value.tokenUsageLoading)
        assertNull(store.state.value.tokenUsageLastError)
        assertEquals(
            TokenUsageRange.ALL,
            store.state.value.tokenUsageStatsByScope.getValue(
                tokenUsageScopeKey(
                    engineId = TokenUsageSettingsTab.CLAUDE.engineId,
                    range = TokenUsageRange.ALL,
                ),
            ).range,
        )
    }

    @Test
    fun `stale token usage responses are cached without clearing the active request state`() {
        val store = SidePanelAreaStore()
        val codexLast7Key = tokenUsageScopeKey(
            engineId = TokenUsageSettingsTab.CODEX.engineId,
            range = TokenUsageRange.LAST_7_DAYS,
        )
        val codexAllKey = tokenUsageScopeKey(
            engineId = TokenUsageSettingsTab.CODEX.engineId,
            range = TokenUsageRange.ALL,
        )

        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectSettingsSection(SettingsSection.TOKEN_USAGE),
            ),
        )
        store.onEvent(
            AppEvent.TokenUsageStatsLoadingChanged(
                loading = true,
                requestScopeKey = codexLast7Key,
            ),
        )
        store.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.SelectTokenUsageRange(TokenUsageRange.ALL),
            ),
        )
        store.onEvent(
            AppEvent.TokenUsageStatsLoadingChanged(
                loading = true,
                requestScopeKey = codexAllKey,
            ),
        )
        store.onEvent(
            AppEvent.TokenUsageStatsUpdated(
                snapshot = TokenUsageStatsSnapshot(
                    engineId = TokenUsageSettingsTab.CODEX.engineId,
                    range = TokenUsageRange.LAST_7_DAYS,
                    hasHistoricalData = true,
                ),
                requestScopeKey = codexLast7Key,
            ),
        )

        assertEquals(true, store.state.value.tokenUsageLoading)
        assertEquals(codexAllKey, store.state.value.tokenUsageActiveRequestKey)
        assertNull(store.state.value.tokenUsageLastError)
        assertEquals(
            TokenUsageRange.LAST_7_DAYS,
            store.state.value.tokenUsageStatsByScope.getValue(codexLast7Key).range,
        )

        store.onEvent(
            AppEvent.TokenUsageStatsUpdated(
                snapshot = TokenUsageStatsSnapshot(
                    engineId = TokenUsageSettingsTab.CODEX.engineId,
                    range = TokenUsageRange.ALL,
                    hasHistoricalData = true,
                ),
                requestScopeKey = codexAllKey,
            ),
        )

        assertEquals(false, store.state.value.tokenUsageLoading)
        assertEquals(null, store.state.value.tokenUsageActiveRequestKey)
        assertEquals(
            TokenUsageRange.ALL,
            store.state.value.tokenUsageStatsByScope.getValue(codexAllKey).range,
        )
    }
}
