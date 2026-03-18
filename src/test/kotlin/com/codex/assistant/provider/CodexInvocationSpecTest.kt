package com.codex.assistant.provider

import com.codex.assistant.model.AgentRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexInvocationSpecTest {
    private val spec = CodexInvocationSpec()

    @Test
    fun `builds first turn command with exec`() {
        val command = spec.buildCommand(
            executablePath = "codex",
            request = AgentRequest(
                engineId = "codex",
                model = "gpt-5.3-codex",
                prompt = "Hello",
                contextFiles = emptyList(),
                workingDirectory = ".",
                cliSessionId = null,
            ),
        )

        assertEquals(
            listOf(
                "codex",
                "exec",
                "-m",
                "gpt-5.3-codex",
                "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox",
                "--json",
            ),
            command,
        )
    }

    @Test
    fun `builds follow-up command with exec resume and model`() {
        val command = spec.buildCommand(
            executablePath = "codex",
            request = AgentRequest(
                engineId = "codex",
                model = "gpt-5.4",
                prompt = "Follow up",
                contextFiles = emptyList(),
                workingDirectory = ".",
                cliSessionId = "thread_1",
            ),
        )

        assertEquals(
            listOf(
                "codex",
                "exec",
                "resume",
                "-m",
                "gpt-5.4",
                "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox",
                "--json",
                "thread_1",
            ),
            command,
        )
    }

    @Test
    fun `adds reasoning effort when configured`() {
        val command = spec.buildCommand(
            executablePath = "codex",
            request = AgentRequest(
                engineId = "codex",
                model = "gpt-5.4",
                reasoningEffort = "xhigh",
                prompt = "Deeper reasoning",
                contextFiles = emptyList(),
                workingDirectory = ".",
            ),
        )

        assertTrue(command.contains("-c"))
        assertTrue(command.contains("model_reasoning_effort=\"xhigh\""))
    }
}
