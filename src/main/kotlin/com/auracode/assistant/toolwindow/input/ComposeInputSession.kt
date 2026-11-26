package com.auracode.assistant.toolwindow.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** TextFieldState 是唯一实时文档；业务草稿不参与 Compose 的逐字输入回路。 */
internal class ComposeInputSession(initialDraft: InputDraft) {
    val state = TextFieldState(initialDraft.value.text, initialDraft.value.selection)
    private val synchronizer = DraftSynchronizer(initialDraft)
    private var disposed = false
    var onValueChange: (InputDraft) -> Unit = {}
    var requestFocus: () -> Unit = {}
    var normalizeValue: (TextFieldValue, TextFieldValue) -> TextFieldValue = { _, value -> value }
    private var previousValue = initialDraft.value

    fun value() = TextFieldValue(state.text.toString(), state.selection, state.composition)

    fun acceptExternal(draft: InputDraft) {
        if (disposed) return
        synchronizer.acceptExternal(draft, state.composition != null)?.let(::replace)
    }

    fun flush() {
        if (disposed) return
        if (state.composition == null) synchronizer.finishComposition()?.let(::replace)
        var live = value()
        if (live.composition == null) {
            val normalized = normalizeValue(previousValue, live)
            // 业务层只约束选区。文字编辑必须由 TextField 自身的事务或显式外部命令完成。
            if (normalized.text == live.text && normalized.selection != live.selection) {
                state.edit { selection = normalized.selection }
                live = value()
            }
        }
        previousValue = live
        synchronizer.publish(committedInputSnapshot(live))?.let(onValueChange)
    }

    fun prepareSubmission(): Boolean {
        if (disposed) return false
        flush()
        return state.composition == null
    }

    fun replace(value: TextFieldValue) {
        state.edit {
            if (toString() != value.text) replace(0, length, value.text)
            selection = value.selection
        }
        previousValue = value.copy(composition = null)
    }

    fun dispose() {
        disposed = true
        onValueChange = {}
        requestFocus = {}
        normalizeValue = { _, value -> value }
    }
}

/** 临时拼音留在 TextFieldState；折叠 composition 仅向业务层表达「正在选词」。 */
internal fun committedInputSnapshot(value: TextFieldValue): TextFieldValue {
    val composition = value.composition ?: return value
    val start = composition.min
    val end = composition.max
    val text = value.text.removeRange(start, end)
    fun committedOffset(offset: Int): Int = when {
        offset <= start -> offset
        offset >= end -> offset - (end - start)
        else -> start
    }
    val selection = TextRange(committedOffset(value.selection.start), committedOffset(value.selection.end))
    return TextFieldValue(text, selection, TextRange(selection.end))
}
