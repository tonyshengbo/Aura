package com.auracode.assistant.provider.codex

import com.auracode.assistant.settings.AgentSettingsService
import kotlin.test.Test
import kotlin.test.assertEquals

/** 覆盖模型目录升级后的缓存回退和有效缓存读取行为。 */
class CodexModelCatalogServiceTest {
    @Test
    fun `missing cache falls back to built in models`() {
        val service = createService()

        assertEquals(CodexModelCatalog.ids(), service.currentModels().map { it.slug })
    }

    @Test
    fun `obsolete cache falls back to current built in models`() {
        val service = createService(
            cachedIds = listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5", "gpt-5.4"),
            cachedNames = listOf("GPT-5.6-SOL", "GPT-5.6-TERRA", "GPT-5.6-LUNA", "GPT-5.5", "GPT-5.4"),
        )

        assertEquals(
            CodexModelCatalog.options.map { CodexModelEntry(slug = it.id, displayName = it.description) },
            service.currentModels(),
        )
    }

    @Test
    fun `valid cached models preserve names and catalog order while excluding obsolete entries`() {
        val service = createService(
            cachedIds = listOf("gpt-6.1-sol", "gpt-5.6-sol", "gpt-6.2-sol"),
            cachedNames = listOf("Cached SOL 6.1", "Obsolete SOL", "Cached SOL 6.2"),
        )

        assertEquals(
            listOf(
                CodexModelEntry(slug = "gpt-6.2-sol", displayName = "Cached SOL 6.2"),
                CodexModelEntry(slug = "gpt-6.1-sol", displayName = "Cached SOL 6.1"),
            ),
            service.currentModels(),
        )
    }

    /** 读取缓存无需调用 CLI，测试仅提供内存中的持久化状态。 */
    private fun createService(
        cachedIds: List<String> = emptyList(),
        cachedNames: List<String> = emptyList(),
    ): CodexModelCatalogService {
        val settings = AgentSettingsService().apply {
            loadState(
                AgentSettingsService.State(
                    cachedCodexModelIds = cachedIds.toMutableList(),
                    cachedCodexModelDisplayNames = cachedNames.toMutableList(),
                ),
            )
        }
        return CodexModelCatalogService(
            settingsService = settings,
            environmentDetector = CodexEnvironmentDetector(),
            commandRunner = { _, _ -> error("Reading the model cache must not invoke the CLI") },
        )
    }
}
