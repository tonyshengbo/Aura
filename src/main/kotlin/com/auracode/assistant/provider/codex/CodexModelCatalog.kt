package com.auracode.assistant.provider.codex

/** 描述一个可在界面中展示的 Codex 模型选项。 */
data class CodexModelOption(
    val id: String,
    val description: String,
)

/** 维护 Codex 内置模型列表和默认模型选择。 */
object CodexModelCatalog {
    /** 默认使用适合编码和日常任务的最新 SOL 模型。 */
    const val defaultModel: String = "gpt-6.2-sol"

    /** 优先展示最新版本，并保留视觉操作与复杂推理的模型选项。 */
    val options: List<CodexModelOption> = listOf(
        CodexModelOption(
            id = "gpt-6.2-sol",
            description = "GPT-6.2-SOL",
        ),
        CodexModelOption(
            id = "gpt-6.2-astra",
            description = "GPT-6.2-ASTRA",
        ),
        CodexModelOption(
            id = "gpt-6.1-terra",
            description = "GPT-6.1-TERRA",
        ),
        CodexModelOption(
            id = "gpt-6.1-sol",
            description = "GPT-6.1-SOL",
        ),
        CodexModelOption(
            id = "gpt-6-astra",
            description = "GPT-6-ASTRA",
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
