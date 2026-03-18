package com.codex.assistant.service

import com.codex.assistant.model.AgentRequest
import com.codex.assistant.model.EngineEvent
import com.codex.assistant.model.TimelineAction
import com.codex.assistant.persistence.chat.SQLiteChatSessionRepository
import com.codex.assistant.provider.AgentProvider
import com.codex.assistant.provider.AgentProviderFactory
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.settings.AgentSettingsService
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
    fun `second turn reuses persisted cli session id after service reload`() = runBlocking {
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
        ) { action ->
            if (action == TimelineAction.FinishTurn) {
                firstFinished.complete(Unit)
            }
        }
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
        ) { action ->
            if (action == TimelineAction.FinishTurn) {
                secondFinished.complete(Unit)
            }
        }
        withTimeout(2_000) { secondFinished.await() }

        assertEquals(1, firstProvider.requests.size)
        assertNull(firstProvider.requests[0].cliSessionId)
        assertEquals(1, secondProvider.requests.size)
        assertEquals("thread_1", secondProvider.requests[0].cliSessionId)
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

        override fun stream(request: AgentRequest): Flow<EngineEvent> = flow {
            requests += request
            emit(EngineEvent.AssistantTextDelta("ok"))
            emit(EngineEvent.SessionReady(sessionIds.removeFirst()))
            emit(EngineEvent.Completed(exitCode = 0))
        }

        override fun cancel(requestId: String) = Unit
    }
}
