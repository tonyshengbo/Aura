package com.codex.assistant.toolwindow.eventing

import com.codex.assistant.model.AgentRequest
import com.codex.assistant.provider.AgentProvider
import com.codex.assistant.provider.AgentProviderFactory
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.model.MessageRole
import com.codex.assistant.toolwindow.timeline.TimelineNode
import com.codex.assistant.settings.SavedAgentDefinition
import com.codex.assistant.settings.AgentSettingsService
import com.codex.assistant.toolwindow.composer.ComposerAreaStore
import com.codex.assistant.toolwindow.drawer.RightDrawerAreaStore
import com.codex.assistant.toolwindow.header.HeaderAreaStore
import com.codex.assistant.toolwindow.status.StatusAreaStore
import com.codex.assistant.toolwindow.timeline.TimelineAreaStore
import com.codex.assistant.persistence.chat.SQLiteChatSessionRepository
import com.codex.assistant.protocol.TurnOutcome
import com.codex.assistant.protocol.UnifiedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolWindowCoordinatorSubmitTest {
    @Test
    fun `submitting with a text attachment keeps composer context clean and still sends file content`() {
        val workingDir = createTempDirectory("coordinator-submit")
        val attachment = workingDir.resolve("notes.md")
        Files.writeString(attachment, "# Notes\nhello attachment")

        val provider = RecordingProvider()
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(provider),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val composerStore = ComposerAreaStore()
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = TimelineAreaStore(),
            composerStore = composerStore,
            rightDrawerStore = RightDrawerAreaStore(),
        )

        eventHub.publishUiIntent(UiIntent.AddAttachments(listOf(attachment.toString())))
        waitUntil(timeoutMs = 2_000) { composerStore.state.value.attachments.size == 1 }
        assertTrue(composerStore.state.value.contextEntries.isEmpty())

        eventHub.publishUiIntent(UiIntent.InputChanged("summarize"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { provider.requests.isNotEmpty() }
        val request = provider.requests.single()
        assertEquals("summarize", request.prompt)
        assertEquals(listOf(attachment.toString()), request.contextFiles.map { it.path })
        assertTrue(request.contextFiles.single().content.contains("hello attachment"))

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `submitting with selected agents keeps user prompt clean and passes agent instructions separately`() {
        val workingDir = createTempDirectory("coordinator-agent-submit")

        val provider = RecordingProvider()
        val settings = AgentSettingsService().apply {
            loadState(
                AgentSettingsService.State(
                    savedAgents = mutableListOf(
                        SavedAgentDefinition(
                            id = "agent-1",
                            name = "Reviewer",
                            prompt = "Review the answer before replying.",
                        ),
                    ),
                ),
            )
        }
        val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(provider),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val composerStore = ComposerAreaStore()
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = TimelineAreaStore(),
            composerStore = composerStore,
            rightDrawerStore = RightDrawerAreaStore(),
        )

        eventHub.publishUiIntent(
            UiIntent.SelectAgent(
                SavedAgentDefinition(
                    id = "agent-1",
                    name = "Reviewer",
                    prompt = "Review the answer before replying.",
                ),
            ),
        )
        eventHub.publishUiIntent(UiIntent.InputChanged("summarize"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { provider.requests.isNotEmpty() }
        val request = provider.requests.single()
        assertEquals("summarize", request.prompt)
        assertEquals(listOf("Review the answer before replying."), request.systemInstructions)

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `submitting with only selected agents does not create a blank user message`() {
        val workingDir = createTempDirectory("coordinator-agent-only-submit")

        val provider = RecordingProvider()
        val settings = AgentSettingsService().apply {
            loadState(
                AgentSettingsService.State(
                    savedAgents = mutableListOf(
                        SavedAgentDefinition(
                            id = "agent-1",
                            name = "Reviewer",
                            prompt = "Review the answer before replying.",
                        ),
                    ),
                ),
            )
        }
        val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(provider),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val timelineStore = TimelineAreaStore()
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = timelineStore,
            composerStore = ComposerAreaStore(),
            rightDrawerStore = RightDrawerAreaStore(),
        )

        eventHub.publishUiIntent(
            UiIntent.SelectAgent(
                SavedAgentDefinition(
                    id = "agent-1",
                    name = "Reviewer",
                    prompt = "Review the answer before replying.",
                ),
            ),
        )
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { provider.requests.isNotEmpty() && !timelineStore.state.value.isRunning }
        val request = provider.requests.single()
        assertEquals("", request.prompt)
        assertEquals(listOf("Review the answer before replying."), request.systemInstructions)
        assertTrue(
            timelineStore.state.value.nodes.none {
                it is TimelineNode.MessageNode && it.role == MessageRole.USER && it.text.isBlank()
            },
        )

        coordinator.dispose()
        service.dispose()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition was not met within ${timeoutMs}ms")
            }
            Thread.sleep(20)
        }
    }

    private fun registry(provider: AgentProvider): ProviderRegistry {
        return ProviderRegistry(
            descriptors = listOf(
                EngineDescriptor(
                    id = "codex",
                    displayName = "Codex",
                    models = listOf("gpt-5.3-codex"),
                    capabilities = EngineCapabilities(
                        supportsThinking = true,
                        supportsToolEvents = true,
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

    private class RecordingProvider : AgentProvider {
        val requests = mutableListOf<AgentRequest>()

        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = flow {
            requests += request
            emit(UnifiedEvent.TurnCompleted(turnId = "turn-1", outcome = TurnOutcome.SUCCESS))
        }

        override fun cancel(requestId: String) = Unit
    }
}
