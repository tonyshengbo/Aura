package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID

/**
 * 编辑器是输入状态的唯一所有者。Store 回传的快照只用于确认，不重新写入编辑器，
 * 避免重组期间旧草稿覆盖原生输入法正在维护的文档、选区和组合态。
 */
internal class NativeDraftSynchronizer(initialDraft: NativeInputDraft) {
    private val editorId = UUID.randomUUID().toString()
    private var sequence = 0L
    private var observedExternal = initialDraft
    private var published = initialDraft.value
    private var deferredExternal: TextFieldValue? = null

    fun publish(value: TextFieldValue): NativeInputDraft? {
        if (published == value) return null
        published = value
        sequence++
        return NativeInputDraft(value, sequence, NativeDraftOrigin(editorId, sequence))
    }

    fun acceptExternal(draft: NativeInputDraft, composing: Boolean): TextFieldValue? {
        if (draft == observedExternal) return null
        observedExternal = draft
        if (draft.origin?.editorId == editorId) return null
        if (composing) {
            deferredExternal = draft.value
            return null
        }
        return acceptCommand(draft.value)
    }

    fun finishComposition(): TextFieldValue? {
        val deferred = deferredExternal ?: return null
        deferredExternal = null
        return acceptCommand(deferred)
    }

    private fun acceptCommand(value: TextFieldValue): TextFieldValue {
        deferredExternal = null
        published = value
        return value
    }
}
