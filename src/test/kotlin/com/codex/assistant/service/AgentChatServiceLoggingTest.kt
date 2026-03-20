package com.codex.assistant.service

import com.codex.assistant.model.AgentRequest
import com.codex.assistant.persistence.chat.SQLiteChatSessionRepository
import com.codex.assistant.provider.AgentProvider
import com.codex.assistant.provider.AgentProviderFactory
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.settings.AgentSettingsService
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnOutcome
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.protocol.UnifiedItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentChatServiceLoggingTest {
    @Test
    fun `logs codex remote conversation chain across turns`() = runBlocking {
        val provider = RecordingProvider(
            sessionIds = ArrayDeque(listOf("thread_1", "thread_1")),
        )
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
        val logs = mutableListOf<String>()
        val service = createServiceWithLogger(
            dbPath = createTempDirectory("chat-service-logging").resolve("chat.db"),
            registry = registry,
            settings = settings,
            logSink = logs::add,
        )

        val firstFinished = CompletableDeferred<Unit>()
        service.runAgent(
            engineId = "codex",
            model = "gpt-5.3-codex",
            prompt = "First turn",
            contextFiles = emptyList(),
            onTurnPersisted = { firstFinished.complete(Unit) },
        )
        withTimeout(2_000) { firstFinished.await() }

        val secondFinished = CompletableDeferred<Unit>()
        service.runAgent(
            engineId = "codex",
            model = "gpt-5.3-codex",
            prompt = "Second turn",
            contextFiles = emptyList(),
            onTurnPersisted = { secondFinished.complete(Unit) },
        )
        withTimeout(2_000) { secondFinished.await() }

        assertTrue(
            logs.any { it.contains("Codex chain request:") && it.contains("remoteConversationId=<none>") },
            "Expected a first-turn log without remoteConversationId, got: ${logs.joinToString()}",
        )
        assertTrue(
            logs.any { it.contains("Codex chain stored remote conversation:") && it.contains("remoteConversationId=thread_1") },
            "Expected a stored remoteConversationId log for thread_1, got: ${logs.joinToString()}",
        )
        assertTrue(
            logs.any { it.contains("Codex chain request:") && it.contains("remoteConversationId=thread_1") },
            "Expected a second-turn log with remoteConversationId=thread_1, got: ${logs.joinToString()}",
        )
        service.dispose()
    }

    private fun createServiceWithLogger(
        dbPath: java.nio.file.Path,
        registry: ProviderRegistry,
        settings: AgentSettingsService,
        logSink: (String) -> Unit,
    ): AgentChatService {
        return AgentChatService(
            repository = SQLiteChatSessionRepository(dbPath),
            registry,
            settings,
            { "." },
            logSink,
        )
    }

    private class RecordingProvider(
        private val sessionIds: ArrayDeque<String>,
    ) : AgentProvider {
        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = flow {
            emit(UnifiedEvent.ThreadStarted(threadId = sessionIds.removeFirst()))
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
