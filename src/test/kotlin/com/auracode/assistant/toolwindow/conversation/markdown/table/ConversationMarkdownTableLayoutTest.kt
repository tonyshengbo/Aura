package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationMarkdownTableLayoutTest {
    @Test
    fun `narrow viewports retain readable columns and allow overflow`() {
        val widths = conversationMarkdownTableColumnWidths(listOf(20.dp, 180.dp, 220.dp), 240.dp)

        assertEquals(listOf(120.dp, 180.dp, 220.dp), widths)
        assertTrue(widths.sumOf { it.value.toDouble() } > 240)
    }

    @Test
    fun `long content wraps within a bounded column`() {
        val widths = conversationMarkdownTableColumnWidths(listOf(2_000.dp, 150.dp), 400.dp)

        assertEquals(listOf(320.dp, 150.dp), widths)
    }

    @Test
    fun `resizing to a wider viewport shares spare space across columns`() {
        val preferred = listOf(120.dp, 180.dp)

        assertEquals(listOf(120.dp, 180.dp), conversationMarkdownTableColumnWidths(preferred, 240.dp))
        assertEquals(listOf(220.dp, 280.dp), conversationMarkdownTableColumnWidths(preferred, 500.dp))
    }

    @Test
    fun `unbounded measurement keeps finite widths`() {
        assertEquals(
            listOf(120.dp, 320.dp),
            conversationMarkdownTableColumnWidths(listOf(50.dp, 800.dp), Dp.Infinity),
        )
        assertEquals(emptyList(), conversationMarkdownTableColumnWidths(emptyList(), 400.dp))
    }
}
