package com.auracode.assistant.provider.claude

import com.auracode.assistant.model.AgentApprovalMode
import com.auracode.assistant.model.AgentCollaborationMode
import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.provider.codex.CodexEnvironmentStatus
import com.auracode.assistant.provider.runtime.DefaultRuntimeLaunchResolver
import com.auracode.assistant.provider.runtime.RuntimeLaunchResolution
import com.auracode.assistant.provider.runtime.RuntimeLaunchResolver
import com.auracode.assistant.settings.AgentSettingsService
import java.io.File

internal interface ClaudeCliLauncher {
    fun start(
        request: AgentRequest,
        settings: AgentSettingsService,
    ): ClaudeStreamJsonSession
}

internal class DefaultClaudeCliLauncher(
    private val runtimeLaunchResolver: RuntimeLaunchResolver = DefaultRuntimeLaunchResolver(),
    private val processStarter: ClaudeProcessStarter = DefaultClaudeProcessStarter(),
) : ClaudeCliLauncher {
    override fun start(
        request: AgentRequest,
        settings: AgentSettingsService,
    ): ClaudeStreamJsonSession {
        val resolution = resolveLaunch(settings)
        require(resolution.cliStatus != CodexEnvironmentStatus.MISSING) {
            "Claude CLI was not found. Install the CLI first or configure a valid executable path in Settings."
        }
        require(resolution.cliStatus != CodexEnvironmentStatus.FAILED) {
            "Configured Claude CLI path is not executable. Update Settings and try again."
        }
        if (settings.nodeExecutablePath().isNotBlank()) {
            require(resolution.nodeStatus != CodexEnvironmentStatus.FAILED) {
                "Configured Node Path is not executable. Update Settings and try again."
            }
        }
        val process = processStarter.start(
            command = buildCommand(request, resolution.cliPath),
            workingDirectory = File(request.workingDirectory),
            environmentOverrides = resolution.environmentOverrides,
        )
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).apply {
                write(ClaudeUserInputEncoder.encode(request))
                newLine()
                flush()
            }
        } catch (error: Exception) {
            process.destroyForcibly()
            throw error
        }
        return ProcessClaudeStreamJsonSession(process)
    }

    internal fun buildCommand(
        request: AgentRequest,
        executable: String,
    ): List<String> {
        return buildList {
            add(executable)
            add("-p")
            add("--verbose")
            add("--output-format")
            add("stream-json")
            add("--include-partial-messages")
            add("--permission-mode")
            add(resolvePermissionMode(request))
            add("--permission-prompt-tool")
            add("stdio")
            add("--replay-user-messages")
            request.model?.trim()?.takeIf { it.isNotBlank() }?.let { model ->
                add("--model")
                add(model)
            }
            request.reasoningEffort?.trim()?.takeIf { it.isNotBlank() }?.let { effort ->
                add("--effort")
                add(mapReasoningEffort(effort))
            }
            request.remoteConversationId?.trim()?.takeIf { it.isNotBlank() }?.let { sessionId ->
                add("--resume")
                add(sessionId)
            }
            buildAppendSystemPrompt(request)?.let { systemPrompt ->
                add("--append-system-prompt")
                add(systemPrompt)
            }
            add("--input-format")
            add("stream-json")
        }
    }

    private fun mapReasoningEffort(effort: String): String = when (effort) {
        "xhigh" -> "high"
        else -> effort
    }

    /**
     * 合并全局角色与已选智能体片段为单次 `--append-system-prompt` 参数。
     *
     * Claude CLI 的该参数是单值选项，重复传入只有最后一次生效，
     * 因此必须先合并再下发，否则多个智能体只有最后一个能进入系统提示。
     * 全局角色排在前面，保证它先于智能体片段被模型读取。
     */
    private fun buildAppendSystemPrompt(request: AgentRequest): String? {
        val sections = buildList {
            request.systemPrompt?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            request.systemInstructions
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(::add)
        }
        return sections.takeIf(List<String>::isNotEmpty)?.joinToString("\n\n")
    }

    private fun resolvePermissionMode(request: AgentRequest): String {
        if (request.collaborationMode == AgentCollaborationMode.PLAN) {
            return "plan"
        }
        return when (request.approvalMode) {
            AgentApprovalMode.AUTO -> "auto"
            AgentApprovalMode.REQUIRE_CONFIRMATION -> "default"
        }
    }

    internal fun buildPromptText(request: AgentRequest): String = ClaudeUserInputEncoder.buildPromptText(request)

    private fun resolveLaunch(settings: AgentSettingsService): RuntimeLaunchResolution {
        return runtimeLaunchResolver.resolve(
            commandName = ClaudeProviderFactory.ENGINE_ID,
            configuredCliPath = settings.state.executablePathFor(ClaudeProviderFactory.ENGINE_ID),
            configuredNodePath = settings.nodeExecutablePath(),
        )
    }
}
