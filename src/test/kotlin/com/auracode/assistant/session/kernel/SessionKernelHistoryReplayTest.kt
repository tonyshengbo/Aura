package com.auracode.assistant.session.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Verifies that live updates and history replay converge into the same kernel state.
 */
class SessionKernelHistoryReplayTest {
    @Test
    fun `kernel journal retains only the latest snapshot across ten thousand streaming updates`() {
        val kernel = SessionKernel(sessionId = "session-stream", engineId = "codex")
        kernel.applyLiveEvent(SessionDomainEvent.TurnStarted(turnId = "turn-1", threadId = "thread-1"))

        repeat(10_000) { index ->
            kernel.applyLiveEvent(
                SessionDomainEvent.MessageAppended(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    role = SessionMessageRole.ASSISTANT,
                    text = "response-$index",
                ),
            )
        }

        val journal = kernel.snapshotEventLog()
        assertEquals(2, journal.size)
        assertEquals("response-9999", assertIs<SessionDomainEvent.MessageAppended>(journal.last()).text)
        val message = assertIs<SessionConversationEntry.Message>(
            kernel.currentState.conversation.entries.getValue("assistant-1"),
        )
        assertEquals("response-9999", message.text)
    }

    /**
     * Verifies that live application, initial replay, and older-history prepend converge to one state.
     */
    @Test
    fun `kernel converges live restore and prepend history into the same state`() {
        val events = sampleDomainEvents()
        val olderHistory = events.take(2)
        val recentHistory = events.drop(2)

        val liveKernel = SessionKernel(
            sessionId = "session-1",
            engineId = "codex",
        )
        events.forEach(liveKernel::applyLiveEvent)

        val restoreKernel = SessionKernel(
            sessionId = "session-1",
            engineId = "codex",
        )
        restoreKernel.restoreHistory(events)

        val prependKernel = SessionKernel(
            sessionId = "session-1",
            engineId = "codex",
        )
        prependKernel.restoreHistory(recentHistory)
        prependKernel.prependHistory(olderHistory)

        assertEquals(liveKernel.currentState, restoreKernel.currentState)
        assertEquals(liveKernel.currentState, prependKernel.currentState)
    }

    /**
     * Verifies that the kernel manager reuses kernels and mirrors runtime state into the registry.
     */
    @Test
    fun `kernel manager reuses kernels and updates runtime registry`() {
        val runtimeRegistry = SessionRuntimeRegistry()
        val manager = SessionKernelManager(
            runtimeRegistry = runtimeRegistry,
        )

        val firstKernel = manager.getOrCreate(
            sessionId = "session-1",
            engineId = "codex",
        )
        val secondKernel = manager.getOrCreate(
            sessionId = "session-1",
            engineId = "codex",
        )

        assertSame(firstKernel, secondKernel)

        firstKernel.applyLiveEvent(
            SessionDomainEvent.ThreadStarted(
                threadId = "thread-1",
            ),
        )
        firstKernel.applyLiveEvent(
            SessionDomainEvent.TurnStarted(
                turnId = "turn-1",
                threadId = "thread-1",
            ),
        )

        val binding = runtimeRegistry.binding("session-1")
        assertEquals("codex", binding?.engineId)
        assertEquals("thread-1", binding?.threadId)
        assertEquals("turn-1", binding?.turnId)

        manager.remove("session-1")
        assertNull(runtimeRegistry.binding("session-1"))
    }

    @Test
    fun `kernel manager releases sessions outside the retained memory set`() {
        val manager = SessionKernelManager()
        manager.getOrCreate("session-active", "codex")
        manager.getOrCreate("session-inactive", "codex")

        manager.retainSessions(setOf("session-active"))

        assertEquals(setOf("session-active"), manager.sessionIds())
        assertNull(manager.get("session-inactive"))
    }
}
