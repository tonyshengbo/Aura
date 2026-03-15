package com.codex.assistant.toolwindow

import com.codex.assistant.protocol.ConversationUiState
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.protocol.UnifiedItem
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.service.ProjectSessionStore
import com.codex.assistant.provider.AgentProvider
import com.codex.assistant.provider.AgentProviderFactory
import com.codex.assistant.provider.EngineCapabilities
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.settings.AgentSettingsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolWindowStoreTest {
    @Test
    fun `tracks edited files from diff events within current session`() {
        val store = ToolWindowStore(createService())

        store.onUnifiedEvent(UnifiedEvent.ThreadStarted(threadId = "th_1"))
        store.onUnifiedEvent(UnifiedEvent.TurnStarted(turnId = "tu_1", threadId = "th_1"))
        store.onUnifiedEvent(
            UnifiedEvent.ItemUpdated(
                UnifiedItem(
                    id = "it_1",
                    kind = ItemKind.DIFF_APPLY,
                    status = ItemStatus.SUCCESS,
                    filePath = "src/Main.kt",
                ),
            ),
        )

        assertEquals(1, store.state.value.editedFiles.size)
        assertEquals("src/Main.kt", store.state.value.editedFiles.first().path)
    }

    @Test
    fun `non narrative nodes are collapsed by default`() {
        val store = ToolWindowStore(createService())
        store.onUnifiedEvent(UnifiedEvent.ThreadStarted(threadId = "th"))
        store.onUnifiedEvent(UnifiedEvent.TurnStarted(turnId = "turn", threadId = "th"))
        store.onUnifiedEvent(
            UnifiedEvent.ItemUpdated(
                UnifiedItem(
                    id = "cmd_1",
                    kind = ItemKind.COMMAND_EXEC,
                    status = ItemStatus.RUNNING,
                    command = "ls",
                ),
            ),
        )

        assertFalse(store.state.value.expandedNodeIds.contains("cmd_1"))
        store.dispatch(ToolWindowIntent.ToggleNodeExpanded("cmd_1"))
        assertTrue(store.state.value.expandedNodeIds.contains("cmd_1"))
    }

    @Test
    fun `resets conversation state when creating new session`() {
        val store = ToolWindowStore(createService())
        store.onUnifiedEvent(UnifiedEvent.ThreadStarted(threadId = "th_old"))
        store.onUnifiedEvent(UnifiedEvent.TurnStarted(turnId = "turn_old", threadId = "th_old"))

        store.dispatch(ToolWindowIntent.NewSession)

        assertEquals(ConversationUiState(), store.state.value.conversation)
        assertEquals(0, store.state.value.editedFiles.size)
    }

    @Test
    fun `send prompt does not create local turn before backend events`() {
        val store = ToolWindowStore(createService())
        store.dispatch(ToolWindowIntent.InputChanged("hello"))

        val effect = store.dispatch(ToolWindowIntent.SendPrompt)

        assertNotNull(effect)
        assertTrue(store.state.value.conversation.turns.isEmpty())
        assertFalse(store.state.value.conversation.isRunning)

        store.onUnifiedEvent(UnifiedEvent.ThreadStarted(threadId = "th_1"))
        store.onUnifiedEvent(UnifiedEvent.TurnStarted(turnId = "tu_1", threadId = "th_1"))

        assertTrue(store.state.value.conversation.turns.isNotEmpty())
        assertTrue(store.state.value.conversation.isRunning)
    }

    @Test
    fun `ignores send prompt while running`() {
        val store = ToolWindowStore(createService())
        store.onUnifiedEvent(UnifiedEvent.ThreadStarted(threadId = "th_1"))
        store.onUnifiedEvent(UnifiedEvent.TurnStarted(turnId = "tu_1", threadId = "th_1"))
        store.dispatch(ToolWindowIntent.InputChanged("hello while running"))

        val effect = store.dispatch(ToolWindowIntent.SendPrompt)

        assertNull(effect)
        assertEquals("hello while running", store.state.value.inputText)
    }

    private fun createService(): AgentChatService {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val provider = object : AgentProvider {
            override fun stream(request: com.codex.assistant.model.AgentRequest): Flow<com.codex.assistant.model.EngineEvent> = emptyFlow()
            override fun cancel(requestId: String) = Unit
        }
        val registry = ProviderRegistry(
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
        return AgentChatService(
            sessionStore = ProjectSessionStore(),
            registry = registry,
            settings = settings,
        )
    }
}
