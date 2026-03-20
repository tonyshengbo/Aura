package com.auracode.assistant.toolwindow.eventing

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.protocol.TurnOutcome
import com.auracode.assistant.protocol.UnifiedEvent
import com.auracode.assistant.provider.AgentProvider
import com.auracode.assistant.provider.AgentProviderFactory
import com.auracode.assistant.provider.EngineCapabilities
import com.auracode.assistant.provider.EngineDescriptor
import com.auracode.assistant.provider.ProviderRegistry
import com.auracode.assistant.service.AgentChatService
import com.auracode.assistant.settings.AgentSettingsService
import com.auracode.assistant.settings.skills.LocalSkillCatalog
import com.auracode.assistant.toolwindow.approval.ApprovalAreaStore
import com.auracode.assistant.toolwindow.composer.ComposerAreaStore
import com.auracode.assistant.toolwindow.drawer.RightDrawerAreaStore
import com.auracode.assistant.toolwindow.header.HeaderAreaStore
import com.auracode.assistant.toolwindow.status.StatusAreaStore
import com.auracode.assistant.toolwindow.timeline.TimelineAreaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolWindowCoordinatorSkillTest {
    @Test
    fun `submitting prompt with disabled skill token is blocked before agent run`() {
        val workingDir = createTempDirectory("coordinator-skill-submit")
        val home = createTempDirectory("skills-home")
        home.resolve(".codex/skills/brainstorming").createDirectories()
        home.resolve(".codex/skills/brainstorming/SKILL.md").writeText(
            """
            ---
            name: brainstorming
            description: "Explore requirements."
            ---
            """.trimIndent(),
        )
        val settings = AgentSettingsService().apply {
            loadState(
                AgentSettingsService.State(
                    disabledSkillNames = linkedSetOf("brainstorming"),
                ),
            )
        }
        val service = AgentChatService(
            repository = com.auracode.assistant.persistence.chat.SQLiteChatSessionRepository(workingDir.resolve("chat.db")),
            registry = registry(),
            settings = settings,
        )
        val eventHub = ToolWindowEventHub()
        val statusStore = StatusAreaStore()
        val composerStore = ComposerAreaStore(
            availableSkillsProvider = {
                LocalSkillCatalog(settings = settings, homeDir = home).listEnabledSkills()
            },
        )
        val coordinator = ToolWindowCoordinator(
            chatService = service,
            settingsService = settings,
            eventHub = eventHub,
            headerStore = HeaderAreaStore(),
            statusStore = statusStore,
            timelineStore = TimelineAreaStore(),
            composerStore = composerStore,
            rightDrawerStore = RightDrawerAreaStore(),
            approvalStore = ApprovalAreaStore(),
            skillCatalog = LocalSkillCatalog(settings = settings, homeDir = home),
        )

        composerStore.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.UpdateDocument(TextFieldValue("\$brainstorming write tests", TextRange(27))),
            ),
        )

        eventHub.publishUiIntent(UiIntent.SendPrompt)

        waitUntil(2_000) { statusStore.state.value.toast != null }

        assertEquals(
            "Disabled skills cannot be used: brainstorming",
            statusStore.state.value.toast?.text?.let { (it as com.auracode.assistant.toolwindow.shared.UiText.Raw).value },
        )
        assertEquals(null, statusStore.state.value.turnStatus)

        coordinator.dispose()
        service.dispose()
    }

    @Test
    fun `composer slash suggestions only include enabled skills from catalog`() {
        val home = createTempDirectory("skills-composer-home")
        createSkill(home, ".codex/skills/brainstorming", "brainstorming", "Explore requirements.")
        createSkill(home, ".codex/skills/systematic-debugging", "systematic-debugging", "Debug step by step.")
        val settings = AgentSettingsService().apply {
            loadState(AgentSettingsService.State(disabledSkillNames = linkedSetOf("brainstorming")))
        }
        val store = ComposerAreaStore(
            availableSkillsProvider = {
                LocalSkillCatalog(settings = settings, homeDir = home).listEnabledSkills()
            },
        )

        store.onEvent(AppEvent.UiIntentPublished(UiIntent.UpdateDocument(TextFieldValue("/", TextRange(1)))))

        val skillNames = store.state.value.slashSuggestions
            .mapNotNull { (it as? com.auracode.assistant.toolwindow.composer.SlashSuggestionItem.Skill)?.name }

        assertFalse(skillNames.contains("brainstorming"))
        assertTrue(skillNames.contains("systematic-debugging"))
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

    private fun registry(): ProviderRegistry {
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
                    override fun create(): AgentProvider = object : AgentProvider {
                        override fun stream(request: AgentRequest): Flow<UnifiedEvent> = flow {
                            emit(UnifiedEvent.TurnCompleted(turnId = "turn-1", outcome = TurnOutcome.SUCCESS))
                        }

                        override fun cancel(requestId: String) = Unit
                    }
                },
            ),
            defaultEngineId = "codex",
        )
    }

    private fun createSkill(root: java.nio.file.Path, relativeDir: String, name: String, description: String) {
        val dir = root.resolve(relativeDir)
        dir.createDirectories()
        dir.resolve("SKILL.md").writeText(
            """
            ---
            name: $name
            description: "$description"
            ---
            """.trimIndent(),
        )
    }
}
