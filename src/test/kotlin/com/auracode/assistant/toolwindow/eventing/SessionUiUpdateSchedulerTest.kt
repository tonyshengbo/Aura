package com.auracode.assistant.toolwindow.eventing

import com.auracode.assistant.session.kernel.SessionActivityStatus
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionMessageRole
import com.auracode.assistant.session.kernel.SessionTurnOutcome
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionUiUpdateSchedulerTest {
    @Test
    fun `coalesces repeated requests for one session`() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val flushed = CopyOnWriteArrayList<Pair<String, SessionUiFlushBatch>>()
        val latch = CountDownLatch(1)
        val scheduler = SessionUiUpdateScheduler(
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            intervalMs = 25,
            onFlush = { sessionId, batch ->
                flushed += sessionId to batch
                latch.countDown()
            },
        )

        repeat(5) {
            scheduler.request(
                "session-a",
                eventCount = 1,
                slices = setOf(SessionProjectionSlice.CONVERSATION),
                immediate = false,
            )
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, flushed.size)
        assertEquals("session-a", flushed.single().first)
        assertEquals(5, flushed.single().second.eventCount)
        assertEquals(5, flushed.single().second.requestCount)

        scheduler.cancel()
        dispatcher.close()
    }

    @Test
    fun `keeps pending updates isolated by session`() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val flushed = CopyOnWriteArrayList<Pair<String, SessionUiFlushBatch>>()
        val latch = CountDownLatch(2)
        val scheduler = SessionUiUpdateScheduler(
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            intervalMs = 20,
            onFlush = { sessionId, batch ->
                flushed += sessionId to batch
                latch.countDown()
            },
        )

        scheduler.request("session-a", 2, setOf(SessionProjectionSlice.CONVERSATION), immediate = false)
        scheduler.request("session-b", 3, setOf(SessionProjectionSlice.SUBMISSION), immediate = false)

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(mapOf("session-a" to 2, "session-b" to 3), flushed.associate { it.first to it.second.eventCount })

        scheduler.cancel()
        dispatcher.close()
    }

    @Test
    fun `immediate request flushes an existing pending update once`() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val flushed = CopyOnWriteArrayList<SessionUiFlushBatch>()
        val latch = CountDownLatch(1)
        val scheduler = SessionUiUpdateScheduler(
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            intervalMs = 5_000,
            onFlush = { _, batch ->
                flushed += batch
                latch.countDown()
            },
        )

        scheduler.request(
            "session-a",
            4,
            setOf(SessionProjectionSlice.CONVERSATION),
            immediate = false,
            conversationEntryIds = setOf("message-1"),
        )
        scheduler.request(
            "session-a",
            1,
            setOf(SessionProjectionSlice.CONVERSATION, SessionProjectionSlice.EXECUTION),
            immediate = true,
            conversationEntryIds = setOf("reasoning-1"),
        )

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, flushed.size)
        assertEquals(5, flushed.single().eventCount)
        assertEquals(2, flushed.single().requestCount)
        assertEquals(
            setOf(SessionProjectionSlice.CONVERSATION, SessionProjectionSlice.EXECUTION),
            flushed.single().slices,
        )
        assertEquals(setOf("message-1", "reasoning-1"), flushed.single().conversationEntryIds)

        scheduler.cancel()
        dispatcher.close()
    }

    @Test
    fun `only interaction critical events require immediate projection`() {
        assertTrue(requiresImmediateProjection(SessionDomainEvent.TurnStarted("turn-1")))
        assertTrue(requiresImmediateProjection(SessionDomainEvent.TurnCompleted("turn-1", SessionTurnOutcome.SUCCESS)))
        assertTrue(
            requiresImmediateProjection(
                SessionDomainEvent.MessageAppended("user-1", "turn-1", SessionMessageRole.USER, "hello"),
            ),
        )
        assertFalse(
            requiresImmediateProjection(
                SessionDomainEvent.MessageAppended("assistant-1", "turn-1", SessionMessageRole.ASSISTANT, "hello"),
            ),
        )
        assertFalse(
            requiresImmediateProjection(
                SessionDomainEvent.ReasoningUpdated("reasoning-1", "turn-1", SessionActivityStatus.RUNNING, "work"),
            ),
        )
    }

    @Test
    fun `maps domain events to the smallest safe projection slices`() {
        assertEquals(
            setOf(SessionProjectionSlice.CONVERSATION),
            projectionSlicesFor(
                SessionDomainEvent.MessageAppended(
                    "assistant-1",
                    "turn-1",
                    SessionMessageRole.ASSISTANT,
                    "hello",
                ),
            ),
        )
        assertEquals(
            setOf(SessionProjectionSlice.SUBMISSION),
            projectionSlicesFor(
                SessionDomainEvent.UsageUpdated(null, "turn-1", "model", 100, 10, 0, 2),
            ),
        )
        assertEquals(SessionProjectionSlice.ALL, projectionSlicesFor(SessionDomainEvent.TurnCompleted("turn-1", SessionTurnOutcome.SUCCESS)))
        assertEquals(
            setOf(
                SessionProjectionSlice.CONVERSATION,
                SessionProjectionSlice.EXECUTION,
                SessionProjectionSlice.SUBMISSION,
            ),
            projectionSlicesFor(SessionDomainEvent.ErrorAppended("error-1", "turn-1", "failed", terminal = true)),
        )
    }

    @Test
    fun `maps streaming events to incremental entry ids and terminal events to full rebuild`() {
        assertEquals(
            setOf("message-1", "reasoning-1"),
            incrementalConversationEntryIdsFor(
                listOf(
                    SessionDomainEvent.MessageAppended(
                        "message-1",
                        "turn-1",
                        SessionMessageRole.ASSISTANT,
                        "draft",
                    ),
                    SessionDomainEvent.ReasoningUpdated(
                        "reasoning-1",
                        "turn-1",
                        SessionActivityStatus.RUNNING,
                        "checking",
                    ),
                ),
            ),
        )
        assertEquals(
            emptySet(),
            incrementalConversationEntryIdsFor(listOf(SessionDomainEvent.ThreadStarted("thread-1"))),
        )
        assertEquals(
            null,
            incrementalConversationEntryIdsFor(listOf(SessionDomainEvent.TurnCompleted("turn-1", SessionTurnOutcome.SUCCESS))),
        )
        assertEquals(
            null,
            incrementalConversationEntryIdsFor(listOf(SessionDomainEvent.ErrorAppended("error-1", "turn-1", "failed"))),
        )
    }
}
