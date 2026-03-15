package com.codex.assistant.toolwindow

import javax.swing.JButton

internal interface TitleToolPort {
    fun startNewSession()
    fun startNewWindow()
}

internal interface MessageBoxPort {
    fun retryTool(name: String, input: String)
    fun retryMessage(content: String)
    fun copyMessage(messageId: String)
    fun openFile(path: String)
    fun retryCommand(command: String, cwd: String)
    fun copyCommand(command: String)
}

internal interface InputBoxPort {
    fun primaryAction()
    fun showSdkMenu(anchor: JButton)
    fun showModeMenu(anchor: JButton)
    fun showModelMenu(anchor: JButton)
    fun showReasoningMenu(anchor: JButton)
    fun dismissEditorContext()
    fun fileMentioned(path: String)
}
