package com.codex.assistant.provider.impl

import com.codex.assistant.model.AgentRequest
import com.codex.assistant.model.EngineEvent
import com.codex.assistant.provider.AgentProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader

class ClaudeCliProvider : AgentProvider {

    override fun stream(request: AgentRequest): Flow<EngineEvent> = flow {
        val command = buildCommand(request)
        val process = ProcessBuilder(command)
            .directory(java.io.File(request.workingDirectory))
            .start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                emit(EngineEvent.AssistantTextDelta(line + "\n"))
            }
        }

        val exitCode = process.waitFor()
        emit(EngineEvent.Completed(exitCode))
    }

    override fun cancel(requestId: String) {
        // No-op for now
    }

    private fun buildCommand(request: AgentRequest): List<String> {
        val cmd = mutableListOf("claude", "chat")

        request.contextFiles.forEach { file ->
            cmd.add("--file")
            cmd.add(file.path)
        }

        cmd.add(request.prompt)
        return cmd
    }
}
