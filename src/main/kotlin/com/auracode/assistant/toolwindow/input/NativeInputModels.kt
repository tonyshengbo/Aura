package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.input.TextFieldValue
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JComponent

/** 编辑来源随草稿一起回传，用来源确认编辑，不能通过文本内容猜测是否为旧快照。 */
internal data class NativeDraftOrigin(val editorId: String, val sequence: Long)

internal data class NativeInputDraft(
    val value: TextFieldValue,
    val revision: Long = 0,
    val origin: NativeDraftOrigin? = null,
)

/** 快捷键只表达业务命令；文字、选词和组合输入全部交给原生控件处理。 */
internal data class NativeInputKey(
    val code: Int,
    val shift: Boolean = false,
    val control: Boolean = false,
    val meta: Boolean = false,
)

internal data class NativeInputHighlight(val start: Int, val end: Int)

internal sealed interface NativeInputCommand {
    data class Edit(val value: TextFieldValue) : NativeInputCommand
    data class Invoke(val action: () -> Unit) : NativeInputCommand
}

internal data class NativeInputStyle(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val highlight: Color,
    val fontSize: Float,
)

/** Compose 和 Swing 之间显式传递焦点，不再让两个框架同时充当文本输入目标。 */
internal class NativeInputController {
    internal var host: NativeInputHost? = null

    fun requestFocus() {
        host?.requestInputFocus()
    }

    fun prepareSubmission(): Boolean = host?.prepareSubmission() ?: true
}

internal data class NativeInputCallbacks(
    val onValueChange: (NativeInputDraft) -> Unit,
    val resolveCommand: (NativeInputKey, TextFieldValue) -> NativeInputCommand?,
    val onLayout: (height: Int, caret: Rectangle?) -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onDropFiles: ((List<String>) -> Unit)?,
)

internal interface NativeInputHost {
    val component: JComponent
    fun update(
        draft: NativeInputDraft,
        style: NativeInputStyle,
        placeholder: String,
        highlights: List<NativeInputHighlight>,
        callbacks: NativeInputCallbacks,
    )
    fun requestInputFocus()
    fun prepareSubmission(): Boolean
    fun dispose()
}
