package com.codex.assistant.toolwindow

import com.codex.assistant.actions.QuickOpenAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileMentionInputAreaTest {
    @Test
    fun `shows requested hint in current input area`() {
        assertTrue(FileMentionInputArea.shouldShowHint(hasFocus = true, text = ""))
        assertTrue(FileMentionInputArea.shouldShowHint(hasFocus = false, text = "hello"))
        assertFalse(FileMentionInputArea.shouldShowHint(hasFocus = true, text = "hello"))
    }

    @Test
    fun `quick open action targets codex chat tool window`() {
        assertEquals("Codex Chat", QuickOpenAction.TOOL_WINDOW_ID)
    }
}
