package com.codex.assistant.toolwindow

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AgentToolWindowAction(
    private val titleToolAction: TitleToolAction,
    private val inputBoxAction: InputBoxAction,
    private val titleToolViewModel: TitleToolViewModel,
    private val messageBoxViewModel: MessageBoxViewModel,
    private val inputBoxViewModel: InputBoxViewModel,
    private val titleToolView: TitleToolView,
    private val messageBoxView: MessageBoxView,
    private val inputBoxView: InputBoxView,
) {
    fun bind(scope: CoroutineScope) {
        titleToolAction.bind()
        inputBoxAction.bind()
        bindState(scope)
    }

    private fun bindState(scope: CoroutineScope) {
        scope.launch {
            titleToolViewModel.state.collectLatest { state ->
                ApplicationManager.getApplication().invokeLater {
                    titleToolView.render(
                        TitleToolView.State(
                            title = state.title,
                            subtitle = state.subtitle,
                            usageLabel = state.usageLabel,
                            usageTooltip = state.usageTooltip,
                        ),
                    )
                }
            }
        }
        scope.launch {
            messageBoxViewModel.state.collectLatest { state ->
                ApplicationManager.getApplication().invokeLater {
                    messageBoxView.showMessages(state.forceMessages)
                }
            }
        }
        scope.launch {
            inputBoxViewModel.state.collectLatest { state ->
                ApplicationManager.getApplication().invokeLater {
                    inputBoxView.setRunningState(state.running)
                    if (state.statusMessage.isBlank()) {
                        inputBoxView.hideStatus()
                    } else {
                        inputBoxView.updateStatus(state.statusMessage, state.statusLoading)
                    }
                }
            }
        }
    }
}
