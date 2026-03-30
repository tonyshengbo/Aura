package com.auracode.assistant.toolwindow.composer

import com.auracode.assistant.toolwindow.eventing.UiIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerControlBarTest {
    @Test
    fun `running engine keeps trailing action locked to cancel even when prompt has content`() {
        val state = resolveComposerTrailingActionState(
            running = true,
            hasPromptContent = true,
        )

        assertEquals("/icons/stop.svg", state.iconPath)
        assertEquals(UiIntent.CancelRun, state.intent)
        assertTrue(state.enabled)
        assertTrue(state.running)
    }

    @Test
    fun `idle engine keeps send disabled until prompt content exists`() {
        val emptyState = resolveComposerTrailingActionState(
            running = false,
            hasPromptContent = false,
        )
        val readyState = resolveComposerTrailingActionState(
            running = false,
            hasPromptContent = true,
        )

        assertEquals("/icons/send.svg", emptyState.iconPath)
        assertEquals(UiIntent.SendPrompt, emptyState.intent)
        assertFalse(emptyState.enabled)
        assertFalse(emptyState.running)

        assertEquals("/icons/send.svg", readyState.iconPath)
        assertEquals(UiIntent.SendPrompt, readyState.intent)
        assertTrue(readyState.enabled)
        assertFalse(readyState.running)
    }
}
