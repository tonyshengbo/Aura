package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.intellij.ui.components.JBPasswordField
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** 密钥输入采用平台密码控件，不能用普通编辑器加视觉遮罩来存放明文。 */
internal class NativePasswordInput(initialDraft: NativeInputDraft) : NativeInputSession(initialDraft) {
    private var lastCommitted = initialDraft.value.copy(composition = null)
    override val component = JBPasswordField().apply {
        text = initialDraft.value.text
        border = BorderFactory.createEmptyBorder()
        caretPosition = initialDraft.value.selection.start
        moveCaretPosition(initialDraft.value.selection.end)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = schedulePublish()
            override fun removeUpdate(event: DocumentEvent) = schedulePublish()
            override fun changedUpdate(event: DocumentEvent) = schedulePublish()
        })
        addCaretListener { schedulePublish() }
        attachNativeListeners(this)
    }

    override fun readValue(): TextFieldValue {
        if (composing) {
            val snapshot = composingNativeSnapshot(component, lastCommitted)
            lastCommitted = snapshot.copy(composition = null)
            return snapshot
        }
        val text = String(component.password)
        val selection = TextRange(component.caret.mark, component.caret.dot)
        return TextFieldValue(text, selection).also { lastCommitted = it }
    }

    override fun writeValue(value: TextFieldValue) {
        if (String(component.password) != value.text) component.text = value.text
        component.caretPosition = value.selection.start.coerceIn(0, component.document.length)
        component.moveCaretPosition(value.selection.end.coerceIn(0, component.document.length))
    }

    override fun configure(style: NativeInputStyle, placeholder: String, highlights: List<NativeInputHighlight>) {
        component.background = style.background
        component.foreground = style.foreground
        component.caretColor = style.accent
        component.selectionColor = style.highlight
        component.font = component.font.deriveFont(Font.PLAIN, style.fontSize)
        component.emptyText.text = placeholder
        component.accessibleContext.accessibleName = placeholder
    }

    override fun reportLayout() {
        if (disposed) return
        callbacks?.onLayout?.invoke(component.preferredSize.height, null)
    }

    override fun requestInputFocus() {
        component.requestFocusInWindow()
    }
}
