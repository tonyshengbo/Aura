package com.codex.assistant.toolwindow.drawer

import com.codex.assistant.conversation.ConversationSummary
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.settings.SavedAgentDefinition
import com.codex.assistant.settings.UiLanguageMode
import com.codex.assistant.settings.UiThemeMode
import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RightDrawerKind {
    NONE,
    HISTORY,
    SETTINGS,
}

internal enum class AgentSettingsPage {
    LIST,
    EDITOR,
}

internal data class RightDrawerAreaState(
    val kind: RightDrawerKind = RightDrawerKind.NONE,
    val sessions: List<AgentChatService.SessionSummary> = emptyList(),
    val activeSessionId: String = "",
    val activeRemoteConversationId: String = "",
    val historyConversations: List<ConversationSummary> = emptyList(),
    val historyNextCursor: String? = null,
    val historyLoading: Boolean = false,
    val historyQuery: String = "",
    val codexCliPath: String = "",
    val languageMode: UiLanguageMode = UiLanguageMode.FOLLOW_IDE,
    val themeMode: UiThemeMode = UiThemeMode.FOLLOW_IDE,
    val settingsSection: SettingsSection = SettingsSection.GENERAL,
    val savedAgents: List<SavedAgentDefinition> = emptyList(),
    val agentSettingsPage: AgentSettingsPage = AgentSettingsPage.LIST,
    val editingAgentId: String? = null,
    val agentDraftName: String = "",
    val agentDraftPrompt: String = "",
)

internal class RightDrawerAreaStore {
    private val _state = MutableStateFlow(RightDrawerAreaState())
    val state: StateFlow<RightDrawerAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.UiIntentPublished -> {
                when (event.intent) {
                    UiIntent.ToggleHistory -> {
                        val next = if (_state.value.kind == RightDrawerKind.HISTORY) RightDrawerKind.NONE else RightDrawerKind.HISTORY
                        _state.value = _state.value.copy(kind = next)
                    }

                    is UiIntent.EditHistorySearchQuery -> {
                        _state.value = _state.value.copy(historyQuery = event.intent.value)
                    }

                    UiIntent.ToggleSettings -> {
                        val next = if (_state.value.kind == RightDrawerKind.SETTINGS) RightDrawerKind.NONE else RightDrawerKind.SETTINGS
                        _state.value = _state.value.copy(kind = next)
                    }

                    is UiIntent.SelectSettingsSection -> {
                        _state.value = _state.value.copy(
                            kind = RightDrawerKind.SETTINGS,
                            settingsSection = event.intent.section,
                            agentSettingsPage = if (event.intent.section == SettingsSection.AGENTS) {
                                AgentSettingsPage.LIST
                            } else {
                                _state.value.agentSettingsPage
                            },
                        )
                    }

                    UiIntent.CloseRightDrawer -> {
                        _state.value = _state.value.copy(kind = RightDrawerKind.NONE)
                    }

                    is UiIntent.EditSettingsCodexCliPath -> {
                        _state.value = _state.value.copy(codexCliPath = event.intent.value)
                    }

                    is UiIntent.EditSettingsLanguageMode -> {
                        _state.value = _state.value.copy(languageMode = event.intent.mode)
                    }

                    is UiIntent.EditSettingsThemeMode -> {
                        _state.value = _state.value.copy(themeMode = event.intent.mode)
                    }

                    UiIntent.CreateNewAgentDraft -> {
                        _state.value = _state.value.copy(
                            agentSettingsPage = AgentSettingsPage.EDITOR,
                            editingAgentId = null,
                            agentDraftName = "",
                            agentDraftPrompt = "",
                        )
                    }

                    UiIntent.ShowAgentSettingsList -> {
                        _state.value = _state.value.copy(agentSettingsPage = AgentSettingsPage.LIST)
                    }

                    is UiIntent.SelectSavedAgentForEdit -> {
                        val agent = _state.value.savedAgents.firstOrNull { it.id == event.intent.id } ?: return
                        _state.value = _state.value.copy(
                            agentSettingsPage = AgentSettingsPage.EDITOR,
                            editingAgentId = agent.id,
                            agentDraftName = agent.name,
                            agentDraftPrompt = agent.prompt,
                        )
                    }

                    is UiIntent.EditAgentDraftName -> {
                        _state.value = _state.value.copy(agentDraftName = event.intent.value)
                    }

                    is UiIntent.EditAgentDraftPrompt -> {
                        _state.value = _state.value.copy(agentDraftPrompt = event.intent.value)
                    }

                    else -> Unit
                }
            }

            is AppEvent.SessionSnapshotUpdated -> {
                val active = event.sessions.firstOrNull { it.id == event.activeSessionId }
                _state.value = _state.value.copy(
                    sessions = event.sessions,
                    activeSessionId = event.activeSessionId,
                    activeRemoteConversationId = active?.remoteConversationId.orEmpty(),
                )
            }

            is AppEvent.HistoryConversationsUpdated -> {
                _state.value = _state.value.copy(
                    historyConversations = if (event.append) {
                        (_state.value.historyConversations + event.conversations)
                            .distinctBy { it.remoteConversationId }
                    } else {
                        event.conversations
                    },
                    historyNextCursor = event.nextCursor,
                    historyLoading = event.isLoading,
                )
            }

            is AppEvent.SettingsSnapshotUpdated -> {
                val selected = event.savedAgents.firstOrNull { it.id == _state.value.editingAgentId }
                val fallback = event.savedAgents.firstOrNull()
                _state.value = _state.value.copy(
                    codexCliPath = event.codexCliPath,
                    languageMode = event.languageMode,
                    themeMode = event.themeMode,
                    savedAgents = event.savedAgents,
                    editingAgentId = selected?.id ?: _state.value.editingAgentId?.takeIf { id ->
                        event.savedAgents.any { it.id == id }
                    } ?: fallback?.id,
                    agentSettingsPage = when {
                        _state.value.settingsSection != SettingsSection.AGENTS -> _state.value.agentSettingsPage
                        selected != null -> AgentSettingsPage.EDITOR
                        _state.value.editingAgentId != null && event.savedAgents.none { it.id == _state.value.editingAgentId } -> AgentSettingsPage.LIST
                        else -> _state.value.agentSettingsPage
                    },
                    agentDraftName = selected?.name ?: _state.value.agentDraftName.takeIf { _state.value.agentSettingsPage == AgentSettingsPage.EDITOR } ?: "",
                    agentDraftPrompt = selected?.prompt ?: _state.value.agentDraftPrompt.takeIf { _state.value.agentSettingsPage == AgentSettingsPage.EDITOR } ?: "",
                )
            }

            else -> Unit
        }
    }
}
