package com.auracode.assistant.service.input

import com.auracode.assistant.persistence.input.SQLiteAsyncQuestionRepository
import com.auracode.assistant.session.kernel.SessionActivityStatus
import com.auracode.assistant.session.kernel.SessionConversationEntry
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.session.kernel.SessionRunStatus
import com.auracode.assistant.session.kernel.SessionState
import com.auracode.assistant.session.kernel.SessionStateReducer
import com.auracode.assistant.session.kernel.SessionToolUserInputQuestion
import com.auracode.assistant.session.kernel.SessionToolUserInputRequest
import com.auracode.assistant.session.kernel.SessionTurnOutcome
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncQuestionHistoryTest {
    private val request = SessionToolUserInputRequest(
        "question-1", "thread-1", "turn-1", "question-1",
        listOf(SessionToolUserInputQuestion("q1", "", "Which option?", emptyList())), SessionInputDelivery.ASYNC,
    )

    @Test
    fun `answered and skipped questions stay resolved after restart and repeated replay`() {
        for (status in listOf(SessionActivityStatus.SUCCESS, SessionActivityStatus.SKIPPED)) {
            val db = createTempDirectory("async-question-history").resolve("chat.db")
            val history = AsyncQuestionHistory(SQLiteAsyncQuestionRepository(db))
            history.remember("session-1", "thread-1", request)
            history.resolve("session-1", "thread-1", request.requestId, status, "My answer")
            val restored = AsyncQuestionHistory(SQLiteAsyncQuestionRepository(db))
            repeat(2) {
                val events = restored.replay("session-1", "thread-1", listOf(
                    SessionDomainEvent.TurnStarted("turn-1", "thread-1"),
                    SessionDomainEvent.ToolUserInputRequested(request.copy(threadId = "")),
                    SessionDomainEvent.TurnCompleted("turn-1", SessionTurnOutcome.SUCCESS),
                ), true)
                val state = SessionStateReducer().reduceAll(SessionState.empty("session-1", "codex"), events)
                assertTrue(state.execution.toolUserInputs.isEmpty())
                val entry = state.conversation.entries.values.filterIsInstance<SessionConversationEntry.ToolUserInput>().single()
                assertEquals(status, entry.status)
                assertEquals("My answer", entry.responseSummary)
            }
        }
    }

    @Test
    fun `unanswered questions outside the latest page restore only for their original session and thread`() {
        val db = createTempDirectory("async-question-history").resolve("chat.db")
        AsyncQuestionHistory(SQLiteAsyncQuestionRepository(db)).remember("session-1", "thread-1", request)
        val restored = AsyncQuestionHistory(SQLiteAsyncQuestionRepository(db))
        val events = restored.replay("session-1", "thread-1", emptyList(), true)
        val state = SessionStateReducer().reduceAll(SessionState.empty("session-1", "codex"), events)
        assertEquals(SessionRunStatus.IDLE, state.runtime.runStatus)
        assertEquals(request, state.execution.toolUserInputs.values.single())
        assertTrue(restored.replay("session-2", "thread-1", emptyList(), true).isEmpty())
        assertTrue(restored.replay("session-1", "thread-2", emptyList(), true).isEmpty())
        assertTrue(restored.replay("session-1", "thread-1", emptyList(), false).isEmpty())
    }
}
