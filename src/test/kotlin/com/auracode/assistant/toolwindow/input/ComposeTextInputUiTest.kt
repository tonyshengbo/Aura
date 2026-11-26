package com.auracode.assistant.toolwindow.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.auracode.assistant.toolwindow.shell.AgentToolWindowFactory
import com.auracode.assistant.toolwindow.shell.ComposeToolWindowPanel
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Real ComposePanel / desktop input service coverage, including the production tool-window entry. */
@OptIn(ExperimentalComposeUiApi::class)
class ComposeTextInputUiTest : BasePlatformTestCase() {
    fun testComposeInputEntryPointLoadsWithJvmVerification() {
        val entryPoint = Class.forName("com.auracode.assistant.toolwindow.input.ComposeTextInputKt")
        assertTrue(entryPoint.declaredMethods.any { it.name == "ComposeTextInput" })
    }

    fun testOfficialOutlinedLabelFloatsOnFocusAndReturnsAfterClearing() = withInput(
        outlinedLabel = "Composer hint",
        initialFocus = false,
        palette = assistantPalette(EffectiveTheme.LIGHT).copy(
            textMuted = Color(0xFFFF00FF),
            accent = Color(0xFF007700),
        ),
    ) { ui ->
        val robot = Robot()
        val report = Path.of("build", "reports", "input-label")
        Files.createDirectories(report)
        fun capture(): BufferedImage {
            val origin = ui.panel.locationOnScreen
            return robot.createScreenCapture(Rectangle(origin.x, origin.y, ui.panel.width, ui.panel.height))
        }
        fun labelBounds(image: BufferedImage): Rectangle? {
            var left = image.width
            var top = image.height
            var right = -1
            var bottom = -1
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val red = (rgb ushr 16) and 255
                val green = (rgb ushr 8) and 255
                val blue = rgb and 255
                if (red > 180 && green < 100 && blue > 180) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            return if (right < 0) null else Rectangle(left, top, right - left + 1, bottom - top + 1)
        }
        fun awaitLabel(name: String, condition: (Rectangle) -> Boolean): Pair<BufferedImage, Rectangle> {
            var image: BufferedImage? = null
            var bounds: Rectangle? = null
            ui.await("Outlined label did not reach $name") {
                image = capture()
                bounds = labelBounds(requireNotNull(image))
                bounds?.let(condition) == true
            }
            val result = requireNotNull(image) to requireNotNull(bounds)
            ImageIO.write(result.first, "png", report.resolve("$name.png").toFile())
            return result
        }

        val (_, resting) = awaitLabel("empty-unfocused") { true }
        ui.focus()
        val (focusedImage, floated) = awaitLabel("empty-focused") { it.maxY < resting.y }

        // The official outline leaves a cutout behind its floating label instead of drawing a
        // permanent hint row above the editable text.
        fun isAccent(rgb: Int): Boolean {
            val red = (rgb ushr 16) and 255
            val green = (rgb ushr 8) and 255
            val blue = rgb and 255
            return green > 70 && red < 60 && blue < 70 && green > red + 30
        }
        val borderY = (0 until resting.y).firstOrNull { y ->
            (0 until focusedImage.width).count { x -> isAccent(focusedImage.getRGB(x, y)) } > focusedImage.width / 4
        }
        assertNotNull("Focused outline did not paint", borderY)
        assertFalse("Outline crosses the floating label", isAccent(focusedImage.getRGB(floated.centerX.toInt(), requireNotNull(borderY))))

        ui.type('a')
        ui.await("Typed text was not accepted by outlined input") { ui.session.value().text == "a" }
        ui.blur()
        awaitLabel("text-unfocused") { it.maxY < resting.y }

        val cleared = InputDraft(TextFieldValue(""), revision = 200)
        ui.draft.value = cleared
        ui.awaitRendered(cleared)
        ui.await("Outlined input did not clear") { ui.session.value().text.isEmpty() }
        awaitLabel("cleared-unfocused") { kotlin.math.abs(it.y - resting.y) <= 1 }
    }

    fun testConsecutiveChineseCompositionIgnoresStaleEchoes() = withInput { ui ->
        ui.ime("ni", 0)
        ui.awaitPublished { it.composition != null }
        assertEquals("ni", ui.session.value().text)
        assertEquals("", ui.published.last().value.text)
        val stalePreeditEcho = ui.published.last()

        ui.ime("你", 1)
        ui.awaitPublished { it.text == "你" && it.composition == null }
        ui.draft.value = stalePreeditEcho
        ui.awaitRendered(stalePreeditEcho)
        assertEquals("你", ui.session.value().text)
        assertEquals(TextRange(1), ui.session.value().selection)

        ui.ime("hao", 0)
        ui.awaitPublished { it.composition != null }
        assertEquals("你", ui.published.last().value.text)
        ui.ime("好", 1)
        ui.awaitPublished { it.text == "你好" && it.composition == null }
        assertEquals(TextRange(2), ui.session.value().selection)
        assertEquals(TextRange(2), ui.published.last().value.selection)
    }

    fun testPartialCommitAndCancellationNeverPublishPinyin() = withInput { ui ->
        ui.ime("你hao", 1)
        ui.awaitPublished { it.text == "你" && it.composition != null }
        assertEquals("你hao", ui.session.value().text)
        assertEquals(TextRange(1), ui.published.last().value.selection)
        ui.ime("好", 1)
        ui.awaitPublished { it.text == "你好" && it.composition == null }

        ui.ime("shi", 0)
        ui.awaitPublished { it.composition != null }
        ui.ime(null, 0)
        ui.awaitPublished { it.text == "你好" && it.composition == null }
        assertEquals("你好", ui.session.value().text)
        assertEquals(TextRange(2), ui.session.value().selection)
        assertTrue(ui.published.none { it.value.text.contains("hao") || it.value.text.contains("shi") })
    }

    fun testExternalReplacementWaitsForCompositionAndRestoresSelection() = withInput { ui ->
        ui.ime("ni", 0)
        ui.awaitPublished { it.composition != null }
        val replacement = InputDraft(TextFieldValue("中文 @文件", TextRange(3, 6)), revision = 10)
        ui.draft.value = replacement
        ui.awaitRendered(replacement)
        assertEquals("ni", ui.session.value().text)
        assertNotNull(ui.session.value().composition)

        ui.ime("你", 1)
        ui.await("Deferred completion was not applied") { ui.session.value().text == "中文 @文件" }
        assertEquals(TextRange(3, 6), ui.session.value().selection)
        assertNull(ui.session.value().composition)

        val cleared = InputDraft(TextFieldValue(""), revision = 11)
        ui.draft.value = cleared
        ui.awaitRendered(cleared)
        ui.await("External clear was not applied") { ui.session.value().text.isEmpty() }
        assertEquals(TextRange.Zero, ui.session.value().selection)
    }

    fun testSubmissionFlushesLatestCommitWithoutWaitingForAFrame() = withInput { ui ->
        ui.ime("ni", 0, drainEvents = false)
        assertFalse(ui.controller.prepareSubmission())
        assertEquals("", ui.published.last().value.text)
        assertNotNull(ui.published.last().value.composition)

        ui.ime("你", 1, drainEvents = false)
        assertTrue(ui.controller.prepareSubmission())
        assertEquals("你", ui.published.last().value.text)
        assertEquals(TextRange(1), ui.published.last().value.selection)
        assertNull(ui.published.last().value.composition)
    }

    fun testEnterStaysWithImeUntilCommitThenSendsTheLiveSnapshot() {
        val submitted = mutableListOf<String>()
        var businessResolutionCount = 0
        withInput(resolveCommand = { key, document ->
            businessResolutionCount++
            if (key.code == KeyEvent.VK_ENTER) InputCommand.Invoke { submitted += document.text } else null
        }) { fixture ->
            val ui = fixture
            ui.ime("ni", 0)
            ui.awaitPublished { it.composition != null }
            val before = businessResolutionCount
            ui.key(KeyEvent.VK_ENTER)
            assertEquals(before, businessResolutionCount)
            assertTrue(submitted.isEmpty())

            // Synthetic key dispatch has no OS candidate handler. Start the committed phase from
            // a clean document instead of depending on how that artificial Enter is interpreted.
            ui.ime(null, 0)
            val reset = InputDraft(TextFieldValue(""), revision = 100)
            ui.draft.value = reset
            ui.awaitRendered(reset)
            ui.await("Composition was not reset") { ui.session.value().text.isEmpty() && ui.session.value().composition == null }
            ui.ime("你", 1, drainEvents = false)
            ui.key(KeyEvent.VK_ENTER)
            assertEquals(listOf("你"), submitted)
            assertEquals("你", ui.published.last().value.text)
        }
    }

    fun testUserTypingSelectionReplacementAndUndoUseComposeEditing() = withInput { ui ->
        ui.type('a')
        ui.type('b')
        ui.await("Typed text was not accepted") { ui.session.value().text == "ab" }
        ui.key(KeyEvent.VK_A, menuModifier)
        ui.await("Select all did not use Compose selection") { ui.session.value().selection == TextRange(0, 2) }
        ui.type('c')
        ui.await("Selection replacement did not use live selection") { ui.session.value().text == "c" }
        ui.key(KeyEvent.VK_Z, menuModifier)
        ui.await("User replacement was not undoable") { ui.session.value().text == "ab" }
    }

    fun testShiftEnterInsertsAnUndoableNewlineInCompose() = withInput(
        initial = TextFieldValue("你好", TextRange(2)),
        resolveCommand = { key, _ ->
            if (key.code == KeyEvent.VK_ENTER && !key.shift) InputCommand.Invoke { error("Unexpected send") } else null
        },
    ) { ui ->
        ui.key(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
        ui.await("Shift Enter did not insert a newline") { ui.session.value().text == "你好\n" }
        assertEquals(TextRange(3), ui.session.value().selection)
        ui.key(KeyEvent.VK_Z, menuModifier)
        ui.await("Newline was not undoable") { ui.session.value().text == "你好" }
    }

    fun testOverlayDisablesInputWithoutReplacingItsEditingState() = withInput { ui ->
        ui.type('a')
        ui.await("Initial edit did not reach input") { ui.session.value().text == "a" }
        val original = ui.session
        val canvas = requireNotNull(focusTarget(ui.panel))
        ui.enabled.value = false
        ui.await("Disabled state did not reach Compose") { ui.renderedEnabled == false }
        UIUtil.dispatchAllInvocationEvents()
        val typed = KeyEvent(canvas, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'b')
        canvas.dispatchEvent(typed)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("a", ui.session.value().text)
        assertSame(original, ui.session)

        ui.enabled.value = true
        ui.await("Enabled state did not reach Compose") { ui.renderedEnabled == true }
        ui.focus()
        assertSame(original, ui.session)
        ui.type('c')
        ui.await("Input did not resume after overlay closed") { ui.session.value().text == "ac" }
        ui.key(KeyEvent.VK_Z, menuModifier)
        ui.await("Overlay destroyed Compose undo history") { ui.session.value().text != "ac" }
        assertFalse(ui.session.value().text.contains('b'))
    }

    fun testBusinessTextEditsParticipateInComposeUndo() = withInput(
        initial = TextFieldValue("@文件", TextRange(3)),
        resolveCommand = { key, document ->
            if (key.code == KeyEvent.VK_BACK_SPACE && document.text == "@文件") {
                InputCommand.Edit(TextFieldValue("", TextRange.Zero))
            } else null
        },
    ) { ui ->
        ui.key(KeyEvent.VK_BACK_SPACE)
        ui.await("Mention edit did not replace the document") { ui.session.value().text.isEmpty() }
        ui.key(KeyEvent.VK_Z, menuModifier)
        ui.await("Mention deletion was not undoable") { ui.session.value().text == "@文件" }
    }

    fun testSessionIdentityReplacesTheEditingStateAndDiscardsOldComposition() = withInput { ui ->
        ui.ime("ni", 0)
        ui.awaitPublished { it.composition != null }
        val old = ui.session
        val restored = InputDraft(TextFieldValue("另一个会话", TextRange(5)), revision = 20)
        ui.draft.value = restored
        ui.identity.value = "session-b"
        ui.await("Session identity did not replace Compose editing state") { ui.controller.session !== old }
        assertEquals("另一个会话", ui.session.value().text)
        assertNull(ui.session.value().composition)
        ui.focus()
        ui.ime("hao", 0)
        ui.ime("好", 1)
        ui.awaitPublished { it.text == "另一个会话好" && it.composition == null }
        assertFalse(old.value().text.contains("另一个会话"))
        assertFalse(old.value().text.endsWith("好"))
    }

    fun testSecretInputUsesComposeImeAndBlocksCopyAndCut() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val previous = clipboard.getContents(null)
        try {
            withInput(secret = true) { ui ->
                ui.ime("mi", 0)
                ui.awaitPublished { it.composition != null }
                assertEquals("", ui.published.last().value.text)
                assertFalse(ui.controller.prepareSubmission())
                ui.ime("密", 1)
                ui.awaitPublished { it.text == "密" && it.composition == null }
                assertTrue(ui.controller.prepareSubmission())
                clipboard.setContents(StringSelection("clipboard sentinel"), null)
                ui.key(KeyEvent.VK_A, menuModifier)
                ui.await("Secret selection was not set") { ui.session.value().selection == TextRange(0, 1) }
                ui.key(KeyEvent.VK_C, menuModifier)
                ui.key(KeyEvent.VK_X, menuModifier)
                assertEquals("clipboard sentinel", clipboard.getData(DataFlavor.stringFlavor))
                assertEquals("密", ui.session.value().text)
                assertNull(findNativeEditor(ui.panel))
            }
        } finally {
            if (previous != null) clipboard.setContents(previous, null)
        }
    }

    @Suppress("DEPRECATION")
    fun testProductionToolWindowKeepsComposeEditingStateAcrossDetachAndReattach() {
        withToolWindow { content, panel, frame, controller, failures ->
            awaitUi("Production Compose input did not mount", failures) { controller.session != null }
            assertNull(findNativeEditor(content))
            controller.requestFocus()
            awaitUi("Production input did not receive focus", failures) { focusTarget(panel) != null }
            ime(requireNotNull(focusTarget(panel)), "你", 1)
            awaitUi("Production input did not accept Chinese", failures) { controller.session?.value()?.text == "你" }
            val original = controller.session
            repeat(3) { cycle ->
                frame.contentPane.remove(content)
                UIUtil.dispatchAllInvocationEvents()
                frame.contentPane.add(content, BorderLayout.CENTER)
                frame.validate()
                controller.requestFocus()
                awaitUi("Production input did not remount on cycle $cycle", failures) { focusTarget(panel) != null }
                assertSame(original, controller.session)
                ime(requireNotNull(focusTarget(panel)), "好", 1)
                awaitUi("Input failed after remount", failures) { controller.session?.value()?.text == "你" + "好".repeat(cycle + 1) }
            }
            // Content disposal while detached must also release the retained Compose scene.
            frame.contentPane.remove(content)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    @Suppress("DEPRECATION")
    fun testProductionToolWindowDisposesItsComposeInputWhileMounted() {
        withToolWindow { _, _, _, controller, failures ->
            awaitUi("Production input did not mount", failures) { controller.session != null }
        }
    }

    private fun withInput(
        initial: TextFieldValue = TextFieldValue(""),
        secret: Boolean = false,
        outlinedLabel: String? = null,
        initialFocus: Boolean = true,
        palette: DesignPalette = assistantPalette(EffectiveTheme.LIGHT),
        resolveCommand: (InputKey, TextFieldValue) -> InputCommand? = { _, _ -> null },
        test: (InputFixture) -> Unit,
    ) {
        val ui = InputFixture(initial)
        try {
            ui.panel.exceptionHandler = WindowExceptionHandler { ui.failures += it }
            ui.panel.setContent {
                val renderedDraft = ui.draft.value
                val renderedEnabled = ui.enabled.value
                val focusManager = LocalFocusManager.current
                CompositionLocalProvider(LocalInputEnabled provides renderedEnabled) {
                    MaterialTheme {
                        Column(if (outlinedLabel == null) Modifier else Modifier.padding(top = 16.dp)) {
                            ComposeTextInput(
                                draft = renderedDraft,
                                identityKey = ui.identity.value,
                                palette = palette,
                                outlinedLabel = outlinedLabel,
                                controller = ui.controller,
                                secret = secret,
                                modifier = Modifier.fillMaxWidth().onGloballyPositioned { ui.mounted = true },
                                onFocusChanged = { ui.inputFocused = it },
                                resolveCommand = resolveCommand,
                                onValueChange = {
                                    ui.published += it
                                    ui.draft.value = it
                                },
                            )
                        }
                    }
                }
                SideEffect {
                    ui.renderedDraft = renderedDraft
                    ui.renderedEnabled = renderedEnabled
                    ui.clearComposeFocus = { focusManager.clearFocus(force = true) }
                }
            }
            ui.frame.contentPane.add(ui.panel, BorderLayout.CENTER)
            ui.frame.setBounds(100, 100, 480, 180)
            ui.frame.isVisible = true
            ui.await("Compose input did not mount") { ui.controller.session != null && ui.panel.isShowing && ui.mounted }
            if (initialFocus) ui.focus() else {
                activate(ui.frame, ui.failures)
                ui.blur()
            }
            test(ui)
            assertTrue(ui.failures.toString(), ui.failures.isEmpty())
        } finally {
            ui.frame.dispose()
            ui.panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
        assertNull(ui.controller.session)
    }

    @Suppress("DEPRECATION") // Symmetric teardown for a dynamically registered test tool window.
    private fun withToolWindow(test: (ComposeToolWindowPanel, ComposePanel, JFrame, InputController, List<Throwable>) -> Unit) {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(id = "Aura Compose Input Test", anchor = ToolWindowAnchor.RIGHT, canCloseContent = false),
        )
        val failures = mutableListOf<Throwable>()
        val frame = JFrame("Aura production Compose input test")
        var controller: InputController? = null
        try {
            AgentToolWindowFactory().createToolWindowContent(project, toolWindow)
            val content = toolWindow.contentManager.contents.single().component as ComposeToolWindowPanel
            val panel = content.components.filterIsInstance<ComposePanel>().single()
            val activeController = ComposeToolWindowPanel::class.java.getDeclaredField("inputController").let {
                it.isAccessible = true
                it.get(content) as InputController
            }
            controller = activeController
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            frame.contentPane.add(content, BorderLayout.CENTER)
            frame.setBounds(100, 100, 560, 720)
            frame.isVisible = true
            activate(frame, failures)
            test(content, panel, frame, activeController, failures)
            toolWindow.contentManager.removeAllContents(true)
            awaitUi("Compose input was not released with content", failures) { activeController.session == null }
            assertNull(panel.parent)
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            toolWindow.contentManager.removeAllContents(true)
            manager.unregisterToolWindow(toolWindow.id)
            UIUtil.dispatchAllInvocationEvents()
        }
        assertNull(controller?.session)
    }

    private inner class InputFixture(initial: TextFieldValue) {
        val controller = InputController()
        val draft = mutableStateOf(InputDraft(initial))
        val identity: MutableState<Any?> = mutableStateOf("session-a")
        val enabled = mutableStateOf(true)
        var renderedEnabled: Boolean? = null
        val published = mutableListOf<InputDraft>()
        val failures = mutableListOf<Throwable>()
        val panel = ComposePanel()
        val frame = JFrame("Aura Compose input test")
        var renderedDraft: InputDraft? = null
        var mounted = false
        var inputFocused = false
        var clearComposeFocus: () -> Unit = {}
        val session get() = requireNotNull(controller.session)

        fun await(message: String, condition: () -> Boolean) = awaitUi(message, failures, condition)
        fun awaitPublished(condition: (TextFieldValue) -> Boolean) {
            try {
                await("Input snapshot was not published") { published.lastOrNull()?.value?.let(condition) == true }
            } catch (failure: AssertionError) {
                throw AssertionError("Input snapshot: live=${session.value()}, last=${published.lastOrNull()}, " +
                    "inputFocused=$inputFocused, requests=${focusTarget(panel)?.inputMethodRequests}", failure)
            }
        }
        fun awaitRendered(value: InputDraft) = await("External draft was not observed by Compose") { renderedDraft == value }
        fun focus() {
            activate(frame, failures)
            panel.requestFocusInWindow()
            controller.requestFocus()
            try {
                await("Compose input did not receive keyboard focus") {
                    inputFocused && focusTarget(panel)?.inputMethodRequests != null
                }
            } catch (failure: AssertionError) {
                throw AssertionError("Compose focus: active=${frame.isActive}, focused=${frame.isFocused}, " +
                    "inputFocused=$inputFocused, panelShowing=${panel.isShowing}, " +
                    "owner=${KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner}, " +
                    "requests=${focusTarget(panel)?.inputMethodRequests}", failure)
            }
        }
        fun blur() {
            clearComposeFocus()
            await("Compose input did not lose focus") { !inputFocused }
        }
        fun ime(text: String?, committed: Int, drainEvents: Boolean = true) {
            this@ComposeTextInputUiTest.ime(requireNotNull(focusTarget(panel)), text, committed)
            if (drainEvents) UIUtil.dispatchAllInvocationEvents()
        }
        fun key(code: Int, modifiers: Int = 0) {
            val target = requireNotNull(focusTarget(panel))
            val char = if (code == KeyEvent.VK_ENTER) '\n' else KeyEvent.CHAR_UNDEFINED
            val pressed = KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, code, char)
            target.dispatchEvent(pressed)
            if (code == KeyEvent.VK_ENTER && !pressed.isConsumed) {
                target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, KeyEvent.VK_UNDEFINED, '\n'))
            }
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers, code, char))
            UIUtil.dispatchAllInvocationEvents()
        }
        fun type(char: Char) {
            val target = requireNotNull(focusTarget(panel))
            val code = KeyEvent.getExtendedKeyCodeForChar(char.code)
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, char))
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, char))
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, char))
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun awaitUi(message: String, failures: List<Throwable>, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, {
            failures.firstOrNull()?.let { throw AssertionError("Compose input failed", it) }
            condition()
        }, 10)
    }

    private fun activate(frame: JFrame, failures: List<Throwable>) {
        frame.toFront()
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
            Desktop.getDesktop().requestForeground(true)
        }
        frame.requestFocus()
        awaitUi("Input test frame did not activate", failures) { frame.isActive }
    }

    private fun focusTarget(panel: ComposePanel): Component? {
        val target = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return null
        return target.takeIf { it !== panel && SwingUtilities.isDescendingFrom(it, panel) && it.isShowing }
    }

    private fun findNativeEditor(component: Component): EditorTextField? {
        if (component is EditorTextField) return component
        return (component as? Container)?.components?.firstNotNullOfOrNull(::findNativeEditor)
    }

    private fun ime(target: Component, text: String?, committed: Int) {
        target.dispatchEvent(InputMethodEvent(target, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            text?.let { AttributedString(it).iterator }, committed,
            if (text != null && committed < text.length) TextHitInfo.leading(text.length - committed) else null, null))
    }

    private val menuModifier: Int
        get() = if (System.getProperty("os.name").startsWith("Mac")) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
}
