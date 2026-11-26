package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.conversation.rememberConversationContextMenuRepresentation
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.input.LocalMainInputController
import com.auracode.assistant.toolwindow.input.LocalNativeInputProject
import com.auracode.assistant.toolwindow.input.NativeInputController
import com.auracode.assistant.toolwindow.input.NativeInputDraft
import com.auracode.assistant.toolwindow.input.NativeTextInput
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.auracode.assistant.toolwindow.submission.SlashSuggestionItem
import com.auracode.assistant.toolwindow.submission.SubmissionAreaState
import com.auracode.assistant.toolwindow.submission.SubmissionControlBar
import com.auracode.assistant.toolwindow.submission.SubmissionInputSection
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Desktop
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** 真实窗口测试：菜单跨过原生输入区仍可交互，补全不夺取 IME 焦点。 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
class WindowPopupUiTest : BasePlatformTestCase() {
    private val palette = assistantPalette(EffectiveTheme.LIGHT)

    fun testProductionModelMenuOverlapsEditorWithoutDetachingIt() = withFixture { ui ->
        val state = mutableStateOf(SubmissionAreaState(customModelIds = List(8) { "custom-$it" }))
        val draft = mutableStateOf(NativeInputDraft(TextFieldValue("中文草稿\n第二行\n第三行\n第四行\n第五行")))
        var selected = false
        ui.show(atScreenBottom = true) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                NativeTextInput(draft.value, palette, { draft.value = it }, controller = ui.controller)
                SubmissionControlBar(palette, state.value, false) { intent ->
                    when (intent) {
                        UiIntent.ToggleModelMenu -> state.value = state.value.copy(modelMenuExpanded = !state.value.modelMenuExpanded)
                        is UiIntent.SelectModel -> {
                            selected = true
                            state.value = state.value.copy(modelMenuExpanded = false, selectedModel = intent.model)
                        }
                        else -> Unit
                    }
                }
            }
        }
        val editor = ui.editor()
        val originalText = editor.document.text
        state.value = state.value.copy(modelMenuExpanded = true)
        val popup = ui.popup()
        val editorBounds = Rectangle(editor.contentComponent.locationOnScreen, editor.contentComponent.size)
        val overlap = popup.bounds.intersection(editorBounds)
        assertTrue("The reproducer must place menu rows above the native editor", overlap.width > 0 && overlap.height > 0)
        assertTrue(editor.contentComponent.isShowing)

        // 选择被原生编辑器覆盖过的位置；事件落在独立窗口的 Compose 菜单，而非编辑区。
        val popupPanel = popup.contentPane.components.single() as JComponent
        ui.click(popupPanel, Point(28, overlap.y + overlap.height / 2 - popup.y))
        ui.await("Model option was not selected") { selected && !popup.isDisplayable }
        assertSame(editor, ui.editor())
        assertEquals(originalText, editor.document.text)
    }

    fun testPopupTracksWindowAndAnchorResizesAndScrolls() = withFixture { ui ->
        val expanded = mutableStateOf(false)
        val anchorX = mutableStateOf(30)
        val count = mutableStateOf(2)
        val scroll = ScrollState(0)
        ui.show {
            Box(Modifier.offset(anchorX.value.dp, 40.dp).size(100.dp, 24.dp)) {
                WindowDropdownMenu(expanded.value, { expanded.value = false }, Modifier.heightIn(max = 160.dp), scrollState = scroll) {
                    repeat(count.value) { DropdownMenuItem(onClick = {}) { Text("Menu option $it", Modifier.width(170.dp)) } }
                }
            }
        }
        expanded.value = true
        val popup = ui.popup()
        val original = Rectangle(popup.bounds)
        ui.frame.setLocation(ui.frame.x + 32, ui.frame.y + 20)
        ui.await("Popup did not follow its owner window from $original") { popup.x == original.x + 32 && popup.y == original.y + 20 }
        anchorX.value += 20
        ui.await("Popup did not follow its Compose anchor") { popup.x == original.x + 52 }

        count.value = 20
        ui.await("Popup did not resize after content changed") { popup.height > original.height }
        val popupPanel = popup.contentPane.components.single() as JComponent
        val target = requireNotNull(SwingUtilities.getDeepestComponentAt(popupPanel, 40, 40))
        val local = SwingUtilities.convertPoint(popupPanel, Point(40, 40), target)
        target.dispatchEvent(MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
            local.x, local.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3))
        ui.await("Popup scroll wheel was not delivered") { scroll.value > 0 }
        count.value = 1
        ui.await("Popup did not shrink after filtering") { popup.height < original.height }

        // 点击宿主空白处关闭；随后重新打开并隐藏整个工具窗口，检查原生窗口也被释放。
        ui.click(ui.panel, Point(360, 230))
        ui.await("Outside click did not dismiss popup") { !expanded.value && !popup.isDisplayable }
        expanded.value = true
        val reopened = ui.popup()
        ui.frame.contentPane.remove(ui.panel)
        ui.await("Detached tool window retained its popup") { !reopened.isDisplayable && !expanded.value }
    }

    fun testProductionCompletionKeepsNativeFocusAndImeAndArrowCommands() = withFixture { ui ->
        val state = mutableStateOf(SubmissionAreaState(
            document = TextFieldValue("/", TextRange(1)),
            slashSuggestions = listOf(
                SlashSuggestionItem.Command("/plan", "/plan", "Plan"),
                SlashSuggestionItem.Command("/auto", "/auto", "Auto"),
            ),
        ))
        ui.show {
            SubmissionInputSection(palette, state.value, false) { intent ->
                state.value = when (intent) {
                    is UiIntent.UpdateDocument -> state.value.copy(document = intent.value, documentOrigin = intent.origin)
                    UiIntent.MoveSlashSelectionNext -> state.value.copy(activeSlashIndex = state.value.activeSlashIndex + 1)
                    UiIntent.DismissSlashPopup -> state.value.copy(slashPopupVisible = false)
                    else -> state.value
                }
            }
        }
        val editor = ui.editor()
        ui.focusEditor(editor)
        ui.await("Editor could not receive native focus") { editor.contentComponent.isFocusOwner }
        state.value = state.value.copy(slashPopupVisible = true)
        val popup = ui.popup()
        assertFalse(popup.focusableWindowState)
        assertSame(editor.contentComponent, KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)

        // 在实际补全窗口打开时执行绑定在原生编辑器上的导航命令。
        val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), null)
        val action = ActionUtil.getActions(editor.contentComponent).first { shortcut in it.shortcutSet.shortcuts }
        val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN,
            SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, editor))
        action.update(event)
        assertTrue(event.presentation.isEnabled)
        action.actionPerformed(event)
        ui.await("Completion arrow command was lost") { state.value.activeSlashIndex == 1 }
        ime(editor, "ni", 0)
        ui.await("IME preedit did not publish") { state.value.document.composition != null }
        assertEquals("/", state.value.document.text)
        ime(editor, "你", 1)
        ui.await("Chinese commit was lost while popup was visible") { state.value.document.text == "/你" }
        assertSame(editor, ui.editor())
        assertSame(editor.contentComponent, KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
        state.value = state.value.copy(slashPopupVisible = false)
        ui.await("Completion popup was not disposed") { !popup.isDisplayable }
    }

    fun testProductionContextMenuClickAndEscapeReleaseWindowAndRestoreFocus() = withFixture { ui ->
        val state = ContextMenuState()
        val draft = mutableStateOf(NativeInputDraft(TextFieldValue("保留输入")))
        var selected = false
        ui.show {
            Column {
                NativeTextInput(draft.value, palette, { draft.value = it }, controller = ui.controller)
                Box(Modifier.size(160.dp, 30.dp)) {
                    rememberConversationContextMenuRepresentation(palette).Representation(state) {
                        listOf(ContextMenuItem("Copy") { selected = true })
                    }
                }
            }
        }
        val editor = ui.editor()
        ui.focusEditor(editor)
        ui.await("Editor could not receive focus") { editor.contentComponent.isFocusOwner }
        state.status = ContextMenuState.Status.Open(Rect(10f, 0f, 11f, 1f))
        val popup = ui.popup()
        ui.click(popup.contentPane.components.single() as JComponent, Point(30, 20))
        ui.await("Context menu action was not delivered") { selected && !popup.isDisplayable }
        ui.await("Native focus was not restored") { editor.contentComponent.isFocusOwner }

        state.status = ContextMenuState.Status.Open(Rect(10f, 0f, 11f, 1f))
        val reopened = ui.popup()
        ui.await("Focusable popup did not acquire focus") { reopened.isFocused }
        val target = requireNotNull(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, '\u001b'))
        ui.await("Escape did not dismiss menu") { !reopened.isDisplayable && state.status is ContextMenuState.Status.Closed }
    }

    fun testMenuKeyboardSelectionAndAnchorToggle() = withFixture { ui ->
        val expanded = mutableStateOf(false)
        var selected = -1
        var menuFocused = false
        ui.show {
            Box(Modifier.offset(40.dp, 40.dp).size(100.dp, 30.dp).clickable { expanded.value = !expanded.value }) {
                Text("Models")
                WindowDropdownMenu(expanded.value, { expanded.value = false }, Modifier.onFocusChanged { menuFocused = it.hasFocus }) {
                    repeat(3) { index ->
                        DropdownMenuItem(onClick = { selected = index; expanded.value = false }) { Text("Model $index") }
                    }
                }
            }
        }
        ui.click(ui.panel, Point(70, 55))
        val popup = ui.popup()
        ui.await("Menu did not acquire focus") { popup.isFocused && menuFocused }
        ui.key(KeyEvent.VK_DOWN)
        ui.key(KeyEvent.VK_ENTER)
        ui.await("Menu keyboard selection failed") { selected == 0 && !popup.isDisplayable }

        ui.click(ui.panel, Point(70, 55))
        val reopened = ui.popup()
        // 原生系统先把焦点交还锚点窗口，再投递触发按钮的鼠标事件。
        reopened.dispatchEvent(WindowEvent(reopened, WindowEvent.WINDOW_LOST_FOCUS, ui.frame))
        ui.click(ui.panel, Point(70, 55))
        ui.await("Anchor toggle reopened its menu") { !expanded.value && !reopened.isDisplayable }
    }

    fun testNestedPopupUsesParentWindowAndReleasesWithIt() = withFixture { ui ->
        val outerExpanded = mutableStateOf(false)
        val innerExpanded = mutableStateOf(false)
        ui.show {
            Box(Modifier.offset(40.dp, 40.dp).size(100.dp, 30.dp)) {
                WindowDropdownMenu(outerExpanded.value, { outerExpanded.value = false }) {
                    Box(Modifier.size(150.dp, 40.dp).clickable { innerExpanded.value = true }) {
                        Text("Submenu")
                        WindowDropdownMenu(innerExpanded.value, { innerExpanded.value = false }) {
                            DropdownMenuItem(onClick = {}) { Text("Nested option") }
                        }
                    }
                }
            }
        }
        outerExpanded.value = true
        val outer = ui.popup()
        ui.click(outer.contentPane.components.single() as JComponent, Point(30, 30))
        ui.await("Nested popup did not use the parent popup as its owner") {
            outer.ownedWindows.any { it.isShowing }
        }
        val inner = outer.ownedWindows.single { it.isShowing }
        assertTrue(outer.isDisplayable)
        outerExpanded.value = false
        ui.await("Closing the parent leaked a nested popup") { !outer.isDisplayable && !inner.isDisplayable }
    }

    private fun withFixture(test: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            test(fixture)
            fixture.failures.firstOrNull()?.let { throw AssertionError("Popup rendering failed", it) }
        } finally {
            fixture.frame.dispose()
            fixture.panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
            assertFalse("Owned popup window leaked", fixture.frame.ownedWindows.any { it.isDisplayable })
        }
    }

    private inner class Fixture {
        val frame = JFrame("Aura window popup regression test")
        val panel = ComposePanel().apply { isDisposeOnRemove = false }
        val controller = NativeInputController()
        val failures = mutableListOf<Throwable>()

        fun show(atScreenBottom: Boolean = false, content: @Composable () -> Unit) {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                CompositionLocalProvider(
                    LocalWindowPopupOwner provides panel,
                    LocalNativeInputProject provides project,
                    LocalMainInputController provides controller,
                ) { MaterialTheme { content() } }
            }
            frame.contentPane.add(panel)
            frame.setSize(600, 360)
            val area = frame.graphicsConfiguration.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.graphicsConfiguration)
            frame.setLocation(area.x + 120,
                if (atScreenBottom) area.y + area.height - insets.bottom - frame.height - 20 else area.y + insets.top + 100)
            frame.isVisible = true
            frame.toFront()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true)
            }
            frame.requestFocus()
            await("Compose panel did not mount") { panel.isShowing }
        }

        fun popup(): ComposeDialog {
            await("Native popup window did not open") { frame.ownedWindows.any { it is ComposeDialog && it.isShowing } }
            return frame.ownedWindows.filterIsInstance<ComposeDialog>().single { it.isShowing }
        }

        fun editor(): EditorEx {
            await("Native editor did not mount") { controller.host?.component?.isShowing == true }
            val field = requireNotNull(controller.host).component.components.single() as EditorTextField
            return requireNotNull(field.getEditor(true))
        }

        fun focusEditor(editor: EditorEx) {
            await("Editor could not receive native focus") {
                if (!editor.contentComponent.isFocusOwner) {
                    frame.toFront()
                    frame.requestFocus()
                    editor.contentComponent.requestFocus()
                }
                editor.contentComponent.isFocusOwner
            }
        }

        fun await(message: String, condition: () -> Boolean) {
            try {
                PlatformTestUtil.waitWithEventsDispatching(message, {
                    failures.firstOrNull()?.let { throw AssertionError("Popup rendering failed", it) }
                    condition()
                }, 10)
            } catch (failure: AssertionError) {
                val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                throw AssertionError("$message; focus=${focus.focusOwner}; active=${focus.activeWindow}; " +
                    "frame=${frame.bounds}; popups=${frame.ownedWindows.map { "${it.bounds} showing=${it.isShowing}" }}", failure)
            }
        }

        fun click(canvas: JComponent, point: Point) {
            val target = requireNotNull(SwingUtilities.getDeepestComponentAt(canvas, point.x, point.y))
            val local = SwingUtilities.convertPoint(canvas, point, target)
            target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                local.x, local.y, 1, false, MouseEvent.BUTTON1))
            target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                local.x, local.y, 1, false, MouseEvent.BUTTON1))
            UIUtil.dispatchAllInvocationEvents()
        }

        fun key(code: Int) {
            val target = requireNotNull(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun ime(editor: EditorEx, text: String, committed: Int) {
        editor.contentComponent.dispatchEvent(InputMethodEvent(editor.contentComponent,
            InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString(text).iterator, committed,
            if (committed < text.length) TextHitInfo.leading(text.length - committed) else null, null))
    }
}
