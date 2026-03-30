package com.auracode.assistant.toolwindow.eventing

import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.provider.AgentProvider
import com.auracode.assistant.provider.AgentProviderFactory
import com.auracode.assistant.provider.EngineCapabilities
import com.auracode.assistant.provider.EngineDescriptor
import com.auracode.assistant.provider.ProviderRegistry
import com.auracode.assistant.service.AgentChatService
import com.auracode.assistant.model.MessageRole
import com.auracode.assistant.toolwindow.timeline.TimelineNode
import com.auracode.assistant.settings.SavedAgentDefinition
import com.auracode.assistant.settings.AgentSettingsService
import com.auracode.assistant.toolwindow.composer.ComposerAreaStore
import com.auracode.assistant.toolwindow.composer.FocusedContextSnapshot
import com.auracode.assistant.toolwindow.drawer.RightDrawerAreaStore
import com.auracode.assistant.toolwindow.header.HeaderAreaStore
import com.auracode.assistant.toolwindow.status.StatusAreaStore
import com.auracode.assistant.toolwindow.timeline.TimelineAreaStore
import com.auracode.assistant.persistence.chat.SQLiteChatSessionRepository
import com.auracode.assistant.protocol.TurnOutcome
import com.auracode.assistant.protocol.UnifiedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolWindowCoordinatorSubmitTest {
    @Test
    fun `submitting with a text attachment keeps composer context clean and sends path only context`() {
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
        assertNull(request.contextFiles.single().content)

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
    fun `submitting with focused selection sends only selected snippet instead of whole file`() {
        val workingDir = createTempDirectory("coordinator-selection-submit")
        val source = workingDir.resolve("Feature.kt")
        Files.writeString(
            source,
            """
            class Feature {
                fun wholeFile() = false
                fun selectedLines() = true
            }
            """.trimIndent(),
        )

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

        eventHub.publishUiIntent(
            UiIntent.UpdateFocusedContextFile(
                FocusedContextSnapshot(
                    path = source.toString(),
                    selectedText = "fun selectedLines() = true",
                    startLine = 2,
                    endLine = 2,
                ),
            ),
        )
        eventHub.publishUiIntent(UiIntent.InputChanged("summarize"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { provider.requests.isNotEmpty() }
        val request = provider.requests.single()
        assertEquals(listOf("${source}:2"), request.contextFiles.map { it.path })
        assertEquals("fun selectedLines() = true", request.contextFiles.single().content)

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `submitting with focused whole file sends path only context`() {
        val workingDir = createTempDirectory("coordinator-whole-file-submit")
        val source = workingDir.resolve("Feature.kt")
        Files.writeString(
            source,
            """
            class Feature {
                fun wholeFile() = true
            }
            """.trimIndent(),
        )

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

        eventHub.publishUiIntent(
            UiIntent.UpdateFocusedContextFile(
                FocusedContextSnapshot(path = source.toString()),
            ),
        )
        eventHub.publishUiIntent(UiIntent.InputChanged("summarize"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { provider.requests.isNotEmpty() }
        val request = provider.requests.single()
        assertEquals(listOf(source.toString()), request.contextFiles.map { it.path })
        assertNull(request.contextFiles.single().content)

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

    @Test
    fun `sending while running queues pending submission and dispatches it after completion`() {
        val workingDir = createTempDirectory("coordinator-pending-submit")
        val provider = BlockingProvider()
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(provider),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val composerStore = ComposerAreaStore()
        val timelineStore = TimelineAreaStore()
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = timelineStore,
            composerStore = composerStore,
            rightDrawerStore = RightDrawerAreaStore(),
        )

        eventHub.publishUiIntent(UiIntent.InputChanged("first"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)
        waitUntil(timeoutMs = 2_000) { provider.requests.size == 1 && timelineStore.state.value.isRunning }

        eventHub.publishUiIntent(UiIntent.InputChanged("second"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) { composerStore.state.value.pendingSubmissions.size == 1 }
        assertEquals("", composerStore.state.value.document.text)
        assertEquals(1, provider.requests.size)
        assertEquals("second", composerStore.state.value.pendingSubmissions.single().prompt)

        provider.completeNext()

        waitUntil(timeoutMs = 2_000) {
            provider.requests.size == 2 &&
                composerStore.state.value.pendingSubmissions.isEmpty() &&
                timelineStore.state.value.isRunning
        }
        assertEquals(listOf("first", "second"), provider.requests.map { it.prompt })

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `pending submission can be removed before dispatch`() {
        val workingDir = createTempDirectory("coordinator-pending-remove")
        val provider = BlockingProvider()
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(provider),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val composerStore = ComposerAreaStore()
        val timelineStore = TimelineAreaStore()
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = timelineStore,
            composerStore = composerStore,
            rightDrawerStore = RightDrawerAreaStore(),
        )

        eventHub.publishUiIntent(UiIntent.InputChanged("first"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)
        waitUntil(timeoutMs = 2_000) { provider.requests.size == 1 && timelineStore.state.value.isRunning }

        eventHub.publishUiIntent(UiIntent.InputChanged("second"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)
        waitUntil(timeoutMs = 2_000) { composerStore.state.value.pendingSubmissions.size == 1 }

        val queuedId = composerStore.state.value.pendingSubmissions.single().id
        eventHub.publishUiIntent(UiIntent.RemovePendingSubmission(queuedId))
        waitUntil(timeoutMs = 2_000) { composerStore.state.value.pendingSubmissions.isEmpty() }

        provider.completeNext()
        Thread.sleep(150)
        assertEquals(listOf("first"), provider.requests.map { it.prompt })
        assertTrue(composerStore.state.value.pendingSubmissions.isEmpty())

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `switching to another session while first run is active keeps both sessions sending independently`() {
        val workingDir = createTempDirectory("coordinator-multi-session-submit")
        val provider = BlockingProvider()
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

        val sessionA = service.getCurrentSessionId()
        eventHub.publishUiIntent(UiIntent.InputChanged("first"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) {
            provider.requests.size == 1 &&
                service.listSessions().firstOrNull { it.id == sessionA }?.isRunning == true &&
                composerStore.state.value.sessionIsRunning
        }

        val sessionB = service.createSession()
        eventHub.publishUiIntent(UiIntent.SwitchSession(sessionB))
        waitUntil(timeoutMs = 2_000) {
            coordinator.currentVisibleSessionId() == sessionB &&
                !composerStore.state.value.sessionIsRunning &&
                composerStore.state.value.document.text.isBlank()
        }

        eventHub.publishUiIntent(UiIntent.InputChanged("second"))
        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(timeoutMs = 2_000) {
            provider.requests.size == 2 && service.listSessions().count { it.isRunning } == 2
        }
        assertEquals(listOf("first", "second"), provider.requests.map { it.prompt })
        assertEquals(sessionB, coordinator.currentVisibleSessionId())

        provider.completeNext()
        waitUntil(timeoutMs = 2_000) { service.listSessions().count { it.isRunning } == 1 }
        provider.completeNext()
        waitUntil(timeoutMs = 2_000) { service.listSessions().none { it.isRunning } }

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `new session keeps composer configuration but starts with a clean draft`() {
        val workingDir = createTempDirectory("coordinator-session-init")
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

        val sessionA = service.getCurrentSessionId()
        eventHub.publishUiIntent(UiIntent.SelectModel("gpt-5.4"))
        eventHub.publishUiIntent(UiIntent.SelectReasoning(ComposerReasoning.HIGH))
        eventHub.publishUiIntent(
            UiIntent.SelectAgent(
                SavedAgentDefinition(
                    id = "agent-1",
                    name = "Reviewer",
                    prompt = "Review the answer before replying.",
                ),
            ),
        )
        eventHub.publishUiIntent(UiIntent.InputChanged("draft on session A"))

        waitUntil(timeoutMs = 2_000) {
            composerStore.state.value.selectedModel == "gpt-5.4" &&
                composerStore.state.value.selectedReasoning == ComposerReasoning.HIGH &&
                composerStore.state.value.agentEntries.map { it.id } == listOf("agent-1") &&
                composerStore.state.value.document.text == "draft on session A"
        }

        val sessionB = service.createSession()
        eventHub.publishUiIntent(UiIntent.SwitchSession(sessionB))
        waitUntil(timeoutMs = 2_000) {
            coordinator.currentVisibleSessionId() == sessionB &&
                composerStore.state.value.selectedModel == "gpt-5.4" &&
                composerStore.state.value.selectedReasoning == ComposerReasoning.HIGH &&
                composerStore.state.value.agentEntries.map { it.id } == listOf("agent-1") &&
                composerStore.state.value.document.text.isBlank() &&
                !composerStore.state.value.sessionIsRunning
        }

        eventHub.publishUiIntent(UiIntent.InputChanged("draft on session B"))
        waitUntil(timeoutMs = 2_000) { composerStore.state.value.document.text == "draft on session B" }

        eventHub.publishUiIntent(UiIntent.SwitchSession(sessionA))
        waitUntil(timeoutMs = 2_000) {
            coordinator.currentVisibleSessionId() == sessionA &&
                composerStore.state.value.document.text == "draft on session A"
        }

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `selecting model and reasoning persists to settings snapshot`() {
        val workingDir = createTempDirectory("coordinator-model-settings")
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

        eventHub.publishUiIntent(UiIntent.SelectModel("gpt-5.4"))
        eventHub.publishUiIntent(UiIntent.SelectReasoning(ComposerReasoning.HIGH))

        waitUntil(timeoutMs = 2_000) {
            settings.state.selectedComposerModel == "gpt-5.4" &&
                settings.state.selectedComposerReasoning == ComposerReasoning.HIGH.effort &&
                composerStore.state.value.selectedModel == "gpt-5.4" &&
                composerStore.state.value.selectedReasoning == ComposerReasoning.HIGH
        }

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `selecting and removing agents persists selected ids in settings`() {
        val workingDir = createTempDirectory("coordinator-agent-selection-settings")
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

        waitUntil(timeoutMs = 2_000) { settings.selectedAgentIds() == listOf("agent-1") }
        assertEquals(listOf("agent-1"), composerStore.state.value.agentEntries.map { it.id })

        eventHub.publishUiIntent(UiIntent.RemoveSelectedAgent("agent-1"))

        waitUntil(timeoutMs = 2_000) { settings.selectedAgentIds().isEmpty() }
        assertTrue(composerStore.state.value.agentEntries.isEmpty())

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `coordinator restores selected agents from persisted settings on startup`() {
        val workingDir = createTempDirectory("coordinator-agent-selection-restore")
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
                        SavedAgentDefinition(
                            id = "agent-2",
                            name = "Planner",
                            prompt = "Create the execution plan first.",
                        ),
                    ),
                    selectedAgentIds = linkedSetOf("agent-2", "agent-1"),
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

        waitUntil(timeoutMs = 2_000) { composerStore.state.value.agentEntries.size == 2 }
        assertEquals(listOf("agent-2", "agent-1"), composerStore.state.value.agentEntries.map { it.id })

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
            emit(UnifiedEvent.TurnStarted(turnId = "turn-${requests.size}", threadId = "thread-1"))
            emit(UnifiedEvent.TurnCompleted(turnId = "turn-${requests.size}", outcome = TurnOutcome.SUCCESS))
        }

        override fun cancel(requestId: String) = Unit
    }

    private class BlockingProvider : AgentProvider {
        val requests = mutableListOf<AgentRequest>()
        private val outcomes = LinkedBlockingQueue<TurnOutcome>()

        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = flow {
            requests += request
            emit(UnifiedEvent.TurnStarted(turnId = "turn-${requests.size}", threadId = "thread-1"))
            emit(UnifiedEvent.TurnCompleted(turnId = "turn-${requests.size}", outcome = outcomes.take()))
        }

        override fun cancel(requestId: String) = Unit

        fun completeNext(outcome: TurnOutcome = TurnOutcome.SUCCESS) {
            outcomes.put(outcome)
        }
    }
}
