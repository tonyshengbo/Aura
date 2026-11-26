package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantMaterialColors
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Desktop
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame

/** Screenshots verify that official Compose menus blend into the parent canvas in both themes. */
@OptIn(ExperimentalComposeUiApi::class)
class ComposePopupVisualTest : BasePlatformTestCase() {
    fun testDarkMenuHasRoundedCornersWithoutOpaqueWindowMargins() = verifyMenu(EffectiveTheme.DARK)

    fun testLightMenuHasRoundedCornersWithoutOpaqueWindowMargins() = verifyMenu(EffectiveTheme.LIGHT)

    private fun verifyMenu(theme: EffectiveTheme) {
        val frame = JFrame("Aura Compose popup visual test")
        val panel = ComposePanel()
        val failures = mutableListOf<Throwable>()
        val expanded = mutableStateOf(false)
        val palette = assistantPalette(theme)
        var bounds = Rect.Zero
        var density = 1f
        try {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                density = LocalDensity.current.density
                MaterialTheme(colors = assistantMaterialColors(theme, palette)) {
                    Box(Modifier.fillMaxSize().background(palette.appBg)) {
                        Box(Modifier.offset(90.dp, 50.dp).size(100.dp, 28.dp)) {
                            DropdownMenu(expanded.value, { expanded.value = false },
                                Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
                                listOf("Low", "Medium", "High", "Max").forEach { label ->
                                    DropdownMenuItem(onClick = {}) { Text(label, Modifier.width(110.dp)) }
                                }
                            }
                        }
                    }
                }
            }
            frame.contentPane.add(panel)
            frame.setBounds(160, 160, 560, 400)
            frame.isVisible = true
            frame.toFront()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true)
            }
            expanded.value = true
            await("Compose menu did not open", failures) { bounds.height > 0 }
            assertTrue("Menu created an AWT popup window", frame.ownedWindows.none { it.isDisplayable })
            val robot = Robot()
            val report = Path.of("build", "reports", "popup-visual", theme.name.lowercase())
            Files.createDirectories(report)
            val background = palette.appBg.toArgb()
            var rendered: BufferedImage? = null
            var crop = Rectangle()
            await("Compose menu did not paint", failures) {
                val origin = panel.locationOnScreen
                val padding = 12
                val left = (bounds.left / density).toInt()
                val top = (bounds.top / density).toInt()
                val width = (bounds.width / density).toInt()
                val height = (bounds.height / density).toInt()
                crop = Rectangle(origin.x + left - padding, origin.y + top - padding, width + padding * 2, height + padding * 2)
                val capture = robot.createScreenCapture(crop)
                rendered = capture
                !near(capture.getRGB(capture.width / 2, padding + 6), background, 6)
            }
            val capture = requireNotNull(rendered)
            ImageIO.write(capture, "png", report.resolve("menu.png").toFile())
            ImageIO.write(robot.createScreenCapture(frame.bounds), "png", report.resolve("window.png").toFile())
            for ((x, y) in listOf(1 to 1, capture.width - 2 to 1, 1 to capture.height - 2)) {
                assertTrue("$theme menu contains an opaque canvas margin at ($x, $y)", near(capture.getRGB(x, y), background, 12))
            }
            val corner = capture.getRGB(12, 12)
            val surface = capture.getRGB(capture.width / 2, 18)
            assertFalse("Menu surface did not paint", near(surface, background, 6))
            assertFalse("Menu lost its rounded corner", near(corner, surface, 5))
            assertTrue("Rounded corner contains an opaque artifact", near(corner, background, 30))
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun await(message: String, failures: List<Throwable>, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, {
            failures.firstOrNull()?.let { throw AssertionError("Compose menu rendering failed", it) }
            condition()
        }, 10)
    }

    private fun near(actual: Int, expected: Int, tolerance: Int): Boolean =
        listOf(16, 8, 0).all { shift ->
            kotlin.math.abs(((actual ushr shift) and 255) - ((expected ushr shift) and 255)) <= tolerance
        }
}
