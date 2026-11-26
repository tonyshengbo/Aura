package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantMaterialColors
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Desktop
import java.awt.Robot
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame

/** 捕获实际屏幕合成结果，覆盖 Skia 不透明画布与 Swing 截图无法反映的窗口透明问题。 */
@OptIn(ExperimentalComposeUiApi::class)
class WindowPopupVisualTest : BasePlatformTestCase() {
    fun testDarkMenuHasTransparentMarginsAndRoundedCorners() = verifyMenu(EffectiveTheme.DARK)

    fun testLightMenuHasTransparentMarginsAndRoundedCorners() = verifyMenu(EffectiveTheme.LIGHT)

    private fun verifyMenu(theme: EffectiveTheme) {
        // IntelliJ 测试框架可能设置 Swing 离屏渲染，因此在创建场景前显式选择生产画布。
        val previousRenderer = System.setProperty("compose.swing.render.on.graphics", "false")
        val frame = JFrame("Aura popup visual test")
        val panel = ComposePanel()
        val failures = mutableListOf<Throwable>()
        val expanded = mutableStateOf(false)
        val palette = assistantPalette(theme)
        try {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                CompositionLocalProvider(LocalWindowPopupOwner provides panel) {
                    MaterialTheme(colors = assistantMaterialColors(theme, palette)) {
                        Box(Modifier.fillMaxSize().background(palette.appBg)) {
                            Box(Modifier.offset(90.dp, 50.dp).size(100.dp, 28.dp)) {
                                WindowDropdownMenu(expanded.value, { expanded.value = false }) {
                                    listOf("Low", "Medium", "High", "Max").forEach { label ->
                                        DropdownMenuItem(onClick = {}) {
                                            Text(label, Modifier.width(110.dp))
                                        }
                                    }
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
            if (Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true)
            }
            expanded.value = true
            await("Popup did not open", failures) { frame.ownedWindows.any { it.isShowing } }
            val popup = frame.ownedWindows.single { it.isShowing }
            val robot = Robot()
            val report = Path.of("build", "reports", "popup-visual", theme.name.lowercase())
            Files.createDirectories(report)
            val background = palette.appBg.toArgb()
            var rendered: BufferedImage? = null
            // 等待屏幕首帧真正上屏，而非只等待 Swing isShowing。
            await("Popup did not paint its content", failures) {
                val capture = robot.createScreenCapture(popup.bounds)
                rendered = capture
                !near(capture.getRGB(capture.width / 2, capture.height / 2), background, 2)
            }
            val capture = requireNotNull(rendered)
            ImageIO.write(capture, "png", report.resolve("menu.png").toFile())
            ImageIO.write(robot.createScreenCapture(frame.bounds), "png", report.resolve("window.png").toFile())
            // 外层透明区必须露出宿主背景；黑框即使交互测试全通过也会在此失败。
            for ((x, y) in listOf(1 to 1, capture.width - 2 to 1, 1 to capture.height - 2)) {
                assertTrue("$theme popup margin is opaque at ($x, $y)", near(capture.getRGB(x, y), background, 12))
            }
            val inset = PopupWindowPadding
            val corner = capture.getRGB(inset, inset)
            val surface = capture.getRGB(capture.width / 2, inset + 16)
            assertFalse("Rounded corner must expose the background", near(corner, surface, 5))
            assertTrue("Rounded corner contains an opaque artifact", near(corner, background, 18))
            assertTrue("Menu surface color did not follow $theme", near(surface, palette.timelineCardBg.toArgb(), 6))
            assertEquals("false", System.getProperty("compose.swing.render.on.graphics"))
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
            if (previousRenderer == null) System.clearProperty("compose.swing.render.on.graphics")
            else System.setProperty("compose.swing.render.on.graphics", previousRenderer)
        }
    }

    private fun await(message: String, failures: List<Throwable>, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, {
            failures.firstOrNull()?.let { throw AssertionError("Popup rendering failed", it) }
            condition()
        }, 10)
    }

    private fun near(actual: Int, expected: Int, tolerance: Int): Boolean =
        listOf(16, 8, 0).all { shift ->
            kotlin.math.abs(((actual ushr shift) and 255) - ((expected ushr shift) and 255)) <= tolerance
        }
}
