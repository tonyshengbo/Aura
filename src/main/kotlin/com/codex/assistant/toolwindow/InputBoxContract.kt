package com.codex.assistant.toolwindow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent

internal data class InputBoxState(
    val running: Boolean = false,
    val statusMessage: String = "",
    val statusLoading: Boolean = false,
)

internal sealed interface InputBoxIntent {
    data object PrimaryActionClicked : InputBoxIntent
    data class SdkChipClicked(val anchor: JButton) : InputBoxIntent
    data class ModeChipClicked(val anchor: JButton) : InputBoxIntent
    data class ModelChipClicked(val anchor: JButton) : InputBoxIntent
    data class ReasoningChipClicked(val anchor: JButton) : InputBoxIntent
    data object DismissEditorContextClicked : InputBoxIntent
    data class FileMentioned(val path: String) : InputBoxIntent
}

internal class InputBoxViewModel(
    private val port: InputBoxPort,
) {
    private val _state = MutableStateFlow(InputBoxState())
    val state: StateFlow<InputBoxState> = _state.asStateFlow()

    fun dispatch(intent: InputBoxIntent) {
        when (intent) {
            InputBoxIntent.PrimaryActionClicked -> port.primaryAction()
            is InputBoxIntent.SdkChipClicked -> port.showSdkMenu(intent.anchor)
            is InputBoxIntent.ModeChipClicked -> port.showModeMenu(intent.anchor)
            is InputBoxIntent.ModelChipClicked -> port.showModelMenu(intent.anchor)
            is InputBoxIntent.ReasoningChipClicked -> port.showReasoningMenu(intent.anchor)
            InputBoxIntent.DismissEditorContextClicked -> port.dismissEditorContext()
            is InputBoxIntent.FileMentioned -> port.fileMentioned(intent.path)
        }
    }

    fun setRunning(running: Boolean) {
        _state.value = _state.value.copy(running = running)
    }

    fun setStatus(message: String, loading: Boolean) {
        _state.value = _state.value.copy(statusMessage = message, statusLoading = loading)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = "", statusLoading = false)
    }
}

internal class InputBoxAction(
    private val view: InputBoxView,
    private val viewModel: InputBoxViewModel,
) {
    fun bind() {
        view.actionButton.addActionListener {
            viewModel.dispatch(InputBoxIntent.PrimaryActionClicked)
        }
        view.sdkButton.addActionListener {
            viewModel.dispatch(InputBoxIntent.SdkChipClicked(view.sdkButton))
        }
        view.modeChip.addActionListener {
            viewModel.dispatch(InputBoxIntent.ModeChipClicked(view.modeChip))
        }
        view.modelChip.addActionListener {
            viewModel.dispatch(InputBoxIntent.ModelChipClicked(view.modelChip))
        }
        view.reasoningChip.addActionListener {
            viewModel.dispatch(InputBoxIntent.ReasoningChipClicked(view.reasoningChip))
        }
        view.editorContextCloseButton.addActionListener {
            viewModel.dispatch(InputBoxIntent.DismissEditorContextClicked)
        }
        view.inputArea.onFileSelected { file ->
            viewModel.dispatch(InputBoxIntent.FileMentioned(file.path))
        }
        installInputKeyBindings()
    }

    private fun installInputKeyBindings() {
        val inputMap = view.inputArea.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = view.inputArea.actionMap
        inputMap.put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendPrompt")
        actionMap.put("sendPrompt", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                viewModel.dispatch(InputBoxIntent.PrimaryActionClicked)
            }
        })
        inputMap.put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break")
    }
}
