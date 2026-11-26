package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import javax.swing.KeyStroke

/** 直接向 IDE 编辑组件投递 IME 事件，验证真实平台文档行为，而非复刻按键判断。 */
class NativeEditorInputPlatformTest : BasePlatformTestCase() {
    fun testComposeInputEntryPointLoadsWithJvmVerification() {
        // 输入内核测试不会引用 Compose 桥接类；显式加载入口，覆盖启动时的字节码校验。
        val entryPoint = Class.forName("com.auracode.assistant.toolwindow.input.NativeTextInputKt")
        assertTrue(entryPoint.declaredMethods.any { it.name == "NativeTextInput" })
    }

    fun testConsecutiveChineseCompositionIgnoresStaleDraftEchoes() {
        withInput { host, editor, published ->
            ime(editor, "ni", committed = 0)
            assertNotNull(published.last().value.composition)
            assertEquals("", published.last().value.text)
            val stale = published.last()

            ime(editor, "你", committed = 1)
            assertEquals("你", editor.document.text)
            assertNull(published.last().value.composition)

            update(host, stale, published)
            assertEquals("你", editor.document.text)
            ime(editor, "hao", committed = 0)
            ime(editor, "好", committed = 1)

            assertEquals("你好", editor.document.text)
            assertEquals(TextRange(2), published.last().value.selection)
        }
    }

    fun testPartialCommitAndCancellationDoNotLeakPinyin() {
        withInput { _, editor, published ->
            ime(editor, "你hao", committed = 1)
            assertNotNull(published.last().value.composition)
            assertEquals("你", published.last().value.text)
            ime(editor, "好", committed = 1)
            assertEquals("你好", editor.document.text)

            ime(editor, "shi", committed = 0)
            ime(editor, null, committed = 0)
            assertEquals("你好", editor.document.text)
            assertNull(published.last().value.composition)
        }
    }

    fun testExternalClearAndSelectionReplacementUseNativeDocument() {
        withInput { host, editor, published ->
            ime(editor, "中文", committed = 2)
            update(host, NativeInputDraft(TextFieldValue("中文 @文件", TextRange(6)), revision = 10), published)
            assertEquals("中文 @文件", editor.document.text)
            assertEquals(6, editor.caretModel.offset)

            update(host, NativeInputDraft(TextFieldValue(""), revision = 11), published)
            assertEquals("", editor.document.text)
            assertEquals(0, editor.caretModel.offset)
        }
    }

    fun testSubmissionFlushesCommittedTextWithoutWaitingForAUiFrame() {
        withInput { host, editor, published ->
            ime(editor, "ni", committed = 0)
            assertFalse(host.prepareSubmission())
            ime(editor, "你", committed = 1, drainEvents = false)

            assertTrue(host.prepareSubmission())
            assertEquals("你", published.last().value.text)
            assertNull(published.last().value.composition)
        }
    }

    fun testEnterIsOwnedByImeDuringCompositionAndByBusinessAfterCommit() {
        withInput { host, editor, published ->
            var sent = 0
            update(host, NativeInputDraft(TextFieldValue("")), published) { key, _ ->
                if (key.code == KeyEvent.VK_ENTER) NativeInputCommand.Invoke { sent++ } else null
            }
            val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null)
            val action = ActionUtil.getActions(editor.contentComponent).first { shortcut in it.shortcutSet.shortcuts }
            val context = SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, editor)
            fun event() = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context)

            ime(editor, "ni", committed = 0)
            val composingEvent = event()
            action.update(composingEvent)
            assertFalse(composingEvent.presentation.isEnabled)

            ime(editor, "你", committed = 1, drainEvents = false)
            val committedEvent = event()
            action.update(committedEvent)
            assertTrue(committedEvent.presentation.isEnabled)
            action.actionPerformed(committedEvent)
            assertEquals(1, sent)
            assertEquals("你", published.last().value.text)
        }
    }

    fun testProgrammaticCompletionRemainsUndoableInTheIde() {
        withInput { host, editor, published ->
            update(host, NativeInputDraft(TextFieldValue("@文件", TextRange(3)), revision = 1), published)
            val fileEditor = TextEditorProvider.getInstance().getTextEditor(editor)
            val undo = UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(fileEditor))
            undo.undo(fileEditor)
            assertEquals("", editor.document.text)
        }
    }

    fun testSecretInputUsesNativePasswordMaskAndPublishesOnlyCommittedText() {
        val host = NativePasswordInput(NativeInputDraft(TextFieldValue("")))
        val published = mutableListOf<NativeInputDraft>()
        try {
            update(host, NativeInputDraft(TextFieldValue("")), published)
            val field = host.component
            assertTrue(field.echoChar != '\u0000')
            field.dispatchEvent(
                InputMethodEvent(field, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString("mi").iterator, 0, TextHitInfo.leading(2), null),
            )
            UIUtil.dispatchAllInvocationEvents()
            assertFalse(host.prepareSubmission())
            assertEquals("", published.last().value.text)
            field.dispatchEvent(
                InputMethodEvent(field, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString("密").iterator, 1, null, null),
            )
            assertTrue(host.prepareSubmission())
            assertEquals("密", published.last().value.text)
        } finally {
            host.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun withInput(test: (NativeEditorInput, EditorEx, MutableList<NativeInputDraft>) -> Unit) {
        val host = NativeEditorInput(NativeInputDraft(TextFieldValue("")), 6, project)
        val published = mutableListOf<NativeInputDraft>()
        try {
            update(host, NativeInputDraft(TextFieldValue("")), published)
            val field = host.component.components.single() as EditorTextField
            val editor = requireNotNull(field.getEditor(true))
            UIUtil.dispatchAllInvocationEvents()
            test(host, editor, published)
        } finally {
            host.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun update(
        host: NativeInputHost,
        draft: NativeInputDraft,
        published: MutableList<NativeInputDraft>,
        resolveCommand: (NativeInputKey, TextFieldValue) -> NativeInputCommand? = { _, _ -> null },
    ) {
        host.update(
            draft,
            NativeInputStyle(Color.WHITE, Color.BLACK, Color.GRAY, Color.BLUE, Color.LIGHT_GRAY, 14f),
            "输入",
            emptyList(),
            NativeInputCallbacks(published::add, resolveCommand, { _, _ -> }, {}, null),
        )
    }

    private fun ime(editor: EditorEx, text: String?, committed: Int, drainEvents: Boolean = true) {
        editor.contentComponent.dispatchEvent(
            InputMethodEvent(
                editor.contentComponent,
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                text?.let { AttributedString(it).iterator },
                committed,
                if (text != null && committed < text.length) TextHitInfo.leading(text.length - committed) else null,
                null,
            ),
        )
        if (drainEvents) UIUtil.dispatchAllInvocationEvents()
    }
}
