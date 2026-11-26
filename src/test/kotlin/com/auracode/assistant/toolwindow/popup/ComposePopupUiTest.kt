package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.conversation.rememberConversationContextMenuRepresentation
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.input.ComposeTextInput
import com.auracode.assistant.toolwindow.input.LocalMainInputController
import com.auracode.assistant.toolwindow.input.InputController
import com.auracode.assistant.toolwindow.input.InputDraft
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.auracode.assistant.toolwindow.submission.SlashSuggestionItem
import com.auracode.assistant.toolwindow.submission.SubmissionAreaState
import com.auracode.assistant.toolwindow.submission.SubmissionInputSection
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Desktop
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Real ComposePanel regression tests: menus share the canvas with the editor and own no AWT windows. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
class ComposePopupUiTest : BasePlatformTestCase() {
    private val palette = assistantPalette(EffectiveTheme.LIGHT)

    fun testMenuOverlapsComposeEditorAndReceivesPointerClick() = withFixture { ui ->
        val expanded = mutableStateOf(false)
        val value = mutableStateOf(InputDraft(TextFieldValue("中文草稿\n第二行\n第三行\n第四行\n第五行")))
        var menu = Rect.Zero
        var firstItem = Rect.Zero
        var editor = Rect.Zero
        var selected = false
        ui.show {
            Box(Modifier.fillMaxSize()) {
                ComposeTextInput(value.value, palette, { value.value = it }, Modifier.size(360.dp, 220.dp)
                    .onGloballyPositioned { editor = it.boundsInWindow() })
                Box(Modifier.offset(20.dp, 260.dp).size(100.dp, 28.dp)) {
                    DropdownMenu(expanded.value, { expanded.value = false }, Modifier.onGloballyPositioned { menu = it.boundsInWindow() }) {
                        repeat(5) { index ->
                            DropdownMenuItem(
                                onClick = { selected = true; expanded.value = false },
                                modifier = Modifier.onGloballyPositioned { if (index == 0) firstItem = it.boundsInWindow() },
                            ) { Text("Model $index", Modifier.width(180.dp)) }
                        }
                    }
                }
            }
        }
        expanded.value = true
        ui.await("Menu did not open above its anchor") { menu.height > 0 && firstItem.height > 0 }
        assertTrue("Menu must overlap the editor in this regression", menu.overlaps(editor))
        ui.assertNoPopupWindows()
        ui.click(firstItem.center)
        ui.await("Menu row did not receive click above the editor") { selected && !expanded.value }
        assertEquals("中文草稿\n第二行\n第三行\n第四行\n第五行", value.value.value.text)
    }

    fun testMenuTracksAnchorResizesScrollsAndDismissesOutside() = withFixture { ui ->
        val expanded = mutableStateOf(false)
        val anchorX = mutableStateOf(30)
        val count = mutableStateOf(2)
        val scroll = ScrollState(0)
        var bounds = Rect.Zero
        ui.show {
            Box(Modifier.offset(anchorX.value.dp, 40.dp).size(100.dp, 24.dp)) {
                DropdownMenu(expanded.value, { expanded.value = false }, Modifier.heightIn(max = 160.dp)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }, scrollState = scroll) {
                    repeat(count.value) { DropdownMenuItem(onClick = {}) { Text("Menu option $it", Modifier.width(170.dp)) } }
                }
            }
        }
        expanded.value = true
        ui.await("Menu did not open") { bounds.height > 0 }
        val original = bounds
        anchorX.value += 20
        ui.await("Menu did not follow its Compose anchor") { bounds.left >= original.left + 19 * ui.density }
        val moved = bounds
        ui.frame.setLocation(ui.frame.x + 32, ui.frame.y + 20)
        assertEquals("Canvas coordinates changed when its owner moved", moved, bounds)
        count.value = 20
        ui.await("Menu did not resize after content changed") { bounds.height > original.height }
        ui.wheel(bounds.center, 3)
        ui.await("Scroll wheel was not delivered to the menu") { scroll.value > 0 }
        count.value = 1
        ui.await("Menu did not shrink after filtering") { bounds.height < original.height }
        ui.click(Offset(500f, 260f) * ui.density)
        ui.await("Outside click did not dismiss menu") { !expanded.value }
        ui.assertNoPopupWindows()
    }

    fun testProductionCompletionRetainsEditorFocusChineseCompositionAndArrowCommands() = withFixture { ui ->
        val state = mutableStateOf(SubmissionAreaState(
            document = TextFieldValue("/", TextRange(1)),
            slashSuggestions = listOf(
                SlashSuggestionItem.Command("/plan", "/plan", "Plan"),
                SlashSuggestionItem.Command("/auto", "/auto", "Auto"),
            ),
        ))
        val controller = InputController()
        var renderedState: SubmissionAreaState? = null
        ui.show {
            val currentState = state.value
            CompositionLocalProvider(LocalMainInputController provides controller) {
                SubmissionInputSection(palette, currentState, false) { intent ->
                    state.value = when (intent) {
                        is UiIntent.UpdateDocument -> state.value.copy(document = intent.value,
                            documentOrigin = intent.origin, documentVersion = state.value.documentVersion + 1)
                        UiIntent.MoveSlashSelectionNext -> state.value.copy(activeSlashIndex = state.value.activeSlashIndex + 1)
                        UiIntent.DismissSlashPopup -> state.value.copy(slashPopupVisible = false)
                        else -> state.value
                    }
                }
            }
            SideEffect { renderedState = currentState }
        }
        ui.await("Compose input did not mount") { controller.session != null }
        controller.requestFocus()
        ui.await("Editor did not receive native canvas focus") { ui.focusOwner()?.inputMethodRequests != null }
        val canvas = requireNotNull(ui.focusOwner())
        state.value = state.value.copy(slashPopupVisible = true)
        // The resolver sees the state applied by recomposition, just as the visible menu does.
        ui.await("Completion did not recompose before its first arrow command") {
            renderedState?.slashPopupVisible == true
        }
        ui.key(KeyEvent.VK_DOWN)
        ui.await("Completion arrow command was lost") { state.value.activeSlashIndex == 1 }
        assertSame(canvas, ui.focusOwner())
        canvas.dispatchEvent(InputMethodEvent(canvas, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            AttributedString("ni").iterator, 0, TextHitInfo.leading(2), null))
        ui.await("Preedit was lost while completion was open") { state.value.document.composition != null }
        assertEquals("/", state.value.document.text)
        canvas.dispatchEvent(InputMethodEvent(canvas, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            AttributedString("你").iterator, 1, null, null))
        ui.await("Chinese commit was lost while completion was open") {
            state.value.document.text == "/你" && state.value.document.composition == null
        }
        assertEquals(TextRange(2), state.value.document.selection)
        assertSame(canvas, ui.focusOwner())
        ui.assertNoPopupWindows()
    }

    fun testMenuKeyboardSelectionAndEscape() = withFixture { ui ->
        val expanded = mutableStateOf(false)
        var selected = -1
        var menuFocused = false
        var menuMounted = false
        ui.show {
            Box(Modifier.offset(40.dp, 40.dp).size(100.dp, 30.dp).clickable { expanded.value = !expanded.value }) {
                Text("Models")
                DropdownMenu(expanded.value, { expanded.value = false }, Modifier.onFocusChanged { menuFocused = it.hasFocus }) {
                    DisposableEffect(Unit) {
                        menuMounted = true
                        onDispose { menuMounted = false }
                    }
                    repeat(3) { index ->
                        DropdownMenuItem(onClick = { selected = index; expanded.value = false }) { Text("Model $index") }
                    }
                }
            }
        }
        ui.click(Offset(70f, 55f) * ui.density)
        ui.await("Menu did not mount") { menuMounted }
        ui.key(KeyEvent.VK_DOWN)
        ui.await("Keyboard navigation did not focus a menu item") { menuFocused }
        ui.key(KeyEvent.VK_ENTER)
        ui.await("Menu keyboard selection failed") { selected == 0 && !expanded.value && !menuMounted }
        menuFocused = false
        ui.click(Offset(70f, 55f) * ui.density)
        ui.await("Menu did not reopen") { menuMounted }
        ui.key(KeyEvent.VK_ESCAPE)
        ui.await("Escape did not dismiss menu") { !expanded.value }
        ui.assertNoPopupWindows()
    }

    fun testProductionContextMenuKeyboardActionAndEscapeRestoreEditorFocus() = withFixture { ui ->
        val state = ContextMenuState()
        val controller = InputController()
        val value = mutableStateOf(InputDraft(TextFieldValue("保留输入")))
        var inputFocused = false
        var selected = false
        var menuRenders = 0
        ui.show {
            Column {
                ComposeTextInput(value.value, palette, { value.value = it }, Modifier.size(340.dp, 70.dp),
                    controller = controller, onFocusChanged = { inputFocused = it })
                Box(Modifier.size(160.dp, 30.dp)) {
                    rememberConversationContextMenuRepresentation(assistantPalette(EffectiveTheme.LIGHT))
                        .Representation(state) {
                            menuRenders++
                            listOf(ContextMenuItem("Copy") { selected = true })
                        }
                }
            }
        }
        ui.await("Compose input did not mount") { controller.session != null }
        controller.requestFocus()
        ui.await("Editor did not receive focus") { inputFocused }
        state.status = ContextMenuState.Status.Open(Rect(10f, 0f, 11f, 1f))
        ui.await("Context menu did not render") { menuRenders > 0 }
        ui.key(KeyEvent.VK_DOWN)
        ui.key(KeyEvent.VK_ENTER)
        ui.await("Context menu keyboard action failed") { selected && state.status is ContextMenuState.Status.Closed }
        menuRenders = 0
        state.status = ContextMenuState.Status.Open(Rect(10f, 0f, 11f, 1f))
        ui.await("Context menu did not reopen") { menuRenders > 0 }
        ui.key(KeyEvent.VK_ESCAPE)
        ui.await("Escape did not dismiss context menu") { state.status is ContextMenuState.Status.Closed }
        assertTrue(inputFocused)
        val canvas = requireNotNull(ui.focusOwner())
        canvas.dispatchEvent(InputMethodEvent(canvas, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            AttributedString("你").iterator, 1, null, null))
        ui.await("Input did not regain IME events after closing the context menu") { value.value.value.text == "你保留输入" }
        ui.assertNoPopupWindows()
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
        }
    }

    private inner class Fixture {
        val frame = JFrame("Aura Compose popup regression test")
        val panel = ComposePanel().apply { isDisposeOnRemove = false }
        val failures = mutableListOf<Throwable>()
        var density = 1f
        private var mounted = false

        fun show(content: @Composable () -> Unit) {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                density = LocalDensity.current.density
                MaterialTheme { Box(Modifier.fillMaxSize().onGloballyPositioned { mounted = true }) { content() } }
            }
            frame.contentPane.add(panel)
            frame.setBounds(140, 120, 600, 360)
            frame.isVisible = true
            frame.toFront()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true)
            }
            frame.requestFocus()
            await("Compose panel did not mount") { panel.isShowing && mounted }
        }

        fun focusOwner() = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

        fun assertNoPopupWindows() {
            assertTrue("Compose menu opened a native window", frame.ownedWindows.none { it.isDisplayable })
        }

        fun await(message: String, condition: () -> Boolean) {
            PlatformTestUtil.waitWithEventsDispatching(message, {
                failures.firstOrNull()?.let { throw AssertionError("Popup rendering failed", it) }
                condition()
            }, 10)
        }

        fun click(position: Offset) {
            val point = Point((position.x / density).toInt(), (position.y / density).toInt())
            val target = requireNotNull(SwingUtilities.getDeepestComponentAt(panel, point.x, point.y))
            val local = SwingUtilities.convertPoint(panel, point, target)
            target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                local.x, local.y, 1, false, MouseEvent.BUTTON1))
            target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                local.x, local.y, 1, false, MouseEvent.BUTTON1))
            UIUtil.dispatchAllInvocationEvents()
        }

        fun wheel(position: Offset, rotation: Int) {
            val point = Point((position.x / density).toInt(), (position.y / density).toInt())
            val target = requireNotNull(SwingUtilities.getDeepestComponentAt(panel, point.x, point.y))
            val local = SwingUtilities.convertPoint(panel, point, target)
            target.dispatchEvent(MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                local.x, local.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation))
            UIUtil.dispatchAllInvocationEvents()
        }

        fun key(code: Int) {
            val target = requireNotNull(focusOwner())
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
            UIUtil.dispatchAllInvocationEvents()
        }
    }
}
