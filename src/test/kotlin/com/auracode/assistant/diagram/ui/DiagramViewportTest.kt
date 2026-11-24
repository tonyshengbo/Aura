package com.auracode.assistant.diagram.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Constraints
import com.auracode.assistant.diagram.renderer.DiagramImage
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercises real pointer input under Markdown selection, including the former fullscreen crash. */
class DiagramViewportTest {
    @Test
    fun `fullscreen repeated zoom drag escape and reopen share the same state`() {
        val content = sampleContent()
        val fullscreen = DiagramFullscreenState()
        val palette = assistantPalette(EffectiveTheme.DARK)
        val scene = ImageComposeScene(300, 220) {
            MaterialTheme {
                DiagramFullscreenHost(palette, Modifier.fillMaxSize(), fullscreen) {
                    SelectionContainer {
                        DiagramSurfaceContent(content, palette, false, { fullscreen.open(content) })
                    }
                }
            }
        }
        try {
            render(scene)
            click(scene, 278f, 22f)
            assertSame(content, fullscreen.content)

            // Two clicks used to throw through SelectionManager into the IDE coroutine SPI.
            repeat(12) { click(scene, 225f, 30f) }
            assertEquals(8f, content.state.zoom)
            val beforeDrag = pixels(scene)
            scene.sendPointerEvent(PointerEventType.Press, Offset(150f, 120f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(210f, 145f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(240f, 160f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(240f, 160f))
            render(scene)
            assertTrue(content.state.panFraction != Offset.Zero)
            assertNotEquals(beforeDrag.getRGB(160, 120), pixels(scene).getRGB(160, 120))

            val zoom = content.state.zoom
            val pan = content.state.panFraction
            escape(scene)
            render(scene)
            assertNull(fullscreen.content)
            assertEquals(zoom, content.state.zoom)
            assertEquals(pan, content.state.panFraction)
            click(scene, 278f, 22f)
            assertSame(content, fullscreen.content)
            assertEquals(zoom, content.state.zoom)
            assertEquals(pan, content.state.panFraction)
            click(scene, 270f, 30f)
            assertNull(fullscreen.content)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `zooming leaves layout fixed and shrinking restores the complete image`() {
        val content = sampleContent()
        val palette = assistantPalette(EffectiveTheme.LIGHT)
        val scene = ImageComposeScene(300, 220) {
            MaterialTheme {
                DiagramSurfaceContent(content, palette, false, null)
            }
        }
        try {
            render(scene)
            val initial = pixels(scene)
            assertEquals(RED, initial.getRGB(70, 90))
            assertEquals(BLUE, initial.getRGB(230, 90))
            repeat(3) { click(scene, 278f, 22f) }
            assertTrue(content.state.zoom > 1f)
            assertNotEquals(initial.getRGB(30, 90), pixels(scene).getRGB(30, 90))
            repeat(30) { click(scene, 206f, 22f) }
            assertEquals(1f, content.state.zoom)
            assertEquals(initial.getRGB(30, 90), pixels(scene).getRGB(30, 90))
        } finally {
            scene.close()
        }
    }

    @Test
    fun `fullscreen resizes at maximum zoom without invalid bounds`() {
        val content = sampleContent()
        val fullscreen = DiagramFullscreenState().apply { open(content) }
        val palette = assistantPalette(EffectiveTheme.DARK)
        val scene = ImageComposeScene(300, 220) {
            MaterialTheme { DiagramFullscreenHost(palette, Modifier.fillMaxSize(), fullscreen) {} }
        }
        try {
            content.state.zoomBy(8f)
            for ((width, height) in listOf(300 to 220, 160 to 120, 900 to 600, 1 to 1, 300 to 220)) {
                scene.constraints = Constraints(maxWidth = width, maxHeight = height)
                render(scene)
                assertEquals(8f, content.state.zoom)
            }
            fullscreen.closeIfOwner(sampleContent())
            assertSame(content, fullscreen.content)
            fullscreen.closeIfOwner(content)
            assertNull(fullscreen.content)
        } finally {
            scene.close()
        }
    }

    private fun click(scene: ImageComposeScene, x: Float, y: Float) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
        render(scene)
    }

    @OptIn(InternalComposeUiApi::class)
    private fun escape(scene: ImageComposeScene) {
        scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
    }

    private fun render(scene: ImageComposeScene) {
        repeat(3) { frame += 16_000_000L; scene.render(frame).close() }
    }

    private fun pixels(scene: ImageComposeScene) = scene.render(frame).use { image ->
        image.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
    }

    /** Two color regions make zoom and drag behavior observable through real rendered pixels. */
    private fun sampleContent(): DiagramContent = Surface.makeRasterN32Premul(200, 100).use { surface ->
        surface.canvas.clear(RED)
        Paint().use { paint ->
            paint.color = BLUE
            surface.canvas.drawRect(Rect.makeXYWH(100f, 0f, 100f, 100f), paint)
        }
        surface.makeImageSnapshot().use { image ->
            val png = image.encodeToData()!!.use { it.bytes }
            DiagramContent(DiagramImage("<svg/>", 200f, 100f, png), image.toComposeImageBitmap(), DiagramViewState())
        }
    }

    private var frame = 0L

    companion object {
        private val RED = 0xFFFF0000.toInt()
        private val BLUE = 0xFF0000FF.toInt()
    }
}
