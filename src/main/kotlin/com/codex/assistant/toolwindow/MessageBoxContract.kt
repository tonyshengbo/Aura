package com.codex.assistant.toolwindow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class MessageBoxState(
    val forceMessages: Boolean = false,
)

internal sealed interface MessageBoxIntent {
    data class RetryTool(val name: String, val input: String) : MessageBoxIntent
    data class RetryMessage(val content: String) : MessageBoxIntent
    data class CopyMessage(val messageId: String) : MessageBoxIntent
    data class OpenFile(val path: String) : MessageBoxIntent
    data class RetryCommand(val command: String, val cwd: String) : MessageBoxIntent
    data class CopyCommand(val command: String) : MessageBoxIntent
}

internal class MessageBoxViewModel(
    private val port: MessageBoxPort,
) {
    private val _state = MutableStateFlow(MessageBoxState())
    val state: StateFlow<MessageBoxState> = _state.asStateFlow()

    fun dispatch(intent: MessageBoxIntent) {
        when (intent) {
            is MessageBoxIntent.RetryTool -> port.retryTool(intent.name, intent.input)
            is MessageBoxIntent.RetryMessage -> port.retryMessage(intent.content)
            is MessageBoxIntent.CopyMessage -> port.copyMessage(intent.messageId)
            is MessageBoxIntent.OpenFile -> port.openFile(intent.path)
            is MessageBoxIntent.RetryCommand -> port.retryCommand(intent.command, intent.cwd)
            is MessageBoxIntent.CopyCommand -> port.copyCommand(intent.command)
        }
    }

    fun setForceMessages(forceMessages: Boolean) {
        _state.value = _state.value.copy(forceMessages = forceMessages)
    }
}

internal class MessageBoxAction(
    private val viewModel: MessageBoxViewModel,
) {
    fun onRetryTool(name: String, input: String) {
        viewModel.dispatch(MessageBoxIntent.RetryTool(name, input))
    }

    fun onRetryMessage(content: String) {
        viewModel.dispatch(MessageBoxIntent.RetryMessage(content))
    }

    fun onCopyMessage(messageId: String) {
        viewModel.dispatch(MessageBoxIntent.CopyMessage(messageId))
    }

    fun onOpenFile(path: String) {
        viewModel.dispatch(MessageBoxIntent.OpenFile(path))
    }

    fun onRetryCommand(command: String, cwd: String) {
        viewModel.dispatch(MessageBoxIntent.RetryCommand(command, cwd))
    }

    fun onCopyCommand(command: String) {
        viewModel.dispatch(MessageBoxIntent.CopyCommand(command))
    }
}
