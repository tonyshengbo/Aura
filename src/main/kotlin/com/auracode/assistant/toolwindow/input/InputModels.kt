package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.input.TextFieldValue

/** 回传来源用于确认草稿，不能通过文字相同猜测是否为旧快照。 */
internal data class DraftOrigin(val editorId: String, val sequence: Long)

internal data class InputDraft(
    val value: TextFieldValue,
    val revision: Long = 0,
    val origin: DraftOrigin? = null,
)

/** 只表达业务快捷键；文字、选词、剪贴板和撤销由 Compose 处理。 */
internal data class InputKey(
    val code: Int,
    val shift: Boolean = false,
    val control: Boolean = false,
    val meta: Boolean = false,
)

internal data class InputHighlight(val start: Int, val end: Int)

internal sealed interface InputCommand {
    data class Edit(val value: TextFieldValue) : InputCommand
    data class Invoke(val action: () -> Unit) : InputCommand
}

internal class InputController {
    internal var session: ComposeInputSession? = null

    fun requestFocus() = session?.requestFocus() ?: Unit

    /** 点击发送也必须先同步实时草稿，组合输入期间不发送。 */
    fun prepareSubmission(): Boolean = session?.prepareSubmission() ?: true
}
