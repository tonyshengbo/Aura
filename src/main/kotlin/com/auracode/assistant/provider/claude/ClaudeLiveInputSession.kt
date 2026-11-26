package com.auracode.assistant.provider.claude

import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.provider.input.LiveInput
import com.auracode.assistant.provider.input.LiveInputResult
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Tracks native stdin consumption, without retaining or scheduling message contents in Aura. */
internal class ClaudeLiveInputSession(
    val session: ClaudeStreamJsonSession,
    private val request: AgentRequest,
) {
    private val mutex = Mutex()
    private var accepting = true
    private val unconsumedMessageIds = mutableSetOf<String>()

    suspend fun hasUnconsumedInputs(): Boolean = mutex.withLock { unconsumedMessageIds.isNotEmpty() }

    /** Plan completion and a concurrent send must choose one owner before the process is closed. */
    suspend fun finishPlanIfIdle(): Boolean = mutex.withLock {
        if (unconsumedMessageIds.isNotEmpty()) return@withLock false
        accepting = false
        true
    }

    suspend fun submit(input: LiveInput): LiveInputResult = mutex.withLock {
        if (!accepting) return@withLock LiveInputResult.Inactive
        val messageId = UUID.randomUUID().toString()
        val message = ClaudeUserInputEncoder.encode(
            request.copy(
                prompt = input.prompt,
                systemInstructions = input.systemInstructions,
                contextFiles = input.contextFiles,
                imageAttachments = input.imageAttachments,
                fileAttachments = input.fileAttachments,
            ),
            messageId = messageId,
            includeInstructions = true,
        )
        session.writeStdin(message)
        unconsumedMessageIds.add(messageId)
        LiveInputResult.Accepted("claude:${request.requestId}")
    }

    /** A replayed user UUID marks consumption. Several inputs may share one native result. */
    suspend fun observeStdout(line: String): Boolean = mutex.withLock {
        val message = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
            ?: return@withLock true
        when (message["type"]?.jsonPrimitive?.content) {
            "user" -> unconsumedMessageIds.remove(message["uuid"]?.jsonPrimitive?.content)
            "result" -> {
                val failed = message["is_error"]?.jsonPrimitive?.content == "true"
                if (!failed && unconsumedMessageIds.isNotEmpty()) return@withLock false
                accepting = false
                session.closeInput()
            }
        }
        true
    }
}
