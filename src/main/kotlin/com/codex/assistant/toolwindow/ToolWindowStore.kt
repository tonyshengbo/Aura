package com.codex.assistant.toolwindow

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.ContextFile
import com.codex.assistant.model.MessageRole
import com.codex.assistant.protocol.ConversationStateMachine
import com.codex.assistant.protocol.ConversationUiState
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.protocol.UnifiedItem
import com.codex.assistant.service.AgentChatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RightDrawerKind {
    NONE,
    HISTORY,
    SETTINGS,
}

internal data class EditedFileRecord(
    val path: String,
    val lastTurnId: String?,
)

internal data class ToolWindowUiState(
    val conversation: ConversationUiState = ConversationUiState(),
    val inputText: String = "",
    val stripExpanded: Boolean = true,
    val editedDrawerOpen: Boolean = false,
    val editedFiles: List<EditedFileRecord> = emptyList(),
    val expandedNodeIds: Set<String> = emptySet(),
    val rightDrawer: RightDrawerKind = RightDrawerKind.NONE,
    val sessions: List<AgentChatService.SessionSummary> = emptyList(),
    val activeSessionId: String = "",
)

internal sealed class ToolWindowIntent {
    data class InputChanged(val value: String) : ToolWindowIntent()
    data object SendPrompt : ToolWindowIntent()
    data object NewSession : ToolWindowIntent()
    data object NewTab : ToolWindowIntent()
    data object ToggleHistory : ToolWindowIntent()
    data object ToggleSettings : ToolWindowIntent()
    data class SwitchSession(val sessionId: String) : ToolWindowIntent()
    data object ToggleStrip : ToolWindowIntent()
    data object ToggleEditedDrawer : ToolWindowIntent()
    data class ToggleNodeExpanded(val nodeId: String) : ToolWindowIntent()
}

internal sealed class ToolWindowEffect {
    data class RunPrompt(val prompt: String) : ToolWindowEffect()
}

internal class ToolWindowStore(
    private val chatService: AgentChatService,
) {
    private val stateMachine = ConversationStateMachine()
    private val editedFileMap = linkedMapOf<String, EditedFileRecord>()
    private var activeTurnId: String? = null

    private val _state = MutableStateFlow(
        ToolWindowUiState(
            sessions = chatService.listSessions(),
            activeSessionId = chatService.getCurrentSessionId(),
        ),
    )
    val state: StateFlow<ToolWindowUiState> = _state.asStateFlow()

    fun dispatch(intent: ToolWindowIntent): ToolWindowEffect? {
        return when (intent) {
            is ToolWindowIntent.InputChanged -> {
                update { it.copy(inputText = intent.value) }
                null
            }

            ToolWindowIntent.SendPrompt -> {
                if (state.value.conversation.isRunning) return null
                val prompt = state.value.inputText.trim()
                if (prompt.isBlank()) return null
                chatService.addMessage(ChatMessage(role = MessageRole.USER, content = prompt))
                update { it.copy(inputText = "") }
                ToolWindowEffect.RunPrompt(prompt)
            }

            ToolWindowIntent.NewSession -> {
                resetConversation()
                chatService.createSession()
                syncSessions()
                null
            }

            ToolWindowIntent.NewTab -> {
                // Current V2 behavior maps "new tab" to a fresh session.
                resetConversation()
                chatService.createSession()
                syncSessions()
                null
            }

            ToolWindowIntent.ToggleHistory -> {
                val next = if (state.value.rightDrawer == RightDrawerKind.HISTORY) RightDrawerKind.NONE else RightDrawerKind.HISTORY
                update { it.copy(rightDrawer = next, sessions = chatService.listSessions()) }
                null
            }

            ToolWindowIntent.ToggleSettings -> {
                val next = if (state.value.rightDrawer == RightDrawerKind.SETTINGS) RightDrawerKind.NONE else RightDrawerKind.SETTINGS
                update { it.copy(rightDrawer = next) }
                null
            }

            is ToolWindowIntent.SwitchSession -> {
                if (chatService.switchSession(intent.sessionId)) {
                    resetConversation()
                    syncSessions()
                }
                null
            }

            ToolWindowIntent.ToggleStrip -> {
                update { it.copy(stripExpanded = !it.stripExpanded) }
                null
            }

            ToolWindowIntent.ToggleEditedDrawer -> {
                update { it.copy(editedDrawerOpen = !it.editedDrawerOpen) }
                null
            }

            is ToolWindowIntent.ToggleNodeExpanded -> {
                val current = state.value.expandedNodeIds
                val next = if (current.contains(intent.nodeId)) current - intent.nodeId else current + intent.nodeId
                update { it.copy(expandedNodeIds = next) }
                null
            }
        }
    }

    fun syncSessionsFromService() {
        syncSessions()
    }

    fun onSessionActivated() {
        resetConversation()
        syncSessions()
    }

    fun onUnifiedEvent(event: UnifiedEvent) {
        when (event) {
            is UnifiedEvent.TurnStarted -> activeTurnId = event.turnId
            is UnifiedEvent.TurnCompleted -> if (event.turnId.isBlank() || event.turnId == activeTurnId) activeTurnId = null
            is UnifiedEvent.ItemUpdated -> trackEditedFiles(event.item)
            else -> Unit
        }

        stateMachine.accept(event)
        update {
            it.copy(
                conversation = stateMachine.state,
                editedFiles = editedFileMap.values.toList(),
            )
        }
    }

    private fun trackEditedFiles(item: UnifiedItem) {
        if (item.kind != ItemKind.DIFF_APPLY) return
        val path = item.filePath?.trim().orEmpty()
        if (path.isBlank()) return
        editedFileMap[path] = EditedFileRecord(
            path = path,
            lastTurnId = activeTurnId,
        )
    }

    private fun syncSessions() {
        update {
            it.copy(
                sessions = chatService.listSessions(),
                activeSessionId = chatService.getCurrentSessionId(),
                rightDrawer = RightDrawerKind.NONE,
            )
        }
    }

    private fun resetConversation() {
        activeTurnId = null
        editedFileMap.clear()
        update {
            it.copy(
                conversation = ConversationUiState(),
                editedFiles = emptyList(),
                expandedNodeIds = emptySet(),
                editedDrawerOpen = false,
            )
        }
    }

    private fun update(transform: (ToolWindowUiState) -> ToolWindowUiState) {
        _state.value = transform(_state.value)
    }
}
