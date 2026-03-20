package com.codex.assistant.toolwindow.drawer

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryConversationFormatterTest {
    @Test
    fun `formats thread status into friendly label`() {
        assertEquals("Running", formatHistoryStatus("active"))
        assertEquals("Ready", formatHistoryStatus("idle"))
        assertEquals("Unavailable", formatHistoryStatus("notLoaded"))
        assertEquals("Error", formatHistoryStatus("systemError"))
    }

    @Test
    fun `formats relative updated time`() {
        val nowMillis = 3_600_000L
        assertEquals("just now", formatHistoryUpdatedAt(updatedAtSeconds = 3_600L, nowMillis = nowMillis))
        assertEquals("5m ago", formatHistoryUpdatedAt(updatedAtSeconds = 3_300L, nowMillis = nowMillis))
        assertEquals("2h ago", formatHistoryUpdatedAt(updatedAtSeconds = -3_600L, nowMillis = nowMillis))
    }
}
