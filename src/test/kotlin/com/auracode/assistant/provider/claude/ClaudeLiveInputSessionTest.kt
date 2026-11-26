package com.auracode.assistant.provider.claude

import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.provider.input.LiveInput
import com.auracode.assistant.provider.input.LiveInputResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClaudeLiveInputSessionTest {
    private val request = AgentRequest(
        engineId = "claude", prompt = "first", contextFiles = emptyList(), workingDirectory = ".",
    )

    @Test
    fun `merged follow-ups keep stdin open until the result consuming both messages`() = runBlocking {
        val wire = RecordingSession()
        val live = ClaudeLiveInputSession(wire, request)
        assertIs<LiveInputResult.Accepted>(live.submit(LiveInput("second")))
        assertIs<LiveInputResult.Accepted>(live.submit(LiveInput("third")))
        assertFalse(live.observeStdout("""{"type":"result","is_error":false}"""))
        assertFalse(wire.closed)

        // The CLI replays both UUIDs even when it merges their content into one native turn.
        wire.lines.forEach { line ->
            val id = Json.parseToJsonElement(line).jsonObject.getValue("uuid").jsonPrimitive.content
            live.observeStdout("""{"type":"user","uuid":"$id"}""")
        }
        assertTrue(live.observeStdout("""{"type":"result","is_error":false}"""))
        assertTrue(wire.closed)
        assertEquals(LiveInputResult.Inactive, live.submit(LiveInput("too late")))
        assertEquals(2, wire.lines.size)
    }

    @Test
    fun `write errors propagate and do not leave phantom unconsumed input`() = runBlocking {
        val wire = RecordingSession().apply { failWrites = true }
        val live = ClaudeLiveInputSession(wire, request)
        assertFailsWith<IOException> { live.submit(LiveInput("failed")) }
        assertFalse(live.hasUnconsumedInputs())
    }

    @Test
    fun `plan completion cannot close a process with unconsumed follow-ups`() = runBlocking {
        val live = ClaudeLiveInputSession(RecordingSession(), request)
        live.submit(LiveInput("revise the plan"))
        assertFalse(live.finishPlanIfIdle())
    }

    private class RecordingSession : ClaudeStreamJsonSession {
        val lines = mutableListOf<String>()
        var closed = false
        var failWrites = false
        override suspend fun collect(onStdoutLine: suspend (String) -> Unit, onStderrLine: suspend (String) -> Unit) = 0
        override suspend fun writeStdin(line: String) {
            if (failWrites) throw IOException("pipe closed")
            lines += line
        }
        override suspend fun closeInput() { closed = true }
        override fun cancel() = Unit
    }
}
