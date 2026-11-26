package com.auracode.assistant.toolwindow.eventing

import com.auracode.assistant.toolwindow.execution.ToolUserInputPromptUiModel

internal object ToolWindowCoordinatorIds {
    fun newLocalTurnId(): String = "local-turn-${java.util.UUID.randomUUID()}"

    fun toolUserInputSourceId(prompt: ToolUserInputPromptUiModel): String {
        return "tool-user-input:${prompt.requestId}:${prompt.itemId}"
    }
}
