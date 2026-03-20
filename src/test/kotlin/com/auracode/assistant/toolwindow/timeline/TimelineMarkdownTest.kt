package com.auracode.assistant.toolwindow.timeline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineMarkdownTest {
    @Test
    fun `http and https links are considered safe`() {
        assertTrue(isSafeHttpUrl("https://example.com/a?b=1"))
        assertTrue(isSafeHttpUrl("http://localhost:8080/path"))
    }

    @Test
    fun `non-http links are rejected`() {
        assertFalse(isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(isSafeHttpUrl("file:///tmp/a.txt"))
        assertFalse(isSafeHttpUrl("mailto:test@example.com"))
        assertFalse(isSafeHttpUrl("not a url"))
    }
}
