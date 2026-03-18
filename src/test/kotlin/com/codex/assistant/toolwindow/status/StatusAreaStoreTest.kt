package com.codex.assistant.toolwindow.status

import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.shared.UiText
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusAreaStoreTest {
    @Test
    fun `prompt accepted stores a localized running status reference`() {
        val store = StatusAreaStore()

        store.onEvent(AppEvent.PromptAccepted(prompt = "hello"))

        assertEquals(UiText.Bundle("status.running"), store.state.value.text)
    }

    @Test
    fun `engine errors preserve raw status messages`() {
        val store = StatusAreaStore()

        store.onEvent(AppEvent.UnifiedEventPublished(UnifiedEvent.Error("boom")))

        assertEquals(UiText.Raw("boom"), store.state.value.text)
    }
}
