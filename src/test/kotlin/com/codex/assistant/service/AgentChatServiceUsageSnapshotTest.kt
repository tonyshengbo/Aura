package com.codex.assistant.service

import com.codex.assistant.model.AgentRequest
import com.codex.assistant.model.EngineEvent
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
import kotlin.test.assertNotNull

class AgentChatServiceUsageSnapshotTest {
    @Test
    fun `stores latest completed turn usage snapshot in the current session`() = runBlocking {
        val dbPath = createTempDirectory("chat-service-usage").resolve("chat.db")
        val provider = UsageReportingProvider()
        val service = createService(dbPath, provider)

        val finished = CompletableDeferred<Unit>()
        service.runAgent(
            engineId = "codex",
            model = "gpt-5.3-codex",
            prompt = "Summarize the repo",
            contextFiles = emptyList(),
        ) { action ->
            if (action == com.codex.assistant.model.TimelineAction.FinishTurn) {
                finished.complete(Unit)
            }
        }
        withTimeout(2_000) { finished.await() }

        val snapshot = assertNotNull(service.currentUsageSnapshot())
        assertEquals("gpt-5.3-codex", snapshot.model)
        assertEquals(400_000, snapshot.contextWindow)
        assertEquals(116_986, snapshot.inputTokens)
        assertEquals(93_440, snapshot.cachedInputTokens)
        assertEquals(3_202, snapshot.outputTokens)
        assertEquals("Est. 70% left", snapshot.headerLabel())

        val persistedRepository = SQLiteChatSessionRepository(dbPath)
        val persistedSnapshot = assertNotNull(persistedRepository.loadActiveSession()?.usageSnapshot)
        assertEquals("gpt-5.3-codex", persistedSnapshot.model)
        assertEquals(400_000, persistedSnapshot.contextWindow)
        assertEquals(116_986, persistedSnapshot.inputTokens)
        assertEquals(93_440, persistedSnapshot.cachedInputTokens)
        assertEquals(3_202, persistedSnapshot.outputTokens)

        val reloaded = createService(dbPath, provider = UsageReportingProvider())
        val reloadedSnapshot = assertNotNull(reloaded.currentUsageSnapshot())
        assertEquals("Est. 70% left", reloadedSnapshot.headerLabel())

        reloaded.dispose()
        service.dispose()
    }

    private fun createService(
        dbPath: java.nio.file.Path,
        provider: AgentProvider,
    ): AgentChatService {
        val registry = ProviderRegistry(
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
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        return AgentChatService(
            repository = SQLiteChatSessionRepository(dbPath),
            registry = registry,
            settings = settings,
        )
    }

    private class UsageReportingProvider : AgentProvider {
        override fun stream(request: AgentRequest): Flow<EngineEvent> = flow {
            emit(EngineEvent.AssistantTextDelta("done"))
            emit(
                EngineEvent.TurnUsage(
                    inputTokens = 116_986,
                    cachedInputTokens = 93_440,
                    outputTokens = 3_202,
                ),
            )
            emit(EngineEvent.Completed(exitCode = 0))
        }

        override fun cancel(requestId: String) = Unit
    }
}
