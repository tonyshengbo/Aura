package com.codex.assistant.toolwindow.header

import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.toolwindow.eventing.AppEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HeaderAreaState(
    val title: String = "",
    val canCreateNewSession: Boolean = false,
)

internal class HeaderAreaStore {
    private val _state = MutableStateFlow(HeaderAreaState())
    val state: StateFlow<HeaderAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.SessionSnapshotUpdated -> {
                if (event.activeSessionId.isBlank()) return
                val active = event.sessions.firstOrNull { it.id == event.activeSessionId }
                val title = active?.title?.trim().orEmpty()
                val canCreate = (active?.messageCount ?: 0) > 0
                _state.value = _state.value.copy(
                    title = title,
                    canCreateNewSession = canCreate,
                )
            }

            else -> Unit
        }
    }
}
