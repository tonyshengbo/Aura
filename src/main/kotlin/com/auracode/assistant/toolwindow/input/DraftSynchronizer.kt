package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID

/** 输入框拥有编辑状态；Store 的回传只确认，显式外部编辑在组合输入结束后执行。 */
internal class DraftSynchronizer(initialDraft: InputDraft) {
    private val editorId = UUID.randomUUID().toString()
    private var sequence = 0L
    private var observedExternal = initialDraft
    private var latestRevision = initialDraft.revision
    private var published = initialDraft.value
    private var deferredExternal: TextFieldValue? = null

    fun publish(value: TextFieldValue): InputDraft? {
        if (published == value) return null
        published = value
        sequence++
        return InputDraft(value, sequence, DraftOrigin(editorId, sequence))
    }

    fun acceptExternal(draft: InputDraft, composing: Boolean): TextFieldValue? {
        if (draft == observedExternal || draft.revision < latestRevision) return null
        observedExternal = draft
        latestRevision = draft.revision
        if (draft.origin?.editorId == editorId) return null
        if (composing) {
            deferredExternal = draft.value
            return null
        }
        return acceptCommand(draft.value)
    }

    fun finishComposition(): TextFieldValue? {
        val deferred = deferredExternal ?: return null
        return acceptCommand(deferred)
    }

    private fun acceptCommand(value: TextFieldValue): TextFieldValue {
        deferredExternal = null
        published = value
        return value
    }
}
