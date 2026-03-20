package com.codex.assistant.toolwindow.eventing

import com.codex.assistant.model.AgentApprovalMode
import com.codex.assistant.model.AgentRequest
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnOutcome
import com.codex.assistant.protocol.UnifiedApprovalRequest
import com.codex.assistant.protocol.UnifiedApprovalRequestKind
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.provider.AgentProvider
import com.codex.assistant.provider.AgentProviderFactory
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.settings.AgentSettingsService
import com.codex.assistant.toolwindow.approval.ApprovalAction
import com.codex.assistant.toolwindow.approval.ApprovalAreaStore
import com.codex.assistant.toolwindow.drawer.RightDrawerAreaStore
import com.codex.assistant.toolwindow.header.HeaderAreaStore
import com.codex.assistant.toolwindow.status.StatusAreaStore
import com.codex.assistant.toolwindow.timeline.TimelineAreaStore
import com.codex.assistant.toolwindow.timeline.TimelineNode
import com.codex.assistant.persistence.chat.SQLiteChatSessionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolWindowApprovalFlowTest {
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
                        supportsCommandProposal = true,
                        supportsDiffProposal = true,
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

    @Test
    fun `approval mode is passed to provider from composer mode`() {
        val harness = CoordinatorHarness()

        harness.eventHub.publishUiIntent(UiIntent.SelectMode(ComposerMode.APPROVAL))
        harness.eventHub.publishUiIntent(UiIntent.InputChanged("run"))
        harness.eventHub.publishUiIntent(UiIntent.SendPrompt)

        harness.waitUntil { harness.provider.requests.isNotEmpty() }
        assertEquals(AgentApprovalMode.REQUIRE_CONFIRMATION, harness.provider.requests.single().approvalMode)

        harness.dispose()
    }

    @Test
    fun `approval decision is routed to provider and timeline node is updated`() {
        val harness = CoordinatorHarness()

        harness.eventHub.publishUiIntent(UiIntent.InputChanged("run"))
        harness.eventHub.publishUiIntent(UiIntent.SendPrompt)
        harness.waitUntil { harness.provider.requests.isNotEmpty() }

        harness.provider.emitApproval(
            UnifiedApprovalRequest(
                requestId = "approval-1",
                turnId = "turn-1",
                itemId = "item-1",
                kind = UnifiedApprovalRequestKind.COMMAND,
                title = "Run command",
                body = "./gradlew test",
                command = "./gradlew test",
                cwd = ".",
            ),
        )

        harness.waitUntil { harness.approvalStore.state.value.current?.requestId == "approval-1" }
        harness.eventHub.publishUiIntent(UiIntent.SubmitApprovalAction(ApprovalAction.ALLOW_FOR_SESSION))

        harness.waitUntil { harness.provider.decisions.isNotEmpty() }
        assertEquals("approval-1" to ApprovalAction.ALLOW_FOR_SESSION, harness.provider.decisions.single())
        val approvalNode = harness.timelineStore.state.value.nodes.filterIsInstance<TimelineNode.ApprovalNode>().single()
        assertEquals(ItemStatus.SUCCESS, approvalNode.status)
        assertTrue(approvalNode.body.contains("Remembered for session"))

        harness.dispose()
    }

    private class CoordinatorHarness {
        private val workingDir = createTempDirectory("approval-flow")
        val provider = RecordingApprovalProvider()
        private val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        private val service = AgentChatService(
            repository = SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = ProviderRegistry(
                descriptors = listOf(
                    EngineDescriptor(
                        id = "codex",
                        displayName = "Codex",
                        models = listOf("gpt-5.3-codex"),
                        capabilities = EngineCapabilities(
                            supportsThinking = true,
                            supportsToolEvents = true,
                            supportsCommandProposal = true,
                            supportsDiffProposal = true,
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
            ),
            settings = settings,
            workingDirectoryProvider = { workingDir.toString() },
        )
        val eventHub = ToolWindowEventHub()
        val timelineStore = TimelineAreaStore()
        val approvalStore = ApprovalAreaStore()
        private val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = StatusAreaStore(),
            timelineStore = timelineStore,
            composerStore = com.codex.assistant.toolwindow.composer.ComposerAreaStore(),
            rightDrawerStore = RightDrawerAreaStore(),
            approvalStore = approvalStore,
        )

        fun waitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
            val start = System.currentTimeMillis()
            while (!condition()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    throw AssertionError("Condition was not met within ${timeoutMs}ms")
                }
                Thread.sleep(20)
            }
        }

        fun dispose() {
            coordinator.dispose()
            service.dispose()
            Files.deleteIfExists(workingDir.resolve("chat.db"))
        }
    }

    private class RecordingApprovalProvider : AgentProvider {
        val requests = mutableListOf<AgentRequest>()
        val decisions = mutableListOf<Pair<String, ApprovalAction>>()
        private var sink: (UnifiedEvent) -> Unit = {}

        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = callbackFlow {
            requests += request
            sink = { event -> trySend(event); Unit }
            trySend(UnifiedEvent.TurnStarted("turn-1", "thread-1"))
            awaitClose { sink = {} }
        }

        fun emitApproval(request: UnifiedApprovalRequest) {
            sink(UnifiedEvent.ApprovalRequested(request))
        }

        override fun submitApprovalDecision(requestId: String, decision: ApprovalAction): Boolean {
            decisions += requestId to decision
            sink(UnifiedEvent.TurnCompleted("turn-1", TurnOutcome.SUCCESS))
            return true
        }

        override fun cancel(requestId: String) = Unit
    }
}
