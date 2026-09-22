package com.auracode.assistant.settings.prompt

/**
 * 全局角色设定，最终以系统提示词的形式下发给底层引擎。
 *
 * 与「智能体」的差异：
 * - 智能体提示词是可选片段，Claude 侧作为追加系统提示下发，Codex 侧拼接进 prompt 正文；
 * - 本设定是全局的，走引擎原生的系统提示词通道
 *   （Claude 的 `--append-system-prompt`、Codex app-server 的 `developerInstructions`），
 *   不参与 prompt 正文拼接。
 */
data class SystemPromptProfile(
    var enabled: Boolean = false,
    var content: String = "",
) {
    /**
     * 归一化后的角色文本。
     *
     * 未启用或内容为空时返回 null，调用方据此跳过系统提示词注入，
     * 避免把空白内容下发给引擎。
     */
    fun resolvedContent(): String? {
        if (!enabled) {
            return null
        }
        return content.trim().takeIf(String::isNotBlank)
    }
}
