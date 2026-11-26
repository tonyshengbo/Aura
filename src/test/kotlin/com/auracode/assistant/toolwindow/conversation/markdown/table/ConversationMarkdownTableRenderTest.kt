package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m2.Markdown
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises actual Compose measurement and input without launching an IDE or adding a UI test framework. */
class ConversationMarkdownTableRenderTest {
    @Test
    fun `wide table scrolls horizontally and clamps its offset after resizing`() {
        var height = 0
        val scrollState = ScrollState(0)
        val scene = tableScene("| First | Second | Third |\n| --- | --- | --- |\n| a | b | c |", scrollState) {
            height = it
        }
        try {
            renderFrames(scene)
            assertTrue(scrollState.maxValue > 0)

            val trackY = height - 4f
            scene.sendPointerEvent(PointerEventType.Press, Offset(10f, trackY))
            scene.sendPointerEvent(PointerEventType.Move, Offset(100f, trackY))
            scene.sendPointerEvent(PointerEventType.Release, Offset(100f, trackY))
            renderFrames(scene)
            assertTrue(scrollState.value > 0, "Dragging the visible scrollbar must move the table")

            val previousOffset = scrollState.value
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(80f, 40f), scrollDelta = Offset(-1f, 0f))
            renderFrames(scene)
            assertTrue(scrollState.value < previousOffset, "Horizontal wheel/trackpad input must reach the table")

            scene.constraints = Constraints(maxWidth = 900, maxHeight = 600)
            renderFrames(scene)
            assertEquals(0, scrollState.maxValue)
            assertEquals(0, scrollState.value)
        } finally {
            scene.close()
        }
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    fun `cell text remains selectable and can be copied`() {
        val clipboard = TableTestClipboard()
        val scrollState = ScrollState(0)
        var height = 0
        val scene = ImageComposeScene(240, 600) {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    TableMarkdown(
                        "| Details |\n| --- |\n| Copyable text |",
                        scrollState,
                        Modifier.onSizeChanged { height = it.height },
                    )
                }
            }
        }
        try {
            renderFrames(scene)
            val textY = height - 26f
            scene.sendPointerEvent(PointerEventType.Press, Offset(17f, textY))
            scene.sendPointerEvent(PointerEventType.Move, Offset(200f, textY))
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, textY))
            renderFrames(scene)

            val mac = System.getProperty("os.name").startsWith("Mac")
            scene.sendKeyEvent(
                KeyEvent(
                    key = Key.C,
                    type = KeyEventType.KeyDown,
                    isMetaPressed = mac,
                    isCtrlPressed = !mac,
                ),
            )
            assertEquals("Copyable text", clipboard.copiedText?.text?.trim())
        } finally {
            scene.close()
        }
    }

    @Test
    fun `streaming extra columns updates the existing scroll viewport`() {
        val content = mutableStateOf("| First |\n| --- |\n| a |")
        val scrollState = ScrollState(0)
        val scene = ImageComposeScene(240, 600) {
            MaterialTheme {
                TableMarkdown(content.value, scrollState)
            }
        }
        try {
            renderFrames(scene)
            assertEquals(0, scrollState.maxValue)

            content.value = "| First | Second | Third |\n| --- | --- | --- |\n| a | b | partial"
            renderFrames(scene)
            assertTrue(scrollState.maxValue > 0, "New streamed columns must enlarge the scroll range")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `long cell content grows vertically instead of being ellipsized`() {
        var height = 0
        val scrollState = ScrollState(0)
        val scene = tableScene("| Details |\n| --- |\n| Short |", scrollState) { height = it }
        try {
            renderFrames(scene)
            val shortHeight = height

            scene.setContent {
                MaterialTheme {
                    TableMarkdown(
                        "| Details |\n| --- |\n| ${"Long content with 中文 and a/path/to/file ".repeat(12)} |",
                        scrollState,
                        Modifier.onSizeChanged { height = it.height },
                    )
                }
            }
            renderFrames(scene)
            assertTrue(height > shortHeight * 2, "All lines of long cell text must contribute to its height")
        } finally {
            scene.close()
        }
    }

    private fun tableScene(
        content: String,
        scrollState: ScrollState,
        onHeight: (Int) -> Unit = {},
    ): ImageComposeScene = ImageComposeScene(240, 600) {
        MaterialTheme {
            TableMarkdown(content, scrollState, Modifier.onSizeChanged { onHeight(it.height) })
        }
    }

    @Composable
    private fun TableMarkdown(content: String, scrollState: ScrollState, modifier: Modifier = Modifier) {
        SelectionContainer {
            Markdown(
                content = content,
                modifier = modifier,
                components = markdownComponents(
                    table = { model ->
                        ConversationMarkdownTable(model.content, model.node, model.typography.text, scrollState)
                    },
                ),
            )
        }
    }

    /** Follow-up frames apply scrollbar visibility and input animations after the initial layout. */
    private fun renderFrames(scene: ImageComposeScene) {
        repeat(3) {
            frameTimeNanos += 16_000_000L
            scene.render(frameTimeNanos).close()
        }
    }

    private var frameTimeNanos = 0L

    private class TableTestClipboard : Clipboard {
        private var entry: ClipEntry? = null
        var copiedText: AnnotatedString? = null
            private set

        override suspend fun getClipEntry() = entry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
            val transferable = clipEntry?.nativeClipEntry as? Transferable
            copiedText = transferable?.getTransferData(DataFlavor.stringFlavor)?.toString()?.let(::AnnotatedString)
        }

        override val nativeClipboard: Any = java.awt.datatransfer.Clipboard("Table test clipboard")
    }
}
