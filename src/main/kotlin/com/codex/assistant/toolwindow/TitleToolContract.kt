package com.codex.assistant.toolwindow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TitleToolState(
    val title: String = "当前对话",
    val subtitle: String = "Codex Chat",
    val usageLabel: String = "--",
    val usageTooltip: String? = null,
)

internal sealed interface TitleToolIntent {
    data object StartNewSessionClicked : TitleToolIntent
    data object StartNewWindowClicked : TitleToolIntent
}

internal class TitleToolViewModel(
    private val port: TitleToolPort,
) {
    private val _state = MutableStateFlow(TitleToolState())
    val state: StateFlow<TitleToolState> = _state.asStateFlow()

    fun dispatch(intent: TitleToolIntent) {
        when (intent) {
            TitleToolIntent.StartNewSessionClicked -> port.startNewSession()
            TitleToolIntent.StartNewWindowClicked -> port.startNewWindow()
        }
    }

    fun updateState(transform: (TitleToolState) -> TitleToolState) {
        _state.value = transform(_state.value)
    }
}

internal class TitleToolAction(
    private val view: TitleToolView,
    private val viewModel: TitleToolViewModel,
) {
    fun bind() {
        view.newChatButton.addActionListener {
            viewModel.dispatch(TitleToolIntent.StartNewSessionClicked)
        }
        view.newWindowButton.addActionListener {
            viewModel.dispatch(TitleToolIntent.StartNewWindowClicked)
        }
    }
}
