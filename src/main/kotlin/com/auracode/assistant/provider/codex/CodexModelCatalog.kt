package com.auracode.assistant.provider.codex

/** 描述一个可在界面中展示的 Codex 模型选项。 */
data class CodexModelOption(
    val id: String,
    val description: String,
)

/** 维护 Codex 内置模型列表和默认模型选择。 */
object CodexModelCatalog {
    const val defaultModel: String = "gpt-5.6-sol"

    val options: List<CodexModelOption> = listOf(
        CodexModelOption(
            id = "gpt-5.6-sol",
            description = "GPT-5.6-SOL",
        ),
        CodexModelOption(
            id = "gpt-5.6-terra",
            description = "GPT-5.6-TERRA",
        ),
        CodexModelOption(
            id = "gpt-5.6-luna",
            description = "GPT-5.6-LUNA",
        ),
        CodexModelOption(
            id = "gpt-5.5",
            description = "GPT-5.5",
        ),
        CodexModelOption(
            id = "gpt-5.4",
            description = "gpt-5.4",
        ),
    )

    /** 返回当前 Codex 引擎内置模型的稳定标识列表。 */
    fun ids(): List<String> = options.map { it.id }

    /** 通过模型标识解析对应的展示选项。 */
    fun option(modelId: String?): CodexModelOption? {
        val normalized = modelId?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return options.firstOrNull { it.id == normalized }
    }
}
