package com.codex.assistant.toolwindow.status

import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.protocol.TurnOutcome
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.shared.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StatusAreaState(
    val text: UiText = UiText.Bundle("status.ready"),
)

internal class StatusAreaStore {
    private val _state = MutableStateFlow(StatusAreaState())
    val state: StateFlow<StatusAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.PromptAccepted -> {
                _state.value = StatusAreaState(UiText.Bundle("status.running"))
            }

            AppEvent.ConversationReset -> {
                _state.value = StatusAreaState()
            }

            is AppEvent.UnifiedEventPublished -> {
                _state.value = StatusAreaState(text = mapUnifiedText(event.event, _state.value.text))
            }

            is AppEvent.StatusTextUpdated -> {
                _state.value = StatusAreaState(text = event.text)
            }

            else -> Unit
        }
    }

    private fun mapUnifiedText(event: UnifiedEvent, current: UiText): UiText {
        return when (event) {
            is UnifiedEvent.Error -> UiText.Raw(event.message)
            is UnifiedEvent.TurnStarted -> UiText.Bundle("status.running")
            is UnifiedEvent.TurnCompleted -> when (event.outcome) {
                TurnOutcome.SUCCESS,
                TurnOutcome.CANCELLED,
                -> UiText.Bundle("status.ready")

                TurnOutcome.FAILED -> UiText.Bundle("status.failed")
                TurnOutcome.RUNNING -> current
            }

            else -> current
        }
    }
}
