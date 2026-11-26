package com.auracode.assistant.toolwindow.input

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.auracode.assistant.toolwindow.shell.AgentToolWindowFactory
import com.auracode.assistant.toolwindow.shell.ComposeToolWindowPanel
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.InputMethodEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import javax.swing.JFrame

/** 挂载真正的 ComposePanel，覆盖平台内核测试触及不到的界面入口和 Swing 互操作生命周期。 */
@OptIn(ExperimentalComposeUiApi::class)
class NativeTextInputUiTest : BasePlatformTestCase() {
    @Suppress("DEPRECATION") // 测试动态注册工具窗口，必须用对称的注销 API 释放内容与服务。
    fun testToolWindowStartupMountsNativeInput() {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(id = "Aura Input Startup Test", anchor = ToolWindowAnchor.RIGHT, canCloseContent = false),
        )
        val failures = mutableListOf<Throwable>()
        val frame = JFrame("Aura tool window startup smoke test")
        try {
            // 从生产工具窗口工厂进入，不能只用原生编辑器或单个 Composable 代替启动路径。
            AgentToolWindowFactory().createToolWindowContent(project, toolWindow)
            val content = toolWindow.contentManager.contents.single().component
            assertTrue(content is ComposeToolWindowPanel)
            val panel = content.components.filterIsInstance<ComposePanel>().single()
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            frame.contentPane.add(content, BorderLayout.CENTER)
            frame.setSize(560, 720)
            frame.isVisible = true
            awaitUi("Tool window did not mount its native input", failures) {
                findEditor(content)?.isShowing == true
            }
            val editor = requireNotNull(findEditor(content)?.getEditor(true))
            ime(editor, "ni", committed = 0)
            ime(editor, "你", committed = 1)
            awaitUi("Tool window input did not accept Chinese", failures) { editor.document.text == "你" }
            repeat(3) { cycle ->
                // 工具窗口隐藏会移除整个 Swing 层级；和 Compose 内部切换遮罩是两条生命周期。
                frame.contentPane.remove(content)
                UIUtil.dispatchAllInvocationEvents()
                frame.contentPane.add(content, BorderLayout.CENTER)
                frame.validate()
                awaitUi("Tool window did not remount on cycle $cycle", failures) {
                    findEditor(content)?.isShowing == true
                }
                assertSame(editor, findEditor(content)?.editor)
                ime(editor, "好", committed = 1)
                val expected = "你" + "好".repeat(cycle + 1)
                awaitUi("Input failed after tool window remount", failures) { editor.document.text == expected }
            }
            // 隐藏状态关闭内容也必须释放保留的场景和原生编辑器。
            frame.contentPane.remove(content)
            UIUtil.dispatchAllInvocationEvents()
            assertFalse(editor.isDisposed)
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            toolWindow.contentManager.removeAllContents(true)
            manager.unregisterToolWindow(toolWindow.id)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    @Suppress("DEPRECATION") // 测试销毁真实工具窗口内容，需对称注销动态注册的窗口。
    fun testToolWindowContentDisposesWhileMounted() {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(id = "Aura Input Disposal Test", anchor = ToolWindowAnchor.RIGHT, canCloseContent = false),
        )
        val failures = mutableListOf<Throwable>()
        val frame = JFrame("Aura mounted content disposal test")
        try {
            AgentToolWindowFactory().createToolWindowContent(project, toolWindow)
            val content = toolWindow.contentManager.contents.single().component
            val panel = content.components.filterIsInstance<ComposePanel>().single()
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            frame.contentPane.add(content)
            frame.setSize(560, 720)
            frame.isVisible = true
            awaitUi("Tool window did not mount", failures) { findEditor(content)?.isShowing == true }
            val editor = requireNotNull(findEditor(content)?.editor)

            // 不先移除 Swing 视图，模拟关闭标签/项目时平台先释放 Content 的路径。
            toolWindow.contentManager.removeAllContents(true)
            awaitUi("Native editor was not released with tool window content", failures) { editor.isDisposed }
            assertNull(panel.parent)
        } finally {
            frame.dispose()
            toolWindow.contentManager.removeAllContents(true)
            manager.unregisterToolWindow(toolWindow.id)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    fun testInputMountsAndPreservesNativeEditorAcrossOverlayVisibilityChanges() {
        val visible = mutableStateOf(true)
        val draft = mutableStateOf(NativeInputDraft(TextFieldValue("")))
        val identity = mutableStateOf<Any?>("session-a")
        val controller = NativeInputController()
        val failures = mutableListOf<Throwable>()
        val panel = ComposePanel()
        val frame = JFrame("Aura native input smoke test")
        try {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                CompositionLocalProvider(
                    LocalNativeInputProject provides project,
                    LocalNativeInputVisible provides visible.value,
                ) {
                    NativeTextInput(
                        draft = draft.value,
                        identityKey = identity.value,
                        palette = assistantPalette(EffectiveTheme.LIGHT),
                        controller = controller,
                        onValueChange = { draft.value = it },
                    )
                }
            }
            frame.contentPane.add(panel, BorderLayout.CENTER)
            frame.setSize(480, 180)
            frame.isVisible = true
            awaitUi("Native input did not mount", failures) {
                controller.host?.component?.isShowing == true
            }
            val host = requireNotNull(controller.host)
            val field = host.component.components.single() as EditorTextField
            val editor = requireNotNull(field.getEditor(true))

            ime(editor, "ni", committed = 0)
            awaitUi("Composition did not reach the Compose draft", failures) { draft.value.value.composition != null }
            assertEquals("", draft.value.value.text)
            ime(editor, "你", committed = 1)
            awaitUi("Committed Chinese text did not reach the Compose draft", failures) {
                draft.value.value.text == "你" && draft.value.value.composition == null
            }

            visible.value = false
            awaitUi("Overlay did not detach the native view", failures) { !host.component.isShowing }
            assertSame(host, controller.host)

            visible.value = true
            awaitUi("Native input did not remount", failures) { host.component.isShowing }
            assertSame(host, controller.host)
            assertSame(editor, field.editor)
            assertEquals("你", editor.document.text)

            val previousHost = host
            draft.value = NativeInputDraft(TextFieldValue("另一个会话", TextRange(5)), revision = 20)
            identity.value = "session-b"
            awaitUi("Session switch did not replace its native input", failures) {
                controller.host !== previousHost && controller.host?.component?.isShowing == true
            }
            val sessionHost = requireNotNull(controller.host)
            val sessionField = sessionHost.component.components.single() as EditorTextField
            val sessionEditor = requireNotNull(sessionField.getEditor(true))
            assertEquals("另一个会话", sessionEditor.document.text)

            // 再次挂载后继续走 IME 和外部草稿更新，检查输入上下文与同步回路均可用。
            ime(sessionEditor, "hao", committed = 0)
            ime(sessionEditor, "好", committed = 1)
            awaitUi("Input stopped working after session switch", failures) { draft.value.value.text == "另一个会话好" }
            draft.value = NativeInputDraft(TextFieldValue("重置", TextRange(2)), revision = 100)
            awaitUi("External draft replacement did not reach the editor", failures) { sessionEditor.document.text == "重置" }
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
        assertNull(controller.host)
    }

    private fun awaitUi(message: String, failures: List<Throwable>, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, {
            failures.firstOrNull()?.let { throw AssertionError("Compose input failed", it) }
            condition()
        }, 10)
    }

    private fun findEditor(component: java.awt.Component): EditorTextField? {
        if (component is EditorTextField) return component
        return (component as? java.awt.Container)?.components?.firstNotNullOfOrNull(::findEditor)
    }

    private fun ime(editor: EditorEx, text: String, committed: Int) {
        editor.contentComponent.dispatchEvent(
            InputMethodEvent(
                editor.contentComponent,
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                AttributedString(text).iterator,
                committed,
                if (committed < text.length) TextHitInfo.leading(text.length - committed) else null,
                null,
            ),
        )
    }
}
