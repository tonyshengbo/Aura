package com.codex.assistant.toolwindow

import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.eventing.ToolWindowEventHub
import com.codex.assistant.toolwindow.eventing.UiIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ToolWindowEventHubTest {
    @Test
    fun `publishes ui intents to app event stream`() {
        val hub = ToolWindowEventHub()
        hub.publishUiIntent(UiIntent.ToggleHistory)

        val event = hub.events.value.last()
        val ui = assertIs<AppEvent.UiIntentPublished>(event)
        assertEquals(UiIntent.ToggleHistory, ui.intent)
    }

    @Test
    fun `publishes unified events to app event stream`() {
        val hub = ToolWindowEventHub()
        val unified = UnifiedEvent.ThreadStarted(threadId = "th_1")
        hub.publishUnifiedEvent(unified)

        val event = hub.events.value.last()
        val mapped = assertIs<AppEvent.UnifiedEventPublished>(event)
        assertEquals(unified, mapped.event)
    }
}
