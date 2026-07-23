package com.auracode.assistant.toolwindow

import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.ToolWindowEventHub
import com.auracode.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolWindowEventHubTest {
    @Test
    fun `ten thousand draft updates occupy one pending mailbox entry`() {
        val hub = ToolWindowEventHub()
        repeat(10_000) { index -> hub.publishUiIntent(UiIntent.InputChanged("draft-$index")) }

        assertEquals(1, hub.pendingEventCount)
        hub.close()
    }

    @Test
    fun `commands preserve draft ordering boundaries`() = runBlocking {
        val hub = ToolWindowEventHub()
        hub.publishUiIntent(UiIntent.InputChanged("before-send"))
        hub.publishUiIntent(UiIntent.SendPrompt)
        hub.publishUiIntent(UiIntent.InputChanged("after-send"))

        val received = mutableListOf<AppEvent>()
        hub.stream.take(3).collect(received::add)
        assertEquals(
            listOf(
                UiIntent.InputChanged("before-send"),
                UiIntent.SendPrompt,
                UiIntent.InputChanged("after-send"),
            ),
            received.map { assertIs<AppEvent.UiIntentPublished>(it).intent },
        )
        hub.close()
    }

    @Test
    fun `mailbox rejects overflow instead of growing beyond its hard limit`() {
        val rejected = mutableListOf<AppEvent>()
        val hub = ToolWindowEventHub(capacity = 2, overflowHandler = rejected::add)
        repeat(3) { hub.publishUiIntent(UiIntent.ToggleHistory) }

        assertEquals(2, hub.pendingEventCount)
        assertEquals(1, rejected.size)
        assertTrue(rejected.single() is AppEvent.UiIntentPublished)
        hub.close()
    }

    @Test
    fun `publishes ui intents to app event stream`() {
        val recorded = mutableListOf<AppEvent>()
        val hub = ToolWindowEventHub(recorder = recorded::add)
        hub.publishUiIntent(UiIntent.ToggleHistory)

        val event = recorded.last()
        val ui = assertIs<AppEvent.UiIntentPublished>(event)
        assertEquals(UiIntent.ToggleHistory, ui.intent)
    }

    @Test
    fun `stream does not drop burst events when consumer is slow`() = runBlocking {
        val hub = ToolWindowEventHub()
        val received = mutableListOf<AppEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            hub.stream.take(300).collect { event ->
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

        collector.join()
        assertEquals(300, received.size)
    }
}
