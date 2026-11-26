package com.auracode.assistant.toolwindow.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import com.auracode.assistant.protocol.ItemStatus
import com.auracode.assistant.toolwindow.conversation.title.ConversationCardTitle
import com.auracode.assistant.toolwindow.shared.EffectiveTheme
import com.auracode.assistant.toolwindow.shared.assistantPalette
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.JFrame
import javax.swing.SwingUtilities
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

/** 恢复的命令记录可能比工具窗口宽；验证标题目标完全省略时的真实链接布局。 */
@OptIn(ExperimentalComposeUiApi::class)
class ConversationTitleUiTest : BasePlatformTestCase() {
    fun testTargetClickOpensFileAndPlainTitleClickTogglesCard() {
        var textLayout: TextLayoutResult? = null
        var origin = Offset.Zero
        var density = 1f
        var toggles = 0
        val opened = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val panel = ComposePanel()
        val frame = JFrame("Aura title link interaction test")
        try {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                density = LocalDensity.current.density
                MaterialTheme {
                    Box(Modifier.clickable { toggles++ }) {
                        ConversationCardTitle(
                            title = "Read File.kt",
                            titleIconPath = null,
                            titleTargetLabel = "File.kt",
                            titleTargetPath = "/project/File.kt",
                            palette = assistantPalette(EffectiveTheme.LIGHT),
                            onOpenTitleTarget = { opened += it },
                            modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() },
                            onTextLayout = { textLayout = it },
                        )
                    }
                }
            }
            frame.contentPane.add(panel)
            frame.setSize(560, 160)
            frame.isVisible = true
            PlatformTestUtil.waitWithEventsDispatching("Title did not render", { textLayout != null }, 10)
            val layout = requireNotNull(textLayout)

            click(panel, (origin + layout.getBoundingBox(6).center) / density)
            PlatformTestUtil.waitWithEventsDispatching("Title target did not open", { opened.isNotEmpty() }, 10)
            assertEquals(listOf("/project/File.kt"), opened)
            assertEquals(0, toggles)

            click(panel, (origin + layout.getBoundingBox(1).center) / density)
            PlatformTestUtil.waitWithEventsDispatching("Plain title did not toggle card", { toggles == 1 }, 10)
            assertEquals(1, opened.size)
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            frame.dispose()
            panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    fun testRestoredCommandLinksSurviveEllipsisAndWidthChanges() {
        val width = mutableStateOf(1)
        var measuredWidth = -1
        val failures = mutableListOf<Throwable>()
        val panel = ComposePanel()
        val frame = JFrame("Aura restored command title test")
        try {
            panel.exceptionHandler = WindowExceptionHandler { failures += it }
            panel.setContent {
                MaterialTheme {
                    Column {
                        Box(Modifier.width(width.value.dp).onGloballyPositioned { measuredWidth = width.value }) {
                            ConversationCommandItem(
                                node = ConversationActivityItem.CommandNode(
                                    id = "restored-command",
                                    sourceId = "command-history",
                                    title = "Read ${"very-long-prefix/".repeat(20)}NativeTextInput.kt",
                                    titleTargetLabel = "NativeTextInput.kt",
                                    titleTargetPath = "/project/src/NativeTextInput.kt",
                                    body = "",
                                    status = ItemStatus.SUCCESS,
                                    turnId = "restored-turn",
                                ),
                                palette = assistantPalette(EffectiveTheme.LIGHT),
                                animateStatusIndicator = false,
                                expanded = false,
                                onToggleExpanded = {},
                                onOpenTitleTarget = {},
                            )
                        }
                    }
                }
            }
            frame.contentPane.add(panel)
            frame.setSize(560, 240)
            frame.isVisible = true
            for (nextWidth in listOf(1, 36, 100, 500, 0, 300)) {
                width.value = nextWidth
                PlatformTestUtil.waitWithEventsDispatching("History title did not render at width $nextWidth", {
                    failures.firstOrNull()?.let { throw AssertionError("History title layout failed", it) }
                    measuredWidth == nextWidth
                }, 10)
                UIUtil.dispatchAllInvocationEvents()
                failures.firstOrNull()?.let { throw AssertionError("History title layout failed", it) }
            }
        } finally {
            frame.dispose()
            panel.dispose()
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun click(panel: ComposePanel, position: Offset) {
        val point = Point(position.x.toInt(), position.y.toInt())
        val target = requireNotNull(SwingUtilities.getDeepestComponentAt(panel, point.x, point.y))
        val local = SwingUtilities.convertPoint(panel, point, target)
        target.dispatchEvent(
            MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                local.x, local.y, 1, false, MouseEvent.BUTTON1),
        )
        target.dispatchEvent(
            MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                local.x, local.y, 1, false, MouseEvent.BUTTON1),
        )
        UIUtil.dispatchAllInvocationEvents()
    }
}
