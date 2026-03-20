package com.auracode.assistant.toolwindow

import com.auracode.assistant.protocol.UnifiedEvent
import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.ToolWindowEventHub
import com.auracode.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `stream does not drop burst events when consumer is slow`() = runBlocking {
        val hub = ToolWindowEventHub()
        val received = mutableListOf<AppEvent>()
        val collector: Job = launch(Dispatchers.Default) {
            hub.stream.collect { event ->
                received += event
                delay(5)
            }
        }

        repeat(300) { index ->
            hub.publishUiIntent(
                when (index % 2) {
                    0 -> UiIntent.ToggleHistory
                    else -> UiIntent.ToggleSettings
                },
            )
        }

        val deadline = System.currentTimeMillis() + 5_000
        while (received.size < 300 && System.currentTimeMillis() < deadline) {
            delay(20)
        }
        collector.cancelAndJoin()

        assertEquals(300, received.size)
    }
}
