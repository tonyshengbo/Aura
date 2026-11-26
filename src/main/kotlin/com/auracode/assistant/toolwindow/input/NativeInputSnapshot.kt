package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import javax.swing.JComponent

/**
 * 业务草稿只投影已上屏文字。候选拼音留在原生 InputMethodRequests 管理的组合区，
 * 避免切换会话或保存草稿时把临时拼音保存成正文；折叠 composition 仅表达「正在选词」。
 */
internal fun composingNativeSnapshot(component: JComponent, lastCommitted: TextFieldValue): TextFieldValue {
    val requests = component.inputMethodRequests
        ?: return lastCommitted.copy(composition = TextRange(lastCommitted.selection.end))
    val iterator = requests.getCommittedText(0, requests.committedTextLength, null)
    val text = buildString {
        for (index in iterator.beginIndex until iterator.endIndex) append(iterator.setIndex(index))
    }
    val caret = requests.insertPositionOffset.coerceIn(0, text.length)
    return TextFieldValue(text, TextRange(caret), TextRange(caret))
}
