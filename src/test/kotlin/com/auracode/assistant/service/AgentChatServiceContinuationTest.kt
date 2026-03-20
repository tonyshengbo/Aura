package com.auracode.assistant.service

import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.persistence.chat.SQLiteChatSessionRepository
import com.auracode.assistant.provider.AgentProvider
import com.auracode.assistant.provider.AgentProviderFactory
import com.auracode.assistant.provider.EngineCapabilities
import com.auracode.assistant.provider.EngineDescriptor
import com.auracode.assistant.provider.ProviderRegistry
import com.auracode.assistant.settings.AgentSettingsService
import com.auracode.assistant.protocol.TurnOutcome
import com.auracode.assistant.protocol.UnifiedEvent
import com.auracode.assistant.protocol.UnifiedItem
import com.auracode.assistant.protocol.ItemKind
import com.auracode.assistant.protocol.ItemStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentChatServiceContinuationTest {
    @Test
    fun `second turn reuses persisted remote conversation id after service reload`() = runBlocking {
        val dbPath = createTempDirectory("chat-service-continuation").resolve("chat.db")
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val firstProvider = RecordingProvider(sessionIds = ArrayDeque(listOf("thread_1")))
        val firstService = AgentChatService(
            repository = SQLiteChatSessionRepository(dbPath),
            registry = registry(provider = firstProvider),
            settings = settings,
        )
        firstService.recordUserMessage(prompt = "First turn")

        val firstFinished = CompletableDeferred<Unit>()
        firstService.runAgent(
            engineId = "codex",
            model = "gpt-5.3-codex",
            prompt = "First turn",
            contextFiles = emptyList(),
            onTurnPersisted = { firstFinished.complete(Unit) },
        )
        withTimeout(2_000) { firstFinished.await() }
        firstService.dispose()

        val secondProvider = RecordingProvider(sessionIds = ArrayDeque(listOf("thread_1")))
        val secondService = AgentChatService(
            repository = SQLiteChatSessionRepository(dbPath),
            registry = registry(provider = secondProvider),
            settings = settings,
        )
        val secondFinished = CompletableDeferred<Unit>()
        secondService.runAgent(
            engineId = "codex",
            model = "gpt-5.3-codex",
            prompt = "Second turn",
            contextFiles = emptyList(),
            onTurnPersisted = { secondFinished.complete(Unit) },
        )
        withTimeout(2_000) { secondFinished.await() }

        assertEquals(1, firstProvider.requests.size)
        assertNull(firstProvider.requests[0].remoteConversationId)
        assertEquals(1, secondProvider.requests.size)
        assertEquals("thread_1", secondProvider.requests[0].remoteConversationId)
        secondService.dispose()
    }

    private fun registry(provider: AgentProvider): ProviderRegistry {
        return ProviderRegistry(
            descriptors = listOf(
                EngineDescriptor(
                    id = "codex",
                    displayName = "Codex",
                    models = listOf("gpt-5.3-codex", "gpt-5.4"),
                    capabilities = EngineCapabilities(
                        supportsThinking = true,
                        supportsToolEvents = false,
                        supportsCommandProposal = false,
                        supportsDiffProposal = false,
                    ),
                ),
            ),
            factories = listOf(
                object : AgentProviderFactory {
                    override val engineId: String = "codex"
                    override fun create(): AgentProvider = provider
                },
            ),
            defaultEngineId = "codex",
        )
    }

    private class RecordingProvider(
        private val sessionIds: ArrayDeque<String>,
    ) : AgentProvider {
        val requests = mutableListOf<AgentRequest>()

        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = flow {
            requests += request
            val threadId = sessionIds.removeFirst()
            emit(UnifiedEvent.ThreadStarted(threadId = threadId))
            emit(
                UnifiedEvent.ItemUpdated(
                    UnifiedItem(
                        id = "${request.requestId}:assistant",
                        kind = ItemKind.NARRATIVE,
                        status = ItemStatus.SUCCESS,
                        name = "message",
                        text = "ok",
                    ),
                ),
            )
            emit(UnifiedEvent.TurnCompleted(turnId = "turn-1", outcome = TurnOutcome.SUCCESS))
        }

        override fun cancel(requestId: String) = Unit
    }
}
