package com.auracode.assistant.toolwindow.conversation.markdown.diagram

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import com.auracode.assistant.diagram.renderer.DiagramFailure
import com.auracode.assistant.diagram.renderer.DiagramRenderRequest
import com.auracode.assistant.diagram.renderer.DiagramRenderResult
import com.auracode.assistant.diagram.renderer.DiagramRenderer
import com.auracode.assistant.diagram.ui.LocalDiagramRenderer
import com.auracode.assistant.toolwindow.conversation.ConversationMarkdown
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the real Markdown entry, including stream gating and selectable source fallback. */
class ConversationDiagramRenderTest {
    @Test
    fun `streaming waits for the closing fence and unrelated text does not render again`() {
        val content = mutableStateOf("```mermaid\nflowchart LR\nA --> B")
        val requests = mutableListOf<DiagramRenderRequest>()
        val renderer = DiagramRenderer { request ->
            requests += request
            DiagramRenderResult.Failed(DiagramFailure.UNAVAILABLE)
        }
        val scene = ImageComposeScene(360, 600) {
            MaterialTheme {
                CompositionLocalProvider(LocalDiagramRenderer provides renderer) {
                    ConversationMarkdown(content.value, assistantPalette(EffectiveTheme.LIGHT), Modifier)
                }
            }
        }
        try {
            settle(scene)
            assertTrue(requests.isEmpty())

            content.value += "\n```"
            settle(scene)
            assertEquals(1, requests.size)
            assertEquals("flowchart LR\nA --> B", requests.single().source)

            content.value += "\n\nFollowing paragraph"
            settle(scene)
            assertEquals(1, requests.size)
        } finally {
            scene.close()
        }
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    fun `unsupported types keep source selectable without any toolbar`() {
        val clipboard = TestClipboard()
        var requests = 0
        val scene = ImageComposeScene(500, 400) {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDiagramRenderer provides DiagramRenderer { requests++; error("Must not render") },
                    LocalClipboardManager provides clipboard,
                ) {
                    ConversationMarkdown(
                        "```mermaid\nsequenceDiagram\nA->>B: hello\n```",
                        assistantPalette(EffectiveTheme.LIGHT),
                    )
                }
            }
        }
        try {
            settle(scene)
            scene.sendPointerEvent(PointerEventType.Press, Offset(12f, 58f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(350f, 96f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(350f, 96f))
            settle(scene)
            val mac = System.getProperty("os.name").startsWith("Mac")
            scene.sendKeyEvent(KeyEvent(Key.C, KeyEventType.KeyDown, isMetaPressed = mac, isCtrlPressed = !mac))
            assertEquals(0, requests)
            assertEquals("sequenceDiagram\nA->>B: hello", clipboard.copied?.text?.trim())
        } finally {
            scene.close()
        }
    }

    private fun settle(scene: ImageComposeScene) {
        repeat(2) { frame += 16_000_000; scene.render(frame).close() }
        runBlocking { delay(180) }
        repeat(2) { frame += 16_000_000; scene.render(frame).close() }
    }

    private var frame = 0L

    private class TestClipboard : ClipboardManager {
        var copied: AnnotatedString? = null
        override fun getText() = copied
        override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
    }
}
