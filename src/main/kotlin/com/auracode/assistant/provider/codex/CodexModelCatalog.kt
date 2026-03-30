package com.auracode.assistant.provider.codex

/** Describes one selectable Codex model option exposed in settings and composer UI. */
data class CodexModelOption(
    val id: String,
    val description: String,
)

/** Holds the built-in Codex model list and default model selection. */
object CodexModelCatalog {
    const val defaultModel: String = "gpt-5.4"

    val options: List<CodexModelOption> = listOf(
        CodexModelOption(
            id = "gpt-5.3-codex",
            description = "",
        ),
        CodexModelOption(
            id = "gpt-5.4",
            description = "",
        ),
        CodexModelOption(
            id = "gpt-5.2-codex",
            description = "",
        ),
        CodexModelOption(
            id = "gpt-5.1-codex-max",
            description = "",
        ),
        CodexModelOption(
            id = "gpt-5.1-codex-mini",
            description = "",
        ),
    )

    fun ids(): List<String> = options.map { it.id }

    fun option(modelId: String?): CodexModelOption? {
        val normalized = modelId?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return options.firstOrNull { it.id == normalized }
    }
}
