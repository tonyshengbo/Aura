package com.auracode.assistant.service

import com.auracode.assistant.conversation.ConversationHistoryPage
import com.auracode.assistant.conversation.ConversationRef
import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.persistence.chat.SQLiteChatSessionRepository
import com.auracode.assistant.provider.AgentProvider
import com.auracode.assistant.provider.AgentProviderFactory
import com.auracode.assistant.provider.EngineCapabilities
import com.auracode.assistant.provider.EngineDescriptor
import com.auracode.assistant.provider.ProviderRegistry
import com.auracode.assistant.session.kernel.SessionActivityStatus
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.session.kernel.SessionState
import com.auracode.assistant.session.kernel.SessionStateReducer
import com.auracode.assistant.session.kernel.SessionToolUserInputQuestion
import com.auracode.assistant.session.kernel.SessionToolUserInputRequest
import com.auracode.assistant.session.kernel.SessionTurnOutcome
import com.auracode.assistant.settings.AgentSettingsService
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentChatServiceAsyncQuestionTest {
    @Test
    fun `service restores pending questions and durable answers against the captured session`() = runBlocking {
        val db = createTempDirectory("async-question-service").resolve("chat.db")
        val provider = HistoryProvider()
        var service = createService(db, provider)
        try {
            val originalSession = service.getCurrentSessionId()
            val persisted = CompletableDeferred<Unit>()
            service.runAgent(
                engineId = "codex", model = "test-model", prompt = "ask me", contextFiles = emptyList(),
                onTurnPersisted = { persisted.complete(Unit) },
            )
            withTimeout(5_000) { persisted.await() }
            service.dispose()
            service = createService(db, provider)

            val otherSession = service.createSession()
            // History restoration must use the captured session, even while another tab is selected.
            val restored = service.loadCurrentConversationReplay(20, sessionId = originalSession)
            var state = SessionStateReducer().reduceAll(SessionState.empty(originalSession, "codex"), restored.events)
            assertEquals("question-1", state.execution.toolUserInputs.values.single().requestId)
            assertEquals(otherSession, service.getCurrentSessionId())

            service.resolveAsyncQuestion(originalSession, "thread-1", "question-1", SessionActivityStatus.SUCCESS, "My answer")
            service.dispose()
            service = createService(db, provider)
            val resolved = service.loadCurrentConversationReplay(20, sessionId = originalSession)
            state = SessionStateReducer().reduceAll(SessionState.empty(originalSession, "codex"), resolved.events)
            assertTrue(state.execution.toolUserInputs.isEmpty())
            assertTrue(resolved.events.filterIsInstance<SessionDomainEvent.ToolUserInputResolved>().any { it.responseSummary == "My answer" })
        } finally {
            service.dispose()
        }
    }

    private fun createService(db: Path, provider: AgentProvider) = AgentChatService(
        repository = SQLiteChatSessionRepository(db),
        registry = ProviderRegistry(
            descriptors = listOf(EngineDescriptor(
                id = "codex",
                displayName = "Codex",
                models = listOf("test-model"),
                capabilities = EngineCapabilities(true, true, true, true),
            )),
            factories = listOf(object : AgentProviderFactory {
                override val engineId = "codex"
                override fun create() = provider
            }),
            defaultEngineId = "codex",
        ),
        settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) },
    )

    private class HistoryProvider : AgentProvider {
        private val events = listOf(
            SessionDomainEvent.ThreadStarted("thread-1"),
            SessionDomainEvent.TurnStarted("turn-1", "thread-1"),
            SessionDomainEvent.ToolUserInputRequested(SessionToolUserInputRequest(
                "question-1", "thread-1", "turn-1", "question-1",
                listOf(SessionToolUserInputQuestion("q1", "", "Which option?", emptyList())), SessionInputDelivery.ASYNC,
            )),
            SessionDomainEvent.TurnCompleted("turn-1", SessionTurnOutcome.SUCCESS),
        )

        override fun stream(request: AgentRequest) = flow { events.forEach { emit(it) } }
        override suspend fun loadInitialHistory(ref: ConversationRef, pageSize: Int) =
            ConversationHistoryPage(events, false, null)
        override fun cancel(requestId: String) = Unit
    }
}
