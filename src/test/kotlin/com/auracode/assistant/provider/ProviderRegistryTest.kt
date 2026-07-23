package com.auracode.assistant.provider

import com.auracode.assistant.protocol.ProviderEvent
import com.auracode.assistant.settings.AgentSettingsService
import com.auracode.assistant.provider.claude.ClaudeModelCatalog
import com.auracode.assistant.provider.codex.CodexModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderRegistryTest {
    @Test
    fun `default registry exposes codex and claude engines`() {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }

        val registry = ProviderRegistry(settings)

        assertEquals(listOf("claude", "codex"), registry.engines().map { it.id }.sorted())
        assertEquals("Claude", registry.engine("claude")?.displayName)
    }

    @Test
    fun `default codex registry exposes curated model list without auto`() {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }

        val registry = ProviderRegistry(settings)

        assertEquals(
            listOf(
                "gpt-5.6-sol",
                "gpt-5.6-terra",
                "gpt-5.6-luna",
                "gpt-5.5",
                "gpt-5.4",
            ),
            registry.engine("codex")?.models,
        )
    }

    @Test
    fun `default claude registry exposes current curated model list`() {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }

        val registry = ProviderRegistry(settings)

        assertEquals(
            listOf(
                "claude-opus-4-8",
                "claude-opus-4-7",
                "claude-sonnet-4-6",
                "claude-fable-5",
                "claude-haiku-4-5-20251001",
                "claude-opus-4-6",
            ),
            registry.engine("claude")?.models,
        )
    }

    @Test
    fun `settings persist selected model per engine`() {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }

        settings.setSelectedSubmissionModel(engineId = "claude", model = "claude-sonnet-4-5")
        settings.setSelectedSubmissionModel(engineId = "codex", model = "gpt-5.4")

        assertEquals("claude-sonnet-4-6", settings.selectedSubmissionModel("claude"))
        assertEquals("gpt-5.4", settings.selectedSubmissionModel("codex"))
    }

    @Test
    fun `settings migrate legacy curated claude model ids to current replacements`() {
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }

        settings.setSelectedSubmissionModel(engineId = "claude", model = "claude-opus-4-1")
        assertEquals("claude-opus-4-7", settings.selectedSubmissionModel("claude"))

        settings.setSelectedSubmissionModel(engineId = "claude", model = "claude-haiku-4-5")
        assertEquals("claude-haiku-4-5-20251001", settings.selectedSubmissionModel("claude"))
    }

    @Test
    fun `curated model catalogs expose display friendly short names`() {
        assertEquals("GPT-5.6-SOL", CodexModelCatalog.option("gpt-5.6-sol")?.description)
        assertEquals("GPT-5.6-TERRA", CodexModelCatalog.option("gpt-5.6-terra")?.description)
        assertEquals("GPT-5.6-LUNA", CodexModelCatalog.option("gpt-5.6-luna")?.description)
        assertEquals("gpt-5.4", CodexModelCatalog.option("gpt-5.4")?.description)
        assertEquals("Opus 4.8 [1m]", ClaudeModelCatalog.option("claude-opus-4-8")?.shortName)
        assertEquals("Sonnet 4.6 [1m]", ClaudeModelCatalog.option("claude-sonnet-4-6")?.shortName)
        assertEquals("Fable 5 [1m]", ClaudeModelCatalog.option("claude-fable-5")?.shortName)
        assertEquals("Haiku 4.5 [200k]", ClaudeModelCatalog.option("claude-haiku-4-5-20251001")?.shortName)
    }

    @Test
    fun `claude model catalog exposes expected context windows`() {
        assertEquals(1_000_000, ClaudeModelCatalog.contextWindow("claude-opus-4-8"))
        assertEquals(1_000_000, ClaudeModelCatalog.contextWindow("claude-fable-5"))
        assertEquals(200_000, ClaudeModelCatalog.contextWindow("claude-haiku-4-5-20251001"))
    }

    @Test
    fun `registry exposes codex as default engine`() {
        val registry = ProviderRegistry(
            descriptors = listOf(
                EngineDescriptor(
                    id = "codex",
                    displayName = "Codex",
                    models = listOf("gpt-5.3-codex", "gpt-5.4"),
                    capabilities = EngineCapabilities(
                        supportsThinking = true,
                        supportsToolEvents = true,
                        supportsCommandProposal = true,
                        supportsDiffProposal = true,
                    ),
                ),
            ),
            factories = emptyList(),
            defaultEngineId = "codex",
        )

        assertEquals("codex", registry.defaultEngineId())
        assertEquals(listOf("codex"), registry.engines().map { it.id })
        assertEquals("Codex", registry.engine("codex")?.displayName)
    }

    @Test
    fun `registry falls back to default engine when unknown id is requested`() {
        val codex = EngineDescriptor(
            id = "codex",
            displayName = "Codex",
            models = listOf("gpt-5.3-codex"),
            capabilities = EngineCapabilities(
                supportsThinking = true,
                supportsToolEvents = true,
                supportsCommandProposal = true,
                supportsDiffProposal = true,
            ),
        )

        val registry = ProviderRegistry(
            descriptors = listOf(codex),
            factories = listOf(
                object : AgentProviderFactory {
                    override val engineId: String = "codex"
                    override fun create(): AgentProvider = object : AgentProvider {
                        override fun stream(request: com.auracode.assistant.model.AgentRequest) =
                            com.auracode.assistant.test.emptySessionDomainEventFlow()

                        override fun cancel(requestId: String) = Unit
                    }
                },
            ),
            defaultEngineId = "codex",
        )

        val provider = registry.providerOrDefault("missing")
        assertNotNull(provider)
        assertTrue(registry.providerOrNull("codex") != null)
    }
}
